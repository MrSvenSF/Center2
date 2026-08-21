package net.managerhub.center.paper.config;

import java.nio.file.Path;
import java.util.Map;

import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.language.Language;

/**
 * Reads and validates one file of {@code Language/}.
 *
 * <p>Every text is validated as MiniMessage, because the chat messages of
 * MHCenter2 are rendered with it. Plain log lines simply contain no tag.</p>
 */
final class LanguageLoader {

    private LanguageLoader() {
        throw new AssertionError("No instances.");
    }

    static Language load(final Path file, final String code) throws ConfigurationException {
        final String fileName = Language.fileName(code);
        final YamlReader reader = YamlReader.read(file, fileName);
        reader.requireConfigVersion();

        final Map<String, String> texts = reader.leafTexts("config-version");
        for (final Map.Entry<String, String> text : texts.entrySet()) {
            reader.validateMiniMessage(text.getKey(), text.getValue());
        }
        return Language.of(code, fileName, texts);
    }
}
