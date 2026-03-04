package uk.co.rstl.libsql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LibSqlLoaderTest {

    @Test
    void osArchReturnsValidPlatformString() {
        var result = LibSqlLoader.osArch();
        assertNotNull(result);
        assertTrue(result.contains("-"), "Expected format: os-arch, got: " + result);
        var parts = result.split("-");
        assertEquals(2, parts.length);
        assertTrue(parts[0].matches("darwin|linux|windows"), "Unexpected OS: " + parts[0]);
        assertTrue(parts[1].matches("aarch64|amd64|.*"), "Unexpected arch: " + parts[1]);
    }

    @Test
    void libNameReturnsCorrectFilenamePerOs() {
        var name = LibSqlLoader.libName();
        assertNotNull(name);
        var os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            assertEquals("liblibsql.dylib", name);
        } else if (os.contains("win")) {
            assertEquals("libsql.dll", name);
        } else {
            assertEquals("liblibsql.so", name);
        }
    }
}
