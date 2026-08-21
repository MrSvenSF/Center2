package net.managerhub.center.common.module;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import net.managerhub.center.Center;
import net.managerhub.center.api.CenterModule;
import net.managerhub.center.api.ModuleContext;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.util.Version;

/**
 * Loads the module jars of {@code Modules/Jars} and owns the state of every
 * installed module.
 *
 * <p>Every jar gets its own {@link URLClassLoader} whose parent is the class
 * loader of MHCenter2, so a module sees the MHCenter2 module API and the API of the
 * platform, and nothing of the other modules. A class loader is opened once and
 * is kept until the server stops: switching a module off and on again never
 * exchanges the binary of an already loaded jar.</p>
 *
 * <p>Every module is handled on its own. A jar without metadata, with the wrong
 * platform or with a duplicate id is reported and is not an installed module. A
 * module that does not support the running MHCenter2 or Minecraft version stays
 * installed, is not started and carries {@link ModuleStatus#INCOMPATIBLE_CENTER}
 * or {@link ModuleStatus#INCOMPATIBLE_MINECRAFT}; that is a normal state and
 * never an exception. A module that throws while loading, starting or stopping
 * carries {@link ModuleStatus#ERROR}. The core and the other modules keep
 * running in every one of these cases.</p>
 */
public final class ModuleLoader {

    /** Creates the context one module gets. */
    @FunctionalInterface
    public interface ContextFactory {

        /**
         * @param descriptor      metadata of the module
         * @param configDirectory folder the module may use for its own files
         * @param cleanup         where this module registers how its resources are removed again
         * @return the context for this module
         */
        ModuleContext create(ModuleDescriptor descriptor, Path configDirectory, ModuleCleanup cleanup);
    }

    /**
     * One installed module and its current state.
     *
     * @param descriptor metadata of the module
     * @param source     file name of the module jar
     * @param status     what the module is doing right now
     */
    public record InstalledModule(ModuleDescriptor descriptor, String source, ModuleStatus status) {
    }

    /** One jar whose metadata was read, before it becomes an installed module. */
    private record Candidate(Path jar, String source, ModuleDescriptor descriptor) {
    }

    private final ModulePlatform platform;
    private final Version centerVersion;
    private final Optional<Version> minecraftVersion;
    private final ContextFactory contextFactory;
    private final ModuleStateStore states;
    private final ModuleReport report;

    /** Every installed module by its lower case id, in the order they were found. */
    private final Map<String, Managed> modules = new LinkedHashMap<>();

    private Set<String> administrativelyDisabled = Set.of();
    private boolean stateReadable = true;
    private Path configsDirectory;

    /**
     * @param platform         the platform MHCenter2 is running on
     * @param centerVersion    version of the running MHCenter2
     * @param minecraftVersion version of the running Minecraft server; only a proxy
     *                         may leave this empty, because it has no single
     *                         Minecraft version of its own
     * @param contextFactory   creates the context of a module
     * @param states           remembers which modules an administrator switched off
     * @param report           where problems of the module system are written
     * @throws IllegalStateException if a Paper loader is built without a Minecraft version
     */
    public ModuleLoader(final ModulePlatform platform,
                        final Version centerVersion,
                        final Optional<Version> minecraftVersion,
                        final ContextFactory contextFactory,
                        final ModuleStateStore states,
                        final ModuleReport report) {
        // The invariant lives here and not only in the caller: on Paper an empty
        // Minecraft version means "not confirmed", never "no check needed". A
        // loader that could not be sure would silently start modules that were
        // never built for this server version.
        if (platform == ModulePlatform.PAPER && minecraftVersion.isEmpty()) {
            throw new IllegalStateException("A " + ModulePlatform.PAPER + " module loader needs the running "
                    + "Minecraft version. Without it no module may be started.");
        }
        this.platform = platform;
        this.centerVersion = centerVersion;
        this.minecraftVersion = minecraftVersion;
        this.contextFactory = contextFactory;
        this.states = states;
        this.report = report;
    }

    /**
     * Creates {@code Modules/Jars} and {@code Modules/Configs}.
     *
     * <p>MHCenter2 never puts files there, the folders only wait for the modules.</p>
     *
     * @param modulesDirectory the {@code Modules} folder of the platform
     * @throws IOException if a folder cannot be created
     */
    public static void createDirectories(final Path modulesDirectory) throws IOException {
        Files.createDirectories(modulesDirectory.resolve(Center.MODULE_JARS_DIRECTORY));
        Files.createDirectories(modulesDirectory.resolve(Center.MODULE_CONFIGS_DIRECTORY));
    }

    /**
     * Reads the jar folder and starts every module that may run.
     *
     * <p>This is both the first load at startup and the answer to the module
     * reload command: a jar that appeared while the server was running becomes a
     * new installed module, an already loaded jar is never exchanged. A module
     * that an administrator switched off stays off.</p>
     *
     * @param jarsDirectory    folder with the module jars
     * @param configsDirectory folder that holds the data of the modules
     */
    public void refresh(final Path jarsDirectory, final Path configsDirectory) {
        this.configsDirectory = configsDirectory;

        final Optional<List<Path>> found = jars(jarsDirectory);
        if (found.isEmpty()) {
            // The folder could not be read. That is not the same as an empty
            // folder, so nothing at all is changed in this run: no module is
            // forgotten and none is treated as removed.
            return;
        }

        readAdministrativeState();

        final List<Candidate> candidates = new ArrayList<>();
        for (final Path jar : found.get()) {
            final String source = jar.getFileName().toString();
            try {
                candidates.add(new Candidate(jar, source, readDescriptor(jar, source)));
            } catch (final ConfigurationException invalid) {
                report.skipped(source, invalid.getMessage());
            } catch (final Throwable broken) {
                report.skipped(source, describe(broken));
            }
        }

        forgetRemovedModules(candidates);

        for (final Candidate candidate : withUniqueIds(candidates)) {
            final Managed known = modules.get(key(candidate.descriptor().id()));
            if (known == null) {
                install(candidate);
                continue;
            }
            if (!known.source.equals(candidate.source())) {
                // The running module keeps its identity. Nothing of it is touched.
                report.skipped(candidate.source(), duplicateRejected(candidate, known.source));
                continue;
            }
            if (!known.jarWasReplaced()) {
                continue;
            }
            if (known.wasLoaded()) {
                // Classes of this jar are in this JVM. Closing a class loader does
                // not prove that every class, static value, thread or listener of
                // the old binary is really gone, so nothing is exchanged here.
                known.reportJarChange(report);
                continue;
            }
            // Nothing of this jar was ever loaded, so the new file is simply read
            // again. That is not a hot swap: no class of the old file exists.
            modules.remove(key(candidate.descriptor().id()));
            install(candidate);
        }
    }

    /**
     * Starts one installed module again.
     *
     * <p>Platform, MHCenter2 version and Minecraft version are checked again, so an
     * incompatible module can never be forced on. A module in
     * {@link ModuleStatus#ERROR} gets exactly one controlled new attempt; if it
     * fails again it stays in {@code ERROR}.</p>
     *
     * @param moduleId id of the module, upper and lower case are the same id
     * @return the state of the module afterwards, or empty if no such module is installed
     */
    public Optional<ModuleStatus> enable(final String moduleId) {
        final Managed managed = modules.get(key(moduleId));
        if (managed == null) {
            return Optional.empty();
        }
        if (managed.status == ModuleStatus.ENABLED) {
            return Optional.of(ModuleStatus.ENABLED);
        }
        if (!compatible(managed)) {
            return Optional.of(managed.status);
        }

        remember(managed, false);
        activate(managed);
        return Optional.of(managed.status);
    }

    /**
     * Stops one installed module and remembers that decision.
     *
     * <p>The module keeps its class loader, so it can be started again without a
     * restart. A module that fails in {@code onDisable} ends in
     * {@link ModuleStatus#ERROR} and is not running either way.</p>
     *
     * @param moduleId id of the module, upper and lower case are the same id
     * @return the state of the module afterwards, or empty if no such module is installed
     */
    public Optional<ModuleStatus> disable(final String moduleId) {
        final Managed managed = modules.get(key(moduleId));
        if (managed == null) {
            return Optional.empty();
        }
        remember(managed, true);
        if (managed.status != ModuleStatus.ENABLED) {
            if (!managed.status.isIncompatible()) {
                managed.status = ModuleStatus.DISABLED;
            }
            return Optional.of(managed.status);
        }

        managed.status = stop(managed) ? ModuleStatus.DISABLED : ModuleStatus.ERROR;
        return Optional.of(managed.status);
    }

    /**
     * Tells every running module that the configuration was reloaded.
     *
     * <p>This is the module half of {@code /center reload}, and it is deliberately
     * <em>not</em> a new load: no jar is read again, no class loader is exchanged
     * and no module is created a second time. The instances that are running keep
     * running and are only asked to read their own configuration again.</p>
     *
     * <p>A module that fails here is stopped and ends in
     * {@link ModuleStatus#ERROR}. MHCenter2 cannot know how much of its
     * configuration the module already applied, and a module in an unknown state
     * is worse than a module that is switched off; an administrator can start it
     * again with the module command once the cause is fixed.</p>
     *
     * @return how many modules applied the reload
     */
    public int reloadModules() {
        int reloaded = 0;
        for (final Managed managed : new ArrayList<>(modules.values())) {
            if (managed.status != ModuleStatus.ENABLED || managed.instance == null) {
                continue;
            }
            try {
                managed.instance.onReload();
                reloaded++;
            } catch (final Throwable broken) {
                report.error(managed.descriptor, ModuleLifecycle.RELOAD, describe(broken), broken);
                stop(managed);
                managed.status = ModuleStatus.ERROR;
            }
        }
        return reloaded;
    }

    /** @return every installed module, sorted by id. */
    public List<InstalledModule> modules() {
        return modules.values().stream()
                .map(managed -> new InstalledModule(managed.descriptor, managed.source, managed.status))
                .sorted(Comparator.comparing(module -> key(module.descriptor().id())))
                .toList();
    }

    /**
     * @param moduleId id of the module, upper and lower case are the same id
     * @return the installed module, or empty if no such module is installed
     */
    public Optional<InstalledModule> module(final String moduleId) {
        return Optional.ofNullable(modules.get(key(moduleId)))
                .map(managed -> new InstalledModule(managed.descriptor, managed.source, managed.status));
    }

    /** @return the number of modules that are running. */
    public int enabledCount() {
        return (int) modules.values().stream().filter(managed -> managed.status == ModuleStatus.ENABLED).count();
    }

    /**
     * Stops every running module, in the opposite order of loading, and closes
     * every class loader.
     *
     * <p>This belongs to the shutdown of the server. The decision which module is
     * switched off is not remembered here, because no administrator made it.</p>
     */
    public void disableAll() {
        final List<Managed> ordered = new ArrayList<>(modules.values());
        for (int index = ordered.size() - 1; index >= 0; index--) {
            final Managed managed = ordered.get(index);
            if (managed.status == ModuleStatus.ENABLED) {
                managed.status = stop(managed) ? ModuleStatus.DISABLED : ModuleStatus.ERROR;
            }
            managed.closeClassLoader();
        }
        modules.clear();
    }

    /**
     * Drops every installed module whose jar is gone and that is not running.
     *
     * <p>A module that is still running keeps its entry: its classes are in use
     * and only a restart can really remove them.</p>
     */
    private void forgetRemovedModules(final List<Candidate> candidates) {
        final Set<String> present = candidates.stream()
                .map(candidate -> candidate.source().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        modules.values().removeIf(managed -> {
            if (present.contains(managed.source.toLowerCase(Locale.ROOT))) {
                return false;
            }
            if (managed.wasLoaded()) {
                // The identity of a module that once loaded classes stays known for
                // the whole server run. Otherwise removing the jar, reloading and
                // putting a new jar with the same id back would be a hot swap
                // through the back door.
                managed.reportJarChange(report);
                return false;
            }
            return true;
        });
    }

    /**
     * Sorts out every jar whose module id is used by another jar as well.
     *
     * <p>The id is also the name of the folder below {@code Modules/Configs} and
     * the key a module is registered under, so it has to belong to exactly one
     * module.</p>
     *
     * <p>What happens then depends on whether one of those jars is already
     * installed. If it is, that module keeps running and only the new jars are
     * rejected - a jar that appears in the folder must never be able to push a
     * running module aside. If none of them is installed yet, none of them is
     * installed at all: guessing which one is meant would silently share one
     * folder and overwrite one registration.</p>
     */
    private List<Candidate> withUniqueIds(final List<Candidate> candidates) {
        final Map<String, List<Candidate>> byId = new LinkedHashMap<>();
        for (final Candidate candidate : candidates) {
            // Two ids that only differ in case would still share one folder on a
            // file system that ignores case, so they count as the same id.
            byId.computeIfAbsent(key(candidate.descriptor().id()), id -> new ArrayList<>()).add(candidate);
        }

        final List<Candidate> unique = new ArrayList<>();
        for (final Map.Entry<String, List<Candidate>> entry : byId.entrySet()) {
            final List<Candidate> sameId = entry.getValue();
            if (sameId.size() == 1) {
                unique.add(sameId.getFirst());
                continue;
            }

            final Candidate installed = alreadyInstalled(entry.getKey(), sameId);
            if (installed != null) {
                // The module of this id is already there. It stays exactly as it
                // is, and only the jars that arrived on top of it are rejected.
                unique.add(installed);
                for (final Candidate rejected : sameId) {
                    if (rejected != installed) {
                        report.skipped(rejected.source(), duplicateRejected(rejected, installed.source()));
                    }
                }
                continue;
            }
            for (final Candidate candidate : sameId) {
                final String others = sameId.stream()
                        .filter(other -> other != candidate)
                        .map(Candidate::source)
                        .collect(Collectors.joining(", "));
                report.skipped(candidate.source(), "duplicate module id '" + candidate.descriptor().id()
                        + "'. It is also used by " + others
                        + ", so none of these jars was installed. Give every module its own id.");
            }
        }
        return unique;
    }

    /**
     * @return the candidate that is the module which is already installed under
     *         this id, or {@code null} if none of them is
     */
    private Candidate alreadyInstalled(final String moduleKey, final List<Candidate> sameId) {
        final Managed known = modules.get(moduleKey);
        if (known == null) {
            return null;
        }
        return sameId.stream()
                .filter(candidate -> candidate.source().equalsIgnoreCase(known.source))
                .findFirst()
                .orElse(null);
    }

    /**
     * The one wording for a rejected duplicate.
     *
     * <p>It has to say both things, because only both together are true: the
     * module that is already there keeps running, and the new jar was not
     * installed. "No module with this id was loaded" would be plainly wrong
     * here.</p>
     */
    private static String duplicateRejected(final Candidate rejected, final String runningSource) {
        return "duplicate module id '" + rejected.descriptor().id() + "'. The module of " + runningSource
                + " stays active; the conflicting jar '" + rejected.source() + "' was rejected.";
    }

    /** Registers a new module and starts it if it may run. */
    private void install(final Candidate candidate) {
        final ModuleDescriptor descriptor = candidate.descriptor();
        if (!descriptor.supportsPlatform(platform)) {
            report.skipped(candidate.source(), "the module is built for " + descriptor.platform()
                    + " and does not run on " + platform + ".");
            return;
        }

        final Managed managed = new Managed(descriptor, candidate.jar(), candidate.source());
        modules.put(key(descriptor.id()), managed);
        if (!compatible(managed)) {
            return;
        }
        if (!stateReadable) {
            // MHCenter2 does not know whether an administrator switched this module
            // off, so it stays off. The reason was reported once for the scan.
            managed.status = ModuleStatus.DISABLED;
            return;
        }
        if (administrativelyDisabled.contains(key(descriptor.id()))) {
            managed.status = ModuleStatus.DISABLED;
            report.administrativelyDisabled(descriptor);
            return;
        }
        activate(managed);
    }

    /**
     * Reads which modules an administrator switched off.
     *
     * <p>An unreadable state is never treated as "nothing is switched off". The
     * scan keeps running so the modules stay visible, but none of them is started
     * automatically.</p>
     */
    private void readAdministrativeState() {
        try {
            administrativelyDisabled = normalized(states.disabledModules());
            stateReadable = true;
        } catch (final ModuleStateException failure) {
            administrativelyDisabled = Set.of();
            stateReadable = false;
            report.stateUnreadable(failure);
        }
    }

    /**
     * Checks the version ranges of a module and marks it if it does not fit.
     *
     * <p>A version that does not fit is a normal state and never an exception.</p>
     *
     * @return {@code true} if the module may run on this server
     */
    private boolean compatible(final Managed managed) {
        final ModuleDescriptor descriptor = managed.descriptor;
        if (!descriptor.supportsCenter(centerVersion)) {
            managed.status = ModuleStatus.INCOMPATIBLE_CENTER;
            report.incompatibleCenter(descriptor, centerVersion.display());
            return false;
        }
        // A platform without a single Minecraft version, for example the proxy,
        // cannot check this and must not invent a value either.
        if (minecraftVersion.isPresent() && !descriptor.supportsMinecraft(minecraftVersion.get())) {
            managed.status = ModuleStatus.INCOMPATIBLE_MINECRAFT;
            report.incompatibleMinecraft(descriptor, minecraftVersion.get().display());
            return false;
        }
        return true;
    }

    /** Creates the module, loads it and starts it. */
    private void activate(final Managed managed) {
        final ModuleDescriptor descriptor = managed.descriptor;
        if (!managed.openClassLoader(report)) {
            managed.status = ModuleStatus.ERROR;
            return;
        }

        final CenterModule module;
        try {
            module = instantiate(descriptor, managed.classLoader);
        } catch (final ConfigurationException rejected) {
            managed.status = ModuleStatus.ERROR;
            report.error(descriptor, ModuleLifecycle.LOAD, rejected.getMessage(), rejected.getCause());
            return;
        }

        // From here on the instance and its cleanup are reachable. A module that
        // registers something and only then throws must not stay half alive.
        managed.instance = module;
        managed.cleanup = new ModuleCleanup();

        try {
            module.onLoad(contextFactory.create(descriptor,
                    configsDirectory.resolve(descriptor.id()), managed.cleanup));
        } catch (final Throwable broken) {
            failed(managed, ModuleLifecycle.LOAD, broken);
            return;
        }
        try {
            module.onEnable();
        } catch (final Throwable broken) {
            failed(managed, ModuleLifecycle.ENABLE, broken);
            return;
        }

        managed.status = ModuleStatus.ENABLED;
    }

    /**
     * Marks a module as broken and removes everything it already registered.
     *
     * <p>{@code onDisable} is not called here: the module never reached the
     * enabled state, so only the cleanup it registered itself is run.</p>
     */
    private void failed(final Managed managed, final ModuleLifecycle step, final Throwable broken) {
        managed.status = ModuleStatus.ERROR;
        report.error(managed.descriptor, step, describe(broken), broken);
        // Best effort: a failing cleanup is reported as well, but it never
        // replaces or hides the error that caused it.
        runCleanup(managed);
        managed.instance = null;
    }

    /**
     * Calls {@code onDisable}, runs the cleanup of the module and drops the
     * running instance.
     *
     * <p>The cleanup runs even when {@code onDisable} threw, so a broken module
     * still releases as much as possible.</p>
     *
     * @return {@code true} if the module stopped cleanly
     */
    private boolean stop(final Managed managed) {
        final CenterModule module = managed.instance;
        managed.instance = null;
        boolean clean = true;
        if (module != null) {
            try {
                module.onDisable();
            } catch (final Throwable broken) {
                report.error(managed.descriptor, ModuleLifecycle.DISABLE, describe(broken), broken);
                clean = false;
            }
        }
        return runCleanup(managed) && clean;
    }

    /**
     * Runs the cleanup a module registered.
     *
     * @return {@code true} if every action worked
     */
    private boolean runCleanup(final Managed managed) {
        final ModuleCleanup cleanup = managed.cleanup;
        if (cleanup == null) {
            return true;
        }
        boolean clean = true;
        for (final Throwable failure : cleanup.runAll()) {
            report.error(managed.descriptor, ModuleLifecycle.CLEANUP, describe(failure), failure);
            clean = false;
        }
        return clean;
    }

    /**
     * Remembers the decision of an administrator.
     *
     * <p>The running server always follows the decision. If it cannot be stored,
     * that is reported clearly, because a restart may then bring the module back
     * into the other state.</p>
     */
    private void remember(final Managed managed, final boolean disabled) {
        final String moduleId = managed.descriptor.id();
        final Set<String> updated = new LinkedHashSet<>(administrativelyDisabled);
        if (disabled) {
            updated.add(key(moduleId));
        } else {
            updated.remove(key(moduleId));
        }
        administrativelyDisabled = Set.copyOf(updated);
        try {
            states.setModuleDisabled(key(moduleId), disabled);
        } catch (final ModuleStateException failure) {
            report.statePersistFailed(managed.descriptor, disabled, failure);
        }
    }

    private static ModuleDescriptor readDescriptor(final Path jar, final String source)
            throws ConfigurationException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            final JarEntry entry = jarFile.getJarEntry(Center.MODULE_DESCRIPTOR_FILE);
            if (entry == null) {
                throw new ConfigurationException(source + " does not contain '" + Center.MODULE_DESCRIPTOR_FILE
                        + "' and is therefore no " + Center.PRODUCT_NAME + " module.");
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                return ModuleDescriptor.read(source, in);
            }
        } catch (final IOException failure) {
            throw new ConfigurationException(source + " could not be read: " + failure.getMessage(), failure);
        }
    }

    private static CenterModule instantiate(final ModuleDescriptor descriptor,
                                            final ClassLoader classLoader) throws ConfigurationException {
        final Class<?> type;
        try {
            type = Class.forName(descriptor.mainClass(), true, classLoader);
        } catch (final Throwable missing) {
            throw new ConfigurationException("the main class '" + descriptor.mainClass()
                    + "' was not found: " + describe(missing), missing);
        }
        if (!CenterModule.class.isAssignableFrom(type)) {
            throw new ConfigurationException("the main class '" + descriptor.mainClass()
                    + "' does not implement " + CenterModule.class.getName() + ".");
        }
        try {
            return (CenterModule) type.getDeclaredConstructor().newInstance();
        } catch (final Throwable failure) {
            throw new ConfigurationException("the main class '" + descriptor.mainClass()
                    + "' could not be created, it needs a public constructor without arguments: "
                    + describe(failure), failure);
        }
    }

    /**
     * Lists the module jars.
     *
     * <p>An empty folder and an unreadable folder are two different answers. An
     * empty list means "read, nothing there"; an empty {@link Optional} means "the
     * folder could not be read at all" and stops the whole scan, so a temporary
     * file system problem never makes MHCenter2 forget a known module.</p>
     *
     * @return the jars in order, or empty if the folder could not be read
     */
    private Optional<List<Path>> jars(final Path jarsDirectory) {
        if (!Files.isDirectory(jarsDirectory)) {
            if (Files.exists(jarsDirectory)) {
                // Something is there, but it is not a folder. That is a real
                // problem and certainly not "no module installed".
                report.scanFailed(Center.MODULES_DIRECTORY + "/" + Center.MODULE_JARS_DIRECTORY,
                        new IOException("'" + jarsDirectory.getFileName() + "' is not a folder."));
                return Optional.empty();
            }
            // A folder that is simply not there yet is not a failure: MHCenter2
            // creates it at startup and it holds no module.
            return Optional.of(List.of());
        }
        try (var entries = Files.list(jarsDirectory)) {
            return Optional.of(entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList());
        } catch (final IOException | RuntimeException unreadable) {
            report.scanFailed(Center.MODULES_DIRECTORY + "/" + Center.MODULE_JARS_DIRECTORY, unreadable);
            return Optional.empty();
        }
    }

    private static Set<String> normalized(final Set<String> ids) {
        return ids.stream().map(ModuleLoader::key).collect(Collectors.toUnmodifiableSet());
    }

    private static String key(final String moduleId) {
        return moduleId.toLowerCase(Locale.ROOT);
    }

    private static String describe(final Throwable failure) {
        final String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName() + ".";
        }
        return failure.getClass().getSimpleName() + ": " + message;
    }

    /** One installed module with everything the core needs to control it. */
    private static final class Managed {

        private final ModuleDescriptor descriptor;
        private final Path jar;
        private final String source;

        private ModuleStatus status = ModuleStatus.DISABLED;
        private URLClassLoader classLoader;
        private CenterModule instance;
        private ModuleCleanup cleanup;

        /** Stays {@code true} for the whole server run once the jar was opened. */
        private boolean loadedOnce;

        /** Whether the changed or removed jar was already reported. */
        private boolean jarChangeReported;

        /** Size and change time of the jar this entry was built from. */
        private String loadedJar;

        private Managed(final ModuleDescriptor descriptor, final Path jar, final String source) {
            this.descriptor = descriptor;
            this.jar = jar;
            this.source = source;
            this.loadedJar = fingerprint(jar);
        }

        /**
         * Opens the class loader of this module once and keeps it.
         *
         * @return {@code true} if the class loader is ready
         */
        private boolean openClassLoader(final ModuleReport report) {
            if (classLoader != null) {
                return true;
            }
            try {
                final URL url = jar.toUri().toURL();
                classLoader = new URLClassLoader(new URL[] {url}, CenterModule.class.getClassLoader());
                loadedJar = fingerprint(jar);
                loadedOnce = true;
                return true;
            } catch (final IOException failure) {
                report.error(descriptor, ModuleLifecycle.LOAD,
                        source + " could not be opened: " + failure.getMessage(), failure);
                return false;
            }
        }

        /**
         * @return {@code true} if this module ever opened its jar in this server
         *         run, no matter whether it is running right now
         */
        private boolean wasLoaded() {
            return loadedOnce;
        }

        /**
         * Reports once that the jar of a loaded module changed or disappeared.
         *
         * <p>Only once: the state does not get better by repeating it at every
         * module reload.</p>
         */
        private void reportJarChange(final ModuleReport report) {
            if (jarChangeReported) {
                return;
            }
            jarChangeReported = true;
            report.jarChanged(descriptor, source);
        }

        /** @return {@code true} if the jar on disk differs from the known one. */
        private boolean jarWasReplaced() {
            if (loadedJar.isEmpty()) {
                return false;
            }
            final String current = fingerprint(jar);
            return !current.isEmpty() && !current.equals(loadedJar);
        }

        private void closeClassLoader() {
            if (classLoader == null) {
                return;
            }
            try {
                classLoader.close();
            } catch (final IOException ignored) {
                // The jar handle is released at the latest when the server ends.
            }
            classLoader = null;
        }

        /**
         * The content of the jar, not its size and change time.
         *
         * <p>Two different jars can have the same size, and a change time can be
         * copied or kept, so only the content itself really answers "is this still
         * the binary MHCenter2 loaded?".</p>
         *
         * @return the SHA-256 of the file, or an empty text if it cannot be read
         */
        private static String fingerprint(final Path jar) {
            try (InputStream in = Files.newInputStream(jar)) {
                final MessageDigest digest = MessageDigest.getInstance("SHA-256");
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (final IOException | NoSuchAlgorithmException unreadable) {
                return "";
            }
        }
    }
}
