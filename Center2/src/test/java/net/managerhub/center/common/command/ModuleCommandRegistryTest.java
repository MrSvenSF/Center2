package net.managerhub.center.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleCommand;
import net.managerhub.center.common.config.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModuleCommandRegistryTest {

    private static final ModuleCommand NOTHING = sender -> { };

    @Test
    @DisplayName("a free path is accepted")
    void acceptsAFreePath() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();

        assertTrue(registry.register("Test", path("center test"), NOTHING).isEmpty());
        assertEquals(1, registry.all().size());
    }

    @Test
    @DisplayName("a path of the core is refused right away")
    void refusesACorePath() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.reserve(() -> Set.of("center reload", "center modules", "center info", "network info"));

        // The answer has to be honest: the module must not be told that the
        // command was accepted and then find it silently dropped later.
        assertTrue(registry.register("Test", path("center reload"), NOTHING).isPresent());
        assertTrue(registry.register("Test", path("center modules"), NOTHING).isPresent());
        assertTrue(registry.register("Test", path("center info"), NOTHING).isPresent());
        assertTrue(registry.register("Test", path("network info"), NOTHING).isPresent(), "an alias counts as well");
        assertTrue(registry.all().isEmpty());
    }

    @Test
    @DisplayName("upper and lower case are the same path")
    void refusesACorePathWithoutCase() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.reserve(() -> Set.of("center reload"));

        assertTrue(registry.register("Test", new CommandPath(List.of("Center", "Reload")), NOTHING).isPresent());
    }

    @Test
    @DisplayName("a path another module already uses is refused")
    void refusesAPathOfAnotherModule() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        assertTrue(registry.register("First", path("center demo"), NOTHING).isEmpty());

        final Optional<String> refused = registry.register("Second", path("center demo"), NOTHING);

        assertTrue(refused.isPresent());
        assertTrue(refused.get().contains("another module"), refused.get());
        assertEquals(1, registry.all().size());
    }

    @Test
    @DisplayName("one command of a module can be removed again")
    void removesOneCommand() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.register("Test", path("center one"), NOTHING);
        registry.register("Test", path("center two"), NOTHING);

        registry.unregister("Test", path("center one"));

        assertEquals(List.of("center two"),
                registry.all().stream().map(entry -> entry.path().display()).toList());
    }

    @Test
    @DisplayName("every command of a module can be removed at once")
    void removesEveryCommandOfAModule() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.register("Test", path("center one"), NOTHING);
        registry.register("Other", path("center two"), NOTHING);

        registry.unregisterModule("test");

        assertEquals(List.of("Other"), registry.all().stream().map(ModuleCommandRegistry.Registered::moduleId)
                .toList());
        registry.clear();
        assertTrue(registry.all().isEmpty());
    }

    @Test
    @DisplayName("a path that was freed can be used again")
    void allowsAFreedPathAgain() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.register("First", path("center demo"), NOTHING);
        registry.unregisterModule("First");

        assertTrue(registry.register("Second", path("center demo"), NOTHING).isEmpty());
        assertFalse(registry.all().isEmpty());
    }

    @Test
    @DisplayName("a command name another plugin owns is refused, not accepted and then dropped")
    void refusesANameOfAnotherPlugin() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.platform("spawn"::equalsIgnoreCase);

        final Optional<String> refused = registry.register("Test", path("spawn home"), NOTHING);

        assertTrue(refused.isPresent());
        assertTrue(refused.get().contains("another plugin"), refused.get());
        assertTrue(registry.all().isEmpty(), "a refused command must not end up in the registry");
    }

    @Test
    @DisplayName("a name Center2 itself registered is not a name of another plugin")
    void acceptsTheOwnCommandName() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        // The platform answers false for "center", because Center2 owns it.
        registry.platform("spawn"::equalsIgnoreCase);

        assertTrue(registry.register("Test", path("center demo"), NOTHING).isEmpty());
    }

    @Test
    @DisplayName("the core is checked before the platform, so the reason is the useful one")
    void coreWinsOverThePlatform() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.reserve(() -> Set.of("center reload"));
        registry.platform(name -> true);

        final Optional<String> refused = registry.register("Test", path("center reload"), NOTHING);

        assertTrue(refused.isPresent());
        assertTrue(refused.get().contains(Center.PRODUCT_NAME), refused.get());
    }

    private static CommandPath path(final String raw) throws ConfigurationException {
        return CommandPath.of("command", raw);
    }
}
