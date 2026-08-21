package net.managerhub.center.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionalConfigurationTest {

    @Test
    @DisplayName("a valid reload replaces the active configuration")
    void appliesValidReload() throws ConfigurationException {
        final AtomicReference<String> source = new AtomicReference<>("v1");
        final List<String> activated = new ArrayList<>();
        final TransactionalConfiguration<String> configuration =
                new TransactionalConfiguration<>(source::get, activated::add);

        configuration.initialize();
        source.set("v2");
        configuration.reload();

        assertEquals("v2", configuration.current());
        assertEquals(List.of("v1", "v2"), activated);
    }

    @Test
    @DisplayName("an invalid configuration keeps the last working configuration")
    void keepsPreviousConfigurationWhenValidationFails() throws ConfigurationException {
        final AtomicReference<String> source = new AtomicReference<>("v1");
        final List<String> activated = new ArrayList<>();
        final TransactionalConfiguration<String> configuration = new TransactionalConfiguration<>(
                () -> {
                    final String value = source.get();
                    if (value == null) {
                        throw new ConfigurationException("Commands.yml: 'root.label' is invalid.");
                    }
                    return value;
                },
                activated::add);

        configuration.initialize();
        source.set(null);

        assertThrows(ConfigurationException.class, configuration::reload);
        assertEquals("v1", configuration.current());
        assertEquals(List.of("v1"), activated);
    }

    @Test
    @DisplayName("a failed activation restores the last working configuration")
    void restoresPreviousConfigurationWhenActivationFails() throws ConfigurationException {
        final AtomicReference<String> source = new AtomicReference<>("v1");
        final List<String> activated = new ArrayList<>();
        final TransactionalConfiguration<String> configuration = new TransactionalConfiguration<>(
                source::get,
                value -> {
                    activated.add(value);
                    if ("v2".equals(value)) {
                        throw new ConfigurationException("The command could not be registered.");
                    }
                });

        configuration.initialize();
        source.set("v2");

        assertThrows(ConfigurationException.class, configuration::reload);
        assertEquals("v1", configuration.current());
        assertEquals(List.of("v1", "v2", "v1"), activated);
    }

    @Test
    @DisplayName("the configuration is only readable after the initialization")
    void requiresInitialization() {
        final TransactionalConfiguration<String> configuration =
                new TransactionalConfiguration<>(() -> "v1", value -> { });

        assertThrows(IllegalStateException.class, configuration::current);
    }
}
