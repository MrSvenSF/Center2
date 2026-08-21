package net.managerhub.center.common.module;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import net.managerhub.center.api.CenterModule;
import net.managerhub.center.api.ModuleActionListener;
import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.api.ModuleCommand;
import net.managerhub.center.api.ModuleContext;
import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.api.ModuleNetwork;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.api.ModuleRemoteException;
import net.managerhub.center.api.ModuleStorage;
import net.managerhub.center.common.util.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleLoaderTest {

    /** MHCenter2 version the tests run with. */
    private static final String CENTER = "0.2.0";

    /** Minecraft version the tests run with. */
    private static final String MINECRAFT = "1.21.11";

    /** What the test modules did, in order. */
    static final List<String> CALLS = new ArrayList<>();

    /** The context the last working module received. */
    static ModuleContext lastContext;

    @TempDir
    Path temporaryDirectory;

    private Path jars;
    private Path configs;
    private MemoryStates states;
    private RecordingReport report;
    private final List<ModuleLoader> created = new ArrayList<>();

    @BeforeEach
    void prepare() throws IOException {
        CALLS.clear();
        lastContext = null;
        jars = Files.createDirectories(temporaryDirectory.resolve("Modules/Jars"));
        configs = Files.createDirectories(temporaryDirectory.resolve("Modules/Configs"));
        states = new MemoryStates();
        report = new RecordingReport();
    }

    /** Releases every jar again, otherwise the temporary folder cannot be removed. */
    @AfterEach
    void releaseJars() {
        created.forEach(ModuleLoader::disableAll);
        created.clear();
    }

    @Test
    @DisplayName("a compatible module is installed, started and stopped")
    void startsCompatibleModule() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(List.of("Working:load", "Working:enable"), CALLS);
        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"));
        assertEquals(1, loader.enabledCount());
        assertTrue(report.entries.isEmpty(), report.entries.toString());

        loader.disableAll();
        assertEquals(List.of("Working:load", "Working:enable", "Working:disable"), CALLS);
    }

    @Test
    @DisplayName("the module gets its own folder below Modules/Configs")
    void handsOverTheOwnConfigFolder() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        loader().refresh(jars, configs);

        assertEquals("Working", lastContext.moduleId());
        assertEquals(configs.resolve("Working"), lastContext.configDirectory());
        assertEquals(ModulePlatform.PAPER, lastContext.platform());
        assertFalse(Files.exists(lastContext.configDirectory()), "the core must not create files for a module");
    }

    @Test
    @DisplayName("a module of another platform is not installed here")
    void skipsForeignPlatform() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.VELOCITY, CENTER, null);
        loader.refresh(jars, configs);

        assertTrue(loader.modules().isEmpty());
        assertTrue(CALLS.isEmpty());
        assertEquals(List.of("skipped:Working.jar"), report.entries);
    }

    @Test
    @DisplayName("a MHCenter2 version below the minimum keeps the module off")
    void refusesCenterVersionBelowMinimum() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.PAPER, "0.1.9", MINECRAFT);
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.INCOMPATIBLE_CENTER, status(loader, "Working"));
        assertTrue(CALLS.isEmpty());
        assertEquals(List.of("incompatible-center:Working"), report.entries);
    }

    @Test
    @DisplayName("a MHCenter2 version above the maximum keeps the module off")
    void refusesCenterVersionAboveMaximum() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.PAPER, "0.3.0", MINECRAFT);
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.INCOMPATIBLE_CENTER, status(loader, "Working"));
        assertTrue(CALLS.isEmpty());
    }

    @Test
    @DisplayName("a MHCenter2 version inside the range starts the module")
    void acceptsCenterVersionInsideTheRange() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.PAPER, "0.2.50", MINECRAFT);
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"));
    }

    @Test
    @DisplayName("a Minecraft version below the minimum keeps the module off")
    void refusesMinecraftVersionBelowMinimum() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.PAPER, CENTER, "1.20.6");
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.INCOMPATIBLE_MINECRAFT, status(loader, "Working"));
        assertTrue(CALLS.isEmpty());
        assertEquals(List.of("incompatible-minecraft:Working"), report.entries);
    }

    @Test
    @DisplayName("a Minecraft version above the maximum keeps the module off")
    void refusesMinecraftVersionAboveMaximum() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.PAPER, CENTER, "1.22.0");
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.INCOMPATIBLE_MINECRAFT, status(loader, "Working"));
        assertTrue(CALLS.isEmpty());
    }

    @Test
    @DisplayName("a Minecraft version inside the range starts the module")
    void acceptsMinecraftVersionInsideTheRange() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.PAPER, CENTER, "1.21.4");
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"));
    }

    @Test
    @DisplayName("a proxy without a Minecraft version does not check one")
    void skipsMinecraftCheckWithoutAMinecraftVersion() throws IOException {
        writeModule("Proxy.jar", """
                id=Proxy
                name=Proxy
                version=1.0
                author=Test
                main=%s
                platform=VELOCITY
                center-min-version=0.2.0
                center-max-version=0.2.99
                """.formatted(WorkingModule.class.getName()), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.VELOCITY, CENTER, null);
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ENABLED, status(loader, "Proxy"));
    }

    @Test
    @DisplayName("a jar without metadata is no module")
    void skipsJarWithoutDescriptor() throws IOException {
        try (JarOutputStream out = jar("Foreign.jar")) {
            addClass(out, WorkingModule.class);
        }

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertTrue(loader.modules().isEmpty());
        assertEquals(List.of("skipped:Foreign.jar"), report.entries);
    }

    @Test
    @DisplayName("a missing main class ends in ERROR")
    void reportsMissingMainClass() throws IOException {
        writeDescriptorOnly("Broken.jar", """
                id=Broken
                name=Broken
                version=1.0
                author=Test
                main=net.managerhub.center.does.NotExist
                platform=PAPER
                center-min-version=0.2.0
                center-max-version=0.2.99
                minecraft-min-version=1.21.0
                minecraft-max-version=1.21.11
                """);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ERROR, status(loader, "Broken"));
        assertEquals(List.of("error:Broken:LOAD"), report.entries);
    }

    @Test
    @DisplayName("a main class that is no module ends in ERROR")
    void reportsClassThatIsNoModule() throws IOException {
        writeModule("NoModule.jar", descriptor("NoModule", NotAModule.class), NotAModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ERROR, status(loader, "NoModule"));
        assertEquals(List.of("error:NoModule:LOAD"), report.entries);
    }

    @Test
    @DisplayName("a module that fails while starting ends in ERROR")
    void reportsFailureWhileStarting() throws IOException {
        writeModule("Broken.jar", descriptor("Broken", BrokenModule.class), BrokenModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ERROR, status(loader, "Broken"));
        assertEquals(List.of("error:Broken:ENABLE"), report.entries);
    }

    @Test
    @DisplayName("a module that fails while starting does not stop the other modules")
    void keepsLoadingAfterABrokenModule() throws IOException {
        writeModule("Broken.jar", descriptor("Broken", BrokenModule.class), BrokenModule.class);
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ERROR, status(loader, "Broken"));
        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"));
        assertEquals(1, loader.enabledCount());
        assertTrue(CALLS.contains("Working:enable"));
    }

    @Test
    @DisplayName("an incompatible module does not stop the other modules")
    void keepsLoadingBesideAnIncompatibleModule() throws IOException {
        writeModule("Old.jar", """
                id=Old
                name=Old
                version=1.0
                author=Test
                main=%s
                platform=PAPER
                center-min-version=0.1.0
                center-max-version=0.1.9
                minecraft-min-version=1.21.0
                minecraft-max-version=1.21.11
                """.formatted(BrokenModule.class.getName()), BrokenModule.class);
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.INCOMPATIBLE_CENTER, status(loader, "Old"));
        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"));
    }

    @Test
    @DisplayName("a module that fails while stopping does not stop the other modules")
    void keepsDisablingAfterABrokenModule() throws IOException {
        writeModule("Rude.jar", descriptor("Rude", RudeModule.class), RudeModule.class);
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        loader.disableAll();

        assertTrue(CALLS.contains("Working:disable"));
        assertEquals(List.of("error:Rude:DISABLE"), report.entries);
        assertTrue(loader.modules().isEmpty());
    }

    @Test
    @DisplayName("two modules with the same id are both refused")
    void refusesDuplicateModuleId() throws IOException {
        writeModule("First.jar", descriptor("Twin", WorkingModule.class), WorkingModule.class);
        writeModule("Second.jar", descriptor("Twin", RudeModule.class), RudeModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertTrue(loader.modules().isEmpty());
        assertTrue(CALLS.isEmpty(), "no module with a duplicate id may be started");
        assertEquals(List.of("skipped:First.jar", "skipped:Second.jar"), report.entries);
    }

    @Test
    @DisplayName("ids that only differ in case count as the same id")
    void refusesDuplicateModuleIdIgnoringCase() throws IOException {
        writeModule("First.jar", descriptor("Twin", WorkingModule.class), WorkingModule.class);
        writeModule("Second.jar", descriptor("TWIN", RudeModule.class), RudeModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertTrue(loader.modules().isEmpty());
    }

    @Test
    @DisplayName("two new jars with one id say that none of them was loaded")
    void duplicateIdWithoutARunningModuleSaysSo() throws IOException {
        writeModule("First.jar", descriptor("Twin", WorkingModule.class), WorkingModule.class);
        writeModule("Second.jar", descriptor("Twin", RudeModule.class), RudeModule.class);

        loader().refresh(jars, configs);

        assertTrue(report.reasons.stream().allMatch(reason -> reason.contains("none of these jars was installed")),
                report.reasons.toString());
    }

    @Test
    @DisplayName("a second jar with the id of a running module does not push it aside")
    void duplicateIdNextToARunningModule() throws IOException {
        writeModule("First.jar", descriptor("Twin", WorkingModule.class), WorkingModule.class);
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        assertEquals(ModuleStatus.ENABLED, status(loader, "Twin"));
        report.entries.clear();
        report.reasons.clear();
        CALLS.clear();

        // A second jar with the same id appears while the first one is running.
        writeModule("Second.jar", descriptor("Twin", RudeModule.class), RudeModule.class);
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ENABLED, status(loader, "Twin"), "the running module keeps running");
        assertEquals(List.of("First.jar"), loader.modules().stream()
                .map(ModuleLoader.InstalledModule::source).toList());
        assertTrue(CALLS.isEmpty(), "nothing of the new jar was loaded");
        assertEquals(List.of("skipped:Second.jar"), report.entries);
        // The exact wording matters: saying that no module of this id was loaded
        // would be plainly wrong while one of them is running.
        final String reason = report.reasons.getFirst();
        assertTrue(reason.contains("stays active"), reason);
        assertTrue(reason.contains("Second.jar"), reason);
        assertFalse(reason.contains("No module with this id was loaded"), reason);
    }

    @Test
    @DisplayName("a duplicate id does not stop the other modules")
    void keepsLoadingBesideADuplicateId() throws IOException {
        writeModule("First.jar", descriptor("Twin", RudeModule.class), RudeModule.class);
        writeModule("Second.jar", descriptor("Twin", RudeModule.class), RudeModule.class);
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(List.of("Working"), loader.modules().stream()
                .map(module -> module.descriptor().id()).toList());
        assertEquals(List.of("Working:load", "Working:enable"), CALLS);
    }

    @Test
    @DisplayName("a reload tells every running module and loads nothing again")
    void reloadTellsEveryRunningModule() throws IOException {
        writeModule("Reloading.jar", descriptor("Reloading", ReloadingModule.class), ReloadingModule.class);
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        CALLS.clear();

        final int reloaded = loader.reloadModules();

        assertEquals(1, reloaded);
        // Only onReload: no jar is read again and no module is created a second time.
        assertEquals(List.of("Reloading:reload"), CALLS);
        assertEquals(ModuleStatus.ENABLED, status(loader, "Reloading"));
    }

    @Test
    @DisplayName("a module an administrator switched off is not reloaded")
    void reloadSkipsASwitchedOffModule() throws IOException {
        writeModule("Reloading.jar", descriptor("Reloading", ReloadingModule.class), ReloadingModule.class);
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        loader.disable("Reloading");
        CALLS.clear();

        assertEquals(0, loader.reloadModules());
        assertTrue(CALLS.isEmpty());
    }

    @Test
    @DisplayName("a module that fails the reload is stopped instead of running in an unknown state")
    void reloadFailureStopsTheModule() throws IOException {
        writeModule("ReloadFailing.jar", descriptor("ReloadFailing", ReloadFailingModule.class),
                ReloadFailingModule.class);
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        CALLS.clear();
        report.entries.clear();

        loader.reloadModules();

        assertEquals(ModuleStatus.ERROR, status(loader, "ReloadFailing"));
        assertEquals(List.of("ReloadFailing:reload", "ReloadFailing:disable"), CALLS);
        assertEquals(List.of("error:ReloadFailing:RELOAD"), report.entries);
    }

    @Test
    @DisplayName("one module that fails the reload does not stop the others")
    void reloadFailureIsIsolated() throws IOException {
        writeModule("ReloadFailing.jar", descriptor("ReloadFailing", ReloadFailingModule.class),
                ReloadFailingModule.class);
        writeModule("Reloading.jar", descriptor("Reloading", ReloadingModule.class), ReloadingModule.class);
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        CALLS.clear();

        assertEquals(1, loader.reloadModules());

        assertEquals(ModuleStatus.ENABLED, status(loader, "Reloading"));
        assertEquals(ModuleStatus.ERROR, status(loader, "ReloadFailing"));
    }

    @Test
    @DisplayName("a module that does not implement onReload simply survives a reload")
    void reloadIsOptionalForAModule() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        CALLS.clear();

        assertEquals(1, loader.reloadModules());

        assertTrue(CALLS.isEmpty(), "the default of the API does nothing at all");
        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"));
    }

    @Test
    @DisplayName("a module can be started again after it failed a reload")
    void aModuleCanComeBackAfterAFailedReload() throws IOException {
        writeModule("Reloading.jar", descriptor("Reloading", ReloadingModule.class), ReloadingModule.class);
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        loader.disable("Reloading");
        CALLS.clear();

        loader.enable("Reloading");
        loader.reloadModules();

        assertEquals(List.of("Reloading:load", "Reloading:enable", "Reloading:reload"), CALLS);
    }

    @Test
    @DisplayName("a missing jar folder is not an error")
    void acceptsMissingFolder() {
        final ModuleLoader loader = loader();

        loader.refresh(temporaryDirectory.resolve("nowhere"), configs);

        assertTrue(loader.modules().isEmpty());
        assertTrue(report.entries.isEmpty());
    }

    @Test
    @DisplayName("a module can be stopped and started again while the server runs")
    void disablesAndEnablesAgain() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(Optional.of(ModuleStatus.DISABLED), loader.disable("Working"));
        assertEquals(List.of("Working:load", "Working:enable", "Working:disable"), CALLS);
        assertEquals(Set.of("working"), states.disabled);

        assertEquals(Optional.of(ModuleStatus.ENABLED), loader.enable("Working"));
        assertEquals(List.of("Working:load", "Working:enable", "Working:disable", "Working:load", "Working:enable"),
                CALLS);
        assertTrue(states.disabled.isEmpty());
    }

    @Test
    @DisplayName("the id is read without case and an unknown module is answered as unknown")
    void answersUnknownModule() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(Optional.of(ModuleStatus.DISABLED), loader.disable("WORKING"));
        assertTrue(loader.enable("Nothing").isEmpty());
        assertTrue(loader.disable("Nothing").isEmpty());
    }

    @Test
    @DisplayName("a module an administrator switched off stays off after a restart")
    void remembersTheSwitchedOffModule() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);
        loader().refresh(jars, configs);
        CALLS.clear();

        // A new loader with the same store is what a server restart looks like.
        final ModuleLoader afterRestart = loader();
        states.disabled.add("working");
        afterRestart.refresh(jars, configs);

        assertEquals(ModuleStatus.DISABLED, status(afterRestart, "Working"));
        assertTrue(CALLS.isEmpty());
        assertEquals(List.of("admin-disabled:Working"), report.entries);
    }

    @Test
    @DisplayName("an incompatible module cannot be started by hand")
    void refusesToStartAnIncompatibleModule() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.PAPER, CENTER, "1.22.0");
        loader.refresh(jars, configs);

        assertEquals(Optional.of(ModuleStatus.INCOMPATIBLE_MINECRAFT), loader.enable("Working"));
        assertTrue(CALLS.isEmpty());
    }

    @Test
    @DisplayName("a module in ERROR is tried again and stays in ERROR")
    void triesTheBrokenModuleAgain() throws IOException {
        writeModule("Broken.jar", descriptor("Broken", BrokenModule.class), BrokenModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(Optional.of(ModuleStatus.ERROR), loader.enable("Broken"));
        assertEquals(List.of("error:Broken:ENABLE", "error:Broken:ENABLE"), report.entries);
        assertEquals(0, loader.enabledCount());
    }

    @Test
    @DisplayName("a jar that appears while the server runs is found by the next refresh")
    void findsANewJarAtRuntime() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        assertEquals(1, loader.modules().size());

        writeModule("Second.jar", descriptor("Second", SecondModule.class), SecondModule.class);
        loader.refresh(jars, configs);

        assertEquals(2, loader.modules().size());
        assertEquals(ModuleStatus.ENABLED, status(loader, "Second"));
        assertTrue(CALLS.contains("Second:enable"));
        // The module that was already running is never loaded a second time.
        assertEquals(1, CALLS.stream().filter("Working:load"::equals).count());
    }

    @Test
    @DisplayName("a replaced jar of a module that was never loaded is read again")
    void readsAReplacedJarOfANeverLoadedModule() throws IOException {
        writeModule("Late.jar", """
                id=Late
                name=Late
                version=1.0
                author=Test
                main=%s
                platform=PAPER
                center-min-version=0.1.0
                center-max-version=0.1.9
                minecraft-min-version=1.21.0
                minecraft-max-version=1.21.11
                """.formatted(WorkingModule.class.getName()), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        assertEquals(ModuleStatus.INCOMPATIBLE_CENTER, status(loader, "Late"));

        // No class of this jar is in memory, so a corrected jar may simply be read
        // again. This is not a hot swap of a loaded module.
        writeModule("Late.jar", descriptor("Late", WorkingModule.class), WorkingModule.class);
        Files.setLastModifiedTime(jars.resolve("Late.jar"),
                FileTime.fromMillis(System.currentTimeMillis() + 10_000));
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ENABLED, status(loader, "Late"));
    }

    @Test
    @DisplayName("a Paper loader without a Minecraft version is refused")
    void refusesPaperLoaderWithoutMinecraftVersion() {
        // Fail closed: on Paper an unknown version must never mean "no check".
        assertThrows(IllegalStateException.class, () -> new ModuleLoader(ModulePlatform.PAPER,
                Version.of(CENTER).orElseThrow(),
                Optional.empty(),
                (descriptor, configDirectory, cleanup) ->
                        new TestContext(descriptor.id(), configDirectory, ModulePlatform.PAPER, cleanup),
                states,
                report));
    }

    @Test
    @DisplayName("a proxy loader without a Minecraft version is allowed")
    void acceptsProxyLoaderWithoutMinecraftVersion() {
        assertDoesNotThrow(() -> loader(ModulePlatform.VELOCITY, CENTER, null));
    }

    @Test
    @DisplayName("a failure in onEnable runs the cleanup the module registered")
    void cleansUpAfterAFailedEnable() throws IOException {
        writeModule("Half.jar", descriptor("Half", HalfStartedModule.class), HalfStartedModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ERROR, status(loader, "Half"));
        assertEquals(List.of("Half:load", "Half:enable", "Half:cleanup-enable", "Half:cleanup-load"), CALLS);
        assertEquals(List.of("error:Half:ENABLE"), report.entries);
    }

    @Test
    @DisplayName("a failure in onLoad runs the cleanup that was already registered")
    void cleansUpAfterAFailedLoad() throws IOException {
        writeModule("Half.jar", descriptor("Half", HalfLoadedModule.class), HalfLoadedModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ERROR, status(loader, "Half"));
        assertEquals(List.of("Half:load", "Half:cleanup-load"), CALLS);
        assertEquals(List.of("error:Half:LOAD"), report.entries);
    }

    @Test
    @DisplayName("a broken module does not stop the cleanup of the other modules")
    void keepsCleaningUpAfterABrokenModule() throws IOException {
        writeModule("Half.jar", descriptor("Half", HalfStartedModule.class), HalfStartedModule.class);
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"));
        assertTrue(CALLS.contains("Half:cleanup-load"));
        assertTrue(CALLS.contains("Working:enable"));
    }

    @Test
    @DisplayName("the cleanup runs even when onDisable fails")
    void cleansUpAfterAFailedDisable() throws IOException {
        writeModule("Rude.jar", descriptor("RudeCleanup", RudeCleanupModule.class), RudeCleanupModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        loader.disableAll();

        assertTrue(CALLS.contains("RudeCleanup:cleanup"), CALLS.toString());
        assertEquals(List.of("error:RudeCleanup:DISABLE"), report.entries);
    }

    @Test
    @DisplayName("a failing cleanup does not replace the original error")
    void reportsTheCleanupFailureNextToTheOriginalOne() throws IOException {
        writeModule("Nasty.jar", descriptor("Nasty", NastyCleanupModule.class), NastyCleanupModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ERROR, status(loader, "Nasty"));
        assertEquals(List.of("error:Nasty:ENABLE", "error:Nasty:CLEANUP"), report.entries);
    }

    @Test
    @DisplayName("an unreadable module state does not start any module")
    void startsNoModuleWhenTheStateIsUnreadable() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);
        states.readable = false;

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);

        // "nothing is switched off" and "the state could not be read" must not
        // look the same, so nothing is started here.
        assertEquals(ModuleStatus.DISABLED, status(loader, "Working"));
        assertTrue(CALLS.isEmpty());
        assertEquals(List.of("state-unreadable"), report.entries);
    }

    @Test
    @DisplayName("a state that cannot be stored is reported but still applied at runtime")
    void reportsAFailedPersist() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        states.writable = false;

        assertEquals(Optional.of(ModuleStatus.DISABLED), loader.disable("Working"));
        assertTrue(CALLS.contains("Working:disable"));
        assertEquals(List.of("state-not-stored:Working:off"), report.entries);
    }

    @Test
    @DisplayName("a changed jar is found even with the same size and change time")
    void findsAChangedJarWithTheSameSizeAndTime() throws IOException {
        // Both descriptors have exactly the same length, and the jar is stored
        // without compression, so only the content itself differs.
        writeModule("Late.jar", rangedDescriptor("Late", "0.1.0", "0.1.99"), WorkingModule.class);
        final FileTime written = Files.getLastModifiedTime(jars.resolve("Late.jar"));
        final long size = Files.size(jars.resolve("Late.jar"));

        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        assertEquals(ModuleStatus.INCOMPATIBLE_CENTER, status(loader, "Late"));

        writeModule("Late.jar", rangedDescriptor("Late", "0.2.0", "0.2.99"), WorkingModule.class);
        Files.setLastModifiedTime(jars.resolve("Late.jar"), written);
        assertEquals(size, Files.size(jars.resolve("Late.jar")), "the test needs two jars of the same size");

        loader.refresh(jars, configs);

        assertEquals(ModuleStatus.ENABLED, status(loader, "Late"));
    }

    @Test
    @DisplayName("a module for both platforms runs on Paper and on the proxy")
    void startsABothModuleEverywhere() throws IOException {
        writeModule("Both.jar", """
                id=Both
                name=Both
                version=1.0
                author=Test
                main=%s
                platform=BOTH
                center-min-version=0.2.0
                center-max-version=0.2.99
                minecraft-min-version=1.21.0
                minecraft-max-version=1.21.11
                """.formatted(WorkingModule.class.getName()), WorkingModule.class);

        final ModuleLoader onPaper = loader();
        onPaper.refresh(jars, configs);
        assertEquals(ModuleStatus.ENABLED, status(onPaper, "Both"));
        assertEquals(ModulePlatform.PAPER, lastContext.platform());

        final ModuleLoader onProxy = loader(ModulePlatform.VELOCITY, CENTER, null);
        onProxy.refresh(jars, configs);
        assertEquals(ModuleStatus.ENABLED, status(onProxy, "Both"));
        // A BOTH module has to be told where it really is.
        assertEquals(ModulePlatform.VELOCITY, lastContext.platform());
    }

    @Test
    @DisplayName("a proxy module goes through the whole lifecycle on the proxy")
    void runsTheFullLifecycleOnTheProxy() throws IOException {
        writeModule("Proxy.jar", """
                id=Proxy
                name=Proxy
                version=1.0
                author=Test
                main=%s
                platform=VELOCITY
                center-min-version=0.2.0
                center-max-version=0.2.99
                """.formatted(WorkingModule.class.getName()), WorkingModule.class);

        final ModuleLoader loader = loader(ModulePlatform.VELOCITY, CENTER, null);
        loader.refresh(jars, configs);
        assertEquals(ModuleStatus.ENABLED, status(loader, "Proxy"));

        assertEquals(Optional.of(ModuleStatus.DISABLED), loader.disable("Proxy"));
        assertEquals(Optional.of(ModuleStatus.ENABLED), loader.enable("Proxy"));
        loader.disableAll();

        // onLoad writes the module id, the other two steps write their own name.
        assertEquals(List.of("Proxy:load", "Working:enable", "Working:disable",
                "Proxy:load", "Working:enable", "Working:disable"), CALLS);
    }

    @Test
    @DisplayName("a failed scan changes nothing at all")
    void keepsEverythingWhenTheScanFails() throws IOException {
        writeModule("Working.jar", descriptor("Working", WorkingModule.class), WorkingModule.class);
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"));
        report.entries.clear();

        // A folder that cannot be read must never look like an empty folder.
        final Path blocked = temporaryDirectory.resolve("Modules/Blocked");
        Files.writeString(blocked, "this is a file, not a folder");
        loader.refresh(blocked, configs);

        assertEquals(List.of("scan-failed:Modules/Jars"), report.entries);
        assertEquals(ModuleStatus.ENABLED, status(loader, "Working"), "a known module must survive a failed scan");
        assertEquals(1, loader.modules().size());
    }

    @Test
    @DisplayName("a module that once loaded classes is never replaced by a new jar")
    void refusesTheHotSwapThroughRemoveAndReadd() throws IOException {
        // The main class comes from the test classpath, so the jar itself is never
        // opened and can be deleted on every operating system.
        writeDescriptorOnly("Swap.jar", swapDescriptor("1.0"));
        final ModuleLoader loader = loader();
        loader.refresh(jars, configs);
        assertEquals(ModuleStatus.ENABLED, status(loader, "Swap"));

        assertEquals(Optional.of(ModuleStatus.DISABLED), loader.disable("Swap"));
        Files.delete(jars.resolve("Swap.jar"));
        loader.refresh(jars, configs);

        // The identity stays known for the whole server run.
        assertEquals(1, loader.modules().size());
        assertEquals(List.of("jar-changed:Swap"), report.entries);

        writeDescriptorOnly("Swap.jar", swapDescriptor("2.0"));
        loader.refresh(jars, configs);

        assertEquals("1.0", loader.module("Swap").orElseThrow().descriptor().version(),
                "the new binary must not replace the one that was already loaded");
    }

    private ModuleLoader loader() {
        return loader(ModulePlatform.PAPER, CENTER, MINECRAFT);
    }

    /** Metadata whose main class is found through the parent class loader. */
    private static String swapDescriptor(final String version) {
        return """
                id=Swap
                name=Swap
                version=%s
                author=Test
                main=%s
                platform=PAPER
                center-min-version=0.2.0
                center-max-version=0.2.99
                minecraft-min-version=1.21.0
                minecraft-max-version=1.21.11
                """.formatted(version, WorkingModule.class.getName());
    }

    private ModuleLoader loader(final ModulePlatform platform,
                                final String centerVersion,
                                final String minecraftVersion) {
        final ModuleLoader loader = new ModuleLoader(platform,
                Version.of(centerVersion).orElseThrow(),
                Version.of(minecraftVersion),
                (descriptor, configDirectory, cleanup) ->
                        new TestContext(descriptor.id(), configDirectory, platform, cleanup),
                states,
                report);
        created.add(loader);
        return loader;
    }

    private static ModuleStatus status(final ModuleLoader loader, final String moduleId) {
        return loader.module(moduleId).orElseThrow().status();
    }

    /**
     * The normal metadata with a freely chosen MHCenter2 range.
     *
     * <p>Two calls with ranges of the same text length produce metadata of exactly
     * the same length, which is what the fingerprint test needs.</p>
     */
    private static String rangedDescriptor(final String id, final String minimum, final String maximum) {
        return """
                id=%s
                name=%s
                version=1.0
                author=Test
                main=%s
                platform=PAPER
                center-min-version=%s
                center-max-version=%s
                minecraft-min-version=1.21.0
                minecraft-max-version=1.21.11
                """.formatted(id, id, WorkingModule.class.getName(), minimum, maximum);
    }

    private static String descriptor(final String id, final Class<?> mainClass) {
        return """
                id=%s
                name=%s
                version=1.0
                author=Test
                main=%s
                platform=PAPER
                center-min-version=0.2.0
                center-max-version=0.2.99
                minecraft-min-version=1.21.0
                minecraft-max-version=1.21.11
                """.formatted(id, id, mainClass.getName());
    }

    private void writeModule(final String fileName,
                             final String descriptor,
                             final Class<?> mainClass) throws IOException {
        try (JarOutputStream out = jar(fileName)) {
            addDescriptor(out, descriptor);
            addClass(out, mainClass);
        }
    }

    private void writeDescriptorOnly(final String fileName, final String descriptor) throws IOException {
        try (JarOutputStream out = jar(fileName)) {
            addDescriptor(out, descriptor);
        }
    }

    private JarOutputStream jar(final String fileName) throws IOException {
        final JarOutputStream out = new JarOutputStream(Files.newOutputStream(jars.resolve(fileName)));
        // Without compression the file size only depends on the content length, so
        // a test can build two different jars that have exactly the same size.
        out.setLevel(0);
        return out;
    }

    private static void addDescriptor(final JarOutputStream out, final String descriptor) throws IOException {
        out.putNextEntry(new JarEntry("center-module.properties"));
        out.write(descriptor.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void addClass(final JarOutputStream out, final Class<?> type) throws IOException {
        final String path = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("The compiled class of " + type.getName() + " was not found.");
            }
            out.putNextEntry(new JarEntry(path));
            in.transferTo((OutputStream) out);
            out.closeEntry();
        }
    }

    /** A module that works and writes down what happened. */
    public static final class WorkingModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            lastContext = context;
            CALLS.add(context.moduleId() + ":load");
        }

        @Override
        public void onEnable() {
            CALLS.add("Working:enable");
        }

        @Override
        public void onDisable() {
            CALLS.add("Working:disable");
        }
    }

    /** A module that reads its configuration again and says so. */
    public static final class ReloadingModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            CALLS.add("Reloading:load");
        }

        @Override
        public void onEnable() {
            CALLS.add("Reloading:enable");
        }

        @Override
        public void onReload() {
            CALLS.add("Reloading:reload");
        }

        @Override
        public void onDisable() {
            CALLS.add("Reloading:disable");
        }
    }

    /** A module that cannot apply its configuration again. */
    public static final class ReloadFailingModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            CALLS.add("ReloadFailing:load");
        }

        @Override
        public void onEnable() {
            CALLS.add("ReloadFailing:enable");
        }

        @Override
        public void onReload() {
            CALLS.add("ReloadFailing:reload");
            throw new IllegalStateException("the configuration of the module is broken");
        }

        @Override
        public void onDisable() {
            CALLS.add("ReloadFailing:disable");
        }
    }

    /** A second working module, used for the runtime detection of a new jar. */
    public static final class SecondModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            CALLS.add("Second:load");
        }

        @Override
        public void onEnable() {
            CALLS.add("Second:enable");
        }

        @Override
        public void onDisable() {
            CALLS.add("Second:disable");
        }
    }

    /** A module that fails while starting. */
    public static final class BrokenModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            CALLS.add("Broken:load");
        }

        @Override
        public void onEnable() {
            throw new IllegalStateException("this module is broken");
        }

        @Override
        public void onDisable() {
            CALLS.add("Broken:disable");
        }
    }

    /** A module that fails while stopping. */
    public static final class RudeModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            CALLS.add("Rude:load");
        }

        @Override
        public void onEnable() {
            CALLS.add("Rude:enable");
        }

        @Override
        public void onDisable() {
            throw new IllegalStateException("this module does not want to stop");
        }
    }

    /** A module that registers cleanup in both steps and then fails while starting. */
    public static final class HalfStartedModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            CALLS.add("Half:load");
            context.registerCleanup(() -> CALLS.add("Half:cleanup-load"));
            context.registerCleanup(() -> CALLS.add("Half:cleanup-enable"));
        }

        @Override
        public void onEnable() {
            CALLS.add("Half:enable");
            throw new IllegalStateException("this module fails after it registered something");
        }

        @Override
        public void onDisable() {
            CALLS.add("Half:disable");
        }
    }

    /** A module that registers cleanup and then fails while loading. */
    public static final class HalfLoadedModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            CALLS.add("Half:load");
            context.registerCleanup(() -> CALLS.add("Half:cleanup-load"));
            throw new IllegalStateException("this module fails while loading");
        }

        @Override
        public void onEnable() {
            CALLS.add("Half:enable");
        }

        @Override
        public void onDisable() {
            CALLS.add("Half:disable");
        }
    }

    /** A module whose cleanup itself fails after a failed start. */
    public static final class NastyCleanupModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            context.registerCleanup(() -> {
                throw new IllegalStateException("even the cleanup of this module is broken");
            });
        }

        @Override
        public void onEnable() {
            throw new IllegalStateException("this module is broken");
        }

        @Override
        public void onDisable() {
            // Never reached.
        }
    }

    /** A module that registers cleanup and fails while stopping. */
    public static final class RudeCleanupModule implements CenterModule {

        @Override
        public void onLoad(final ModuleContext context) {
            context.registerCleanup(() -> CALLS.add("RudeCleanup:cleanup"));
        }

        @Override
        public void onEnable() {
            CALLS.add("RudeCleanup:enable");
        }

        @Override
        public void onDisable() {
            throw new IllegalStateException("this module does not want to stop");
        }
    }

    /** A class that is named as a main class but is no module at all. */
    public static final class NotAModule {
    }

    /** Remembers the switched off modules like the local database does. */
    private static final class MemoryStates implements ModuleStateStore {

        private final Set<String> disabled = new LinkedHashSet<>();
        private boolean readable = true;
        private boolean writable = true;

        @Override
        public Set<String> disabledModules() throws ModuleStateException {
            if (!readable) {
                throw new ModuleStateException("the test store cannot be read", null);
            }
            return Set.copyOf(disabled);
        }

        @Override
        public void setModuleDisabled(final String moduleId, final boolean switchedOff)
                throws ModuleStateException {
            if (!writable) {
                throw new ModuleStateException("the test store cannot be written", null);
            }
            if (switchedOff) {
                disabled.add(moduleId);
            } else {
                disabled.remove(moduleId);
            }
        }
    }

    /** Writes down what the module system reported, without any log. */
    private static final class RecordingReport implements ModuleReport {

        private final List<String> entries = new ArrayList<>();

        /** The wording itself, because a wrong reason is a bug of its own. */
        private final List<String> reasons = new ArrayList<>();

        @Override
        public void skipped(final String source, final String reason) {
            entries.add("skipped:" + source);
            reasons.add(reason);
        }

        @Override
        public void incompatibleCenter(final ModuleDescriptor module, final String running) {
            entries.add("incompatible-center:" + module.id());
        }

        @Override
        public void incompatibleMinecraft(final ModuleDescriptor module, final String running) {
            entries.add("incompatible-minecraft:" + module.id());
        }

        @Override
        public void administrativelyDisabled(final ModuleDescriptor module) {
            entries.add("admin-disabled:" + module.id());
        }

        @Override
        public void jarChanged(final ModuleDescriptor module, final String source) {
            entries.add("jar-changed:" + module.id());
        }

        @Override
        public void error(final ModuleDescriptor module,
                          final ModuleLifecycle step,
                          final String reason,
                          final Throwable failure) {
            entries.add("error:" + module.id() + ":" + step);
        }

        @Override
        public void scanFailed(final String directory, final Throwable failure) {
            entries.add("scan-failed:" + directory);
        }

        @Override
        public void stateUnreadable(final Throwable failure) {
            entries.add("state-unreadable");
        }

        @Override
        public void statePersistFailed(final ModuleDescriptor module,
                                       final boolean disabled,
                                       final Throwable failure) {
            entries.add("state-not-stored:" + module.id() + ":" + (disabled ? "off" : "on"));
        }
    }

    private record TestContext(String moduleId,
                               Path configDirectory,
                               ModulePlatform platform,
                               ModuleCleanup cleanup) implements ModuleContext {

        @Override
        public void registerCleanup(final Runnable action) {
            cleanup.register(action);
        }

        @Override
        public boolean registerCommand(final String path, final ModuleCommand command) {
            // The loader tests do not need a command registration of a platform.
            CALLS.add(moduleId + ":command:" + path);
            return true;
        }

        @Override
        public ModuleNetwork network() {
            // The loader tests never touch the network; a module that would use it
            // gets the same honest "not available" a switched off remote system
            // gives.
            return new UnavailableNetwork(moduleId);
        }

        @Override
        public <T> Optional<T> service(final Class<T> type) {
            // No platform service in the loader tests: that is exactly what a
            // module sees on Paper.
            return Optional.empty();
        }

        @Override
        public ModuleLogger logger() {
            return new ModuleLogger() {

                @Override
                public void info(final String message) {
                    // The test does not need the log.
                }

                @Override
                public void warn(final String message) {
                    // The test does not need the log.
                }

                @Override
                public void error(final String message, final Throwable failure) {
                    // The test does not need the log.
                }
            };
        }
    }

    /** The network a module sees when the remote system is not there at all. */
    private record UnavailableNetwork(String moduleId) implements ModuleNetwork, ModuleStorage {

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String serverId() {
            return "";
        }

        @Override
        public List<String> onlineNodes() {
            return List.of();
        }

        @Override
        public ModuleStorage storage() {
            return this;
        }

        @Override
        public void onAction(final ModuleActionListener listener) {
            // Nothing can arrive without a remote system.
        }

        @Override
        public void send(final String type,
                         final ModuleActionTarget target,
                         final byte[] payload,
                         final Duration lifetime) throws ModuleRemoteException {
            throw unavailable();
        }

        @Override
        public void put(final String key, final byte[] payload, final Duration ttl) throws ModuleRemoteException {
            throw unavailable();
        }

        @Override
        public Optional<byte[]> get(final String key) throws ModuleRemoteException {
            throw unavailable();
        }

        @Override
        public Optional<byte[]> take(final String key) throws ModuleRemoteException {
            throw unavailable();
        }

        @Override
        public boolean delete(final String key) throws ModuleRemoteException {
            throw unavailable();
        }

        private ModuleRemoteException unavailable() {
            return new ModuleRemoteException("The remote system is switched off, so module '"
                    + moduleId + "' cannot use it.");
        }
    }
}
