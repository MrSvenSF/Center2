package net.managerhub.center.common.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.util.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModuleDescriptorTest {

    private static final String COMPLETE = """
            id=TestModule
            name=MHCenter2 TestModule
            version=0.2.0
            author=Manager Hub
            main=net.managerhub.center.testmodule.TestModule
            platform=PAPER
            center-min-version=0.2.0
            center-max-version=0.2.99
            minecraft-min-version=1.21.4
            minecraft-max-version=1.21.8
            """;

    @Test
    @DisplayName("complete metadata is read")
    void readsCompleteMetadata() throws ConfigurationException {
        final ModuleDescriptor descriptor = read(COMPLETE);

        assertEquals("TestModule", descriptor.id());
        assertEquals("MHCenter2 TestModule", descriptor.name());
        assertEquals("0.2.0", descriptor.version());
        assertEquals("Manager Hub", descriptor.author());
        assertEquals("net.managerhub.center.testmodule.TestModule", descriptor.mainClass());
        assertEquals(ModulePlatform.PAPER, descriptor.platform());
        assertEquals("0.2.0 - 0.2.99", descriptor.centerVersions().display());
        assertEquals("1.21.4 - 1.21.8", descriptor.minecraftVersions().orElseThrow().display());
    }

    @ParameterizedTest
    @DisplayName("a missing entry is rejected")
    @ValueSource(strings = {"id", "name", "version", "author", "main", "platform",
            "center-min-version", "center-max-version", "minecraft-min-version", "minecraft-max-version"})
    void rejectsMissingEntry(final String key) {
        final ConfigurationException failure =
                assertThrows(ConfigurationException.class, () -> read(without(COMPLETE, key)));
        assertTrue(failure.getMessage().contains(key), failure.getMessage());
    }

    @Test
    @DisplayName("name and author are read as UTF-8")
    void readsUtf8Metadata() throws ConfigurationException {
        final ModuleDescriptor descriptor = read(COMPLETE
                .replace("name=MHCenter2 TestModule", "name=Überwachung")
                .replace("author=Manager Hub", "author=Müller"));

        assertEquals("Überwachung", descriptor.name());
        assertEquals("Müller", descriptor.author());
    }

    @Test
    @DisplayName("a proxy only module does not need a Minecraft range")
    void acceptsVelocityModuleWithoutMinecraftRange() throws ConfigurationException {
        final String velocity = without(without(COMPLETE.replace("platform=PAPER", "platform=VELOCITY"),
                "minecraft-min-version"), "minecraft-max-version");

        final ModuleDescriptor descriptor = read(velocity);

        assertTrue(descriptor.minecraftVersions().isEmpty());
        // Without a range the Minecraft check never blocks the module.
        assertTrue(descriptor.supportsMinecraft(version("1.99.0")));
    }

    @Test
    @DisplayName("a module that also runs on Paper needs a Minecraft range")
    void requiresMinecraftRangeForBoth() {
        final String both = without(COMPLETE.replace("platform=PAPER", "platform=BOTH"), "minecraft-min-version");

        assertThrows(ConfigurationException.class, () -> read(both));
    }

    @ParameterizedTest
    @DisplayName("an unknown platform is rejected")
    @ValueSource(strings = {"paper2", "SPIGOT", "ALL", " "})
    void rejectsUnknownPlatform(final String platform) {
        assertThrows(ConfigurationException.class,
                () -> read(COMPLETE.replace("platform=PAPER", "platform=" + platform)));
    }

    @Test
    @DisplayName("the platform is read without case")
    void readsPlatformWithoutCase() throws ConfigurationException {
        assertEquals(ModulePlatform.BOTH, read(COMPLETE.replace("platform=PAPER", "platform=both")).platform());
    }

    @ParameterizedTest
    @DisplayName("an id that cannot be a folder name is rejected")
    @ValueSource(strings = {"Test Module", "../escape", "test/module", "test.module"})
    void rejectsInvalidId(final String id) {
        assertThrows(ConfigurationException.class, () -> read(COMPLETE.replace("id=TestModule", "id=" + id)));
    }

    @ParameterizedTest
    @DisplayName("a main entry that is no class name is rejected")
    @ValueSource(strings = {"not a class", "net..module", "net.module."})
    void rejectsInvalidMainClass(final String mainClass) {
        assertThrows(ConfigurationException.class,
                () -> read(COMPLETE.replace("main=net.managerhub.center.testmodule.TestModule", "main=" + mainClass)));
    }

    @ParameterizedTest
    @DisplayName("a version that is no number is rejected")
    @ValueSource(strings = {"center-min-version=latest", "minecraft-max-version=newest"})
    void rejectsUnusableVersion(final String replacement) {
        final String key = replacement.substring(0, replacement.indexOf('='));
        assertThrows(ConfigurationException.class,
                () -> read(without(COMPLETE, key) + replacement + "\n"));
    }

    @Test
    @DisplayName("a minimum that is newer than the maximum is rejected")
    void rejectsInvertedRange() {
        assertThrows(ConfigurationException.class,
                () -> read(COMPLETE.replace("center-min-version=0.2.0", "center-min-version=0.3.0")));
        assertThrows(ConfigurationException.class,
                () -> read(COMPLETE.replace("minecraft-min-version=1.21.4", "minecraft-min-version=1.21.9")));
    }

    @Test
    @DisplayName("a module only runs on the platform it was built for")
    void checksThePlatform() throws ConfigurationException {
        final ModuleDescriptor paperOnly = read(COMPLETE);

        assertTrue(paperOnly.supportsPlatform(ModulePlatform.PAPER));
        assertFalse(paperOnly.supportsPlatform(ModulePlatform.VELOCITY));
    }

    @Test
    @DisplayName("a module for both platforms is accepted everywhere")
    void acceptsBothPlatforms() throws ConfigurationException {
        final ModuleDescriptor both = read(COMPLETE.replace("platform=PAPER", "platform=BOTH"));

        assertTrue(both.supportsPlatform(ModulePlatform.PAPER));
        assertTrue(both.supportsPlatform(ModulePlatform.VELOCITY));
    }

    @Test
    @DisplayName("a MHCenter2 version below the minimum is not supported")
    void refusesCenterVersionBelowMinimum() throws ConfigurationException {
        assertFalse(read(COMPLETE).supportsCenter(version("0.1.9")));
    }

    @Test
    @DisplayName("a MHCenter2 version above the maximum is not supported")
    void refusesCenterVersionAboveMaximum() throws ConfigurationException {
        assertFalse(read(COMPLETE).supportsCenter(version("0.3.0")));
    }

    @Test
    @DisplayName("a MHCenter2 version inside the range is supported")
    void acceptsCenterVersionInsideTheRange() throws ConfigurationException {
        final ModuleDescriptor descriptor = read(COMPLETE);

        assertTrue(descriptor.supportsCenter(version("0.2.0")));
        assertTrue(descriptor.supportsCenter(version("0.2.5")));
        assertTrue(descriptor.supportsCenter(version("0.2.99")));
    }

    @Test
    @DisplayName("a Minecraft version outside the range is not supported")
    void refusesMinecraftVersionOutsideTheRange() throws ConfigurationException {
        final ModuleDescriptor descriptor = read(COMPLETE);

        assertFalse(descriptor.supportsMinecraft(version("1.21.3")));
        assertFalse(descriptor.supportsMinecraft(version("1.21.9")));
    }

    @Test
    @DisplayName("a Minecraft version inside the range is supported")
    void acceptsMinecraftVersionInsideTheRange() throws ConfigurationException {
        final ModuleDescriptor descriptor = read(COMPLETE);

        assertTrue(descriptor.supportsMinecraft(version("1.21.4")));
        assertTrue(descriptor.supportsMinecraft(version("1.21.6")));
        assertTrue(descriptor.supportsMinecraft(version("1.21.8")));
    }

    private static String without(final String properties, final String key) {
        return properties.lines()
                .filter(line -> !line.startsWith(key + "="))
                .reduce("", (left, right) -> left + right + "\n");
    }

    private static Version version(final String raw) {
        return Version.of(raw).orElseThrow();
    }

    private static ModuleDescriptor read(final String properties) throws ConfigurationException {
        return ModuleDescriptor.read("MHCenter2-TestModule-0.2.0.jar",
                new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
    }
}
