package net.managerhub.center.common.language;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A complete {@link Language} for tests.
 *
 * <p>Every text is its own path plus the placeholders that were handed in, so a
 * test can assert what was logged without depending on the wording of DE.yml or
 * EN.yml. The point of a test is the behaviour, not the translation.</p>
 */
public final class TestLanguages {

    private TestLanguages() {
        throw new AssertionError("No instances.");
    }

    /** @return a language that knows every {@link MessageKey}. */
    public static Language complete() {
        final Map<String, String> texts = new LinkedHashMap<>();
        for (final MessageKey key : MessageKey.values()) {
            texts.put(key.path(), key.path());
        }
        try {
            return Language.of("EN", "Test.yml", texts);
        } catch (final Exception impossible) {
            throw new IllegalStateException("The test language could not be built.", impossible);
        }
    }
}
