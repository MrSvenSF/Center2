package net.managerhub.center.common.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import net.managerhub.center.common.language.Language;
import net.managerhub.center.api.ModulePlatform;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModuleTextsTest {

    @Test
    @DisplayName("the platform is shown in German")
    void showsThePlatformInGerman() throws Exception {
        final Language german = bundled("DE");

        assertEquals("Paper", ModuleTexts.platform(german, ModulePlatform.PAPER));
        assertEquals("Velocity", ModuleTexts.platform(german, ModulePlatform.VELOCITY));
        assertEquals("Paper & Velocity", ModuleTexts.platform(german, ModulePlatform.BOTH));
    }

    @Test
    @DisplayName("the platform is shown in English")
    void showsThePlatformInEnglish() throws Exception {
        final Language english = bundled("EN");

        assertEquals("Paper", ModuleTexts.platform(english, ModulePlatform.PAPER));
        assertEquals("Velocity", ModuleTexts.platform(english, ModulePlatform.VELOCITY));
        assertEquals("Paper & Velocity", ModuleTexts.platform(english, ModulePlatform.BOTH));
    }

    @Test
    @DisplayName("a broken module only says that it is broken")
    void showsNoTechnicalDetailForABrokenModule() throws Exception {
        final String german = ModuleTexts.status(bundled("DE"), ModuleStatus.ERROR);
        final String english = ModuleTexts.status(bundled("EN"), ModuleStatus.ERROR);

        assertEquals("Modul Error", german);
        assertEquals("Module error", english);
        for (final String text : new String[] {german, english}) {
            assertTrue(text.lines().count() == 1, text);
            assertTrue(!text.contains("Exception") && !text.contains("java."), text);
        }
    }

    @Test
    @DisplayName("both incompatible states name their own reason")
    void namesTheReasonOfAnIncompatibleModule() throws Exception {
        final Language german = bundled("DE");

        assertEquals("Nicht kompatibel", ModuleTexts.status(german, ModuleStatus.INCOMPATIBLE_CENTER));
        assertTrue(ModuleTexts.reason(german, ModuleStatus.INCOMPATIBLE_CENTER).orElseThrow()
                .contains("MHCenter2"));
        assertTrue(ModuleTexts.reason(german, ModuleStatus.INCOMPATIBLE_MINECRAFT).orElseThrow()
                .contains("Minecraft"));
        assertTrue(ModuleTexts.reason(german, ModuleStatus.ENABLED).isEmpty());
    }

    private static Language bundled(final String code) throws Exception {
        final YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = ModuleTextsTest.class.getClassLoader()
                .getResourceAsStream("defaults/language/" + Language.fileName(code));
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        final Map<String, String> texts = new LinkedHashMap<>();
        for (final String path : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(path) && !"config-version".equals(path)) {
                texts.put(path, yaml.getString(path));
            }
        }
        return Language.of(code, Language.fileName(code), texts);
    }
}
