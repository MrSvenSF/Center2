package net.managerhub.center.common.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.managerhub.center.common.config.ConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTest {

    @ParameterizedTest
    @DisplayName("every bundled language file contains exactly the known texts")
    @ValueSource(strings = {"DE", "EN"})
    void bundledFileIsComplete(final String code) throws Exception {
        final Language language = Language.of(code, Language.fileName(code), bundled(code));

        assertEquals(code, language.code());
    }

    @Test
    @DisplayName("DE.yml and EN.yml have exactly the same keys")
    void bothBundledFilesHaveTheSameKeys() throws Exception {
        assertEquals(bundled("DE").keySet(), bundled("EN").keySet());
    }

    @Test
    @DisplayName("both bundled files use the same placeholders per text")
    void bothBundledFilesUseTheSamePlaceholders() throws Exception {
        final Map<String, String> german = bundled("DE");
        final Map<String, String> english = bundled("EN");

        for (final Map.Entry<String, String> text : english.entrySet()) {
            assertEquals(placeholders(text.getValue()), placeholders(german.get(text.getKey())),
                    "different placeholders in '" + text.getKey() + "'");
        }
    }

    @Test
    @DisplayName("placeholders are replaced")
    void replacesPlaceholders() throws Exception {
        final Language language = Language.of("EN", "EN.yml", bundled("EN"));

        assertEquals("Center2 is a project by Manager Hub.",
                language.get(MessageKey.MENU_ORGANIZATION_DESCRIPTION_1,
                        "product", "Center2", "organization", "Manager Hub"));
    }

    @Test
    @DisplayName("a missing text is rejected")
    void rejectsMissingText() throws Exception {
        final Map<String, String> texts = new HashMap<>(bundled("EN"));
        texts.remove(MessageKey.PLUGIN_DISABLED.path());

        assertThrows(ConfigurationException.class, () -> Language.of("EN", "EN.yml", texts));
    }

    @Test
    @DisplayName("an unknown text is rejected")
    void rejectsUnknownText() throws Exception {
        final Map<String, String> texts = new HashMap<>(bundled("EN"));
        texts.put("plugin.something-new", "text");

        assertThrows(ConfigurationException.class, () -> Language.of("EN", "EN.yml", texts));
    }

    @Test
    @DisplayName("only DE and EN are supported")
    void acceptsOnlySupportedCodes() throws ConfigurationException {
        assertEquals("DE", Language.normalizeCode("language", "de"));
        assertEquals("EN", Language.normalizeCode("language", " EN "));
        assertThrows(ConfigurationException.class, () -> Language.normalizeCode("language", "FR"));
        assertThrows(ConfigurationException.class, () -> Language.normalizeCode("language", ""));
        assertThrows(ConfigurationException.class, () -> Language.normalizeCode("language", null));
    }

    /** @return every {@code {name}} placeholder used inside a text. */
    private static Set<String> placeholders(final String text) {
        final Set<String> names = new TreeSet<>();
        final Matcher matcher = Pattern.compile("\\{([a-z]+)}").matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** @return every text of a bundled language file as {@code path -> text}. */
    private static Map<String, String> bundled(final String code) throws IOException {
        final String resource = "defaults/language/" + Language.fileName(code);
        try (InputStream stream = LanguageTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("The bundled language file '" + resource + "' is missing.");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                final Map<String, String> texts = new LinkedHashMap<>();
                for (final String path : yaml.getKeys(true)) {
                    if ("config-version".equals(path) || yaml.isConfigurationSection(path)) {
                        continue;
                    }
                    texts.put(path, yaml.getString(path));
                }
                return texts;
            }
        }
    }
}
