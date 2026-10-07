package org.c2w.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The tests never use the user's real config file - see {@code TestConfigFileExtension}. */
class ConfigFileTest {

    @Test
    @DisplayName("During the tests, Config reads and writes target/test-config.properties")
    void testsUseTheirOwnConfigFile() {
        Path file = Config.configFile().toAbsolutePath().normalize();

        assertEquals(Path.of("target", "test-config.properties").toAbsolutePath().normalize(), file);
        assertNotEquals(Path.of("config.properties").toAbsolutePath().normalize(), file);
    }
}
