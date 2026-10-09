package org.c2w;

import org.c2w.infra.Config;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;

/**
 * Points {@link Config} at a config file of its own under {@code target} before any test runs,
 * so code under test that saves the config (e.g. a service remembering the last opened lineup)
 * never overwrites the user's real {@code config.properties}. Registered for every test via
 * {@code META-INF/services} and {@code junit-platform.properties} (extension auto-detection) -
 * for Maven and the IDE alike.
 */
public class TestConfigFileExtension implements BeforeAllCallback {

    /** The config file of the tests. */
    static final Path TEST_CONFIG_FILE = Path.of("target", "test-config.properties").toAbsolutePath();

    static {
        // As early as possible - also before a test class touches Config in a static initializer.
        System.setProperty(Config.CONFIG_FILE_PROPERTY, TEST_CONFIG_FILE.toString());
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        System.setProperty(Config.CONFIG_FILE_PROPERTY, TEST_CONFIG_FILE.toString());
    }
}
