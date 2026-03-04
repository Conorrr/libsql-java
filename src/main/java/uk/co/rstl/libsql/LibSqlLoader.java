package uk.co.rstl.libsql;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads liblibsql native library. Tries:
 * 1. Co-located directory (native image mode)
 * 2. native/{os}-{arch}/ on classpath (JAR mode)
 * 3. native/{os}-{arch}/ relative to working dir (dev mode)
 */
public final class LibSqlLoader {

    // Global arena — keeps the library loaded for the lifetime of the JVM.
    // Using Arena.ofAuto() so it's never explicitly closed (survives hot reloads).
    private static final Arena LIB_ARENA = Arena.ofAuto();
    private static volatile SymbolLookup lookup;

    private LibSqlLoader() {}

    public static SymbolLookup get() {
        if (lookup == null) {
            synchronized (LibSqlLoader.class) {
                if (lookup == null) {
                    lookup = load();
                }
            }
        }
        return lookup;
    }

    private static SymbolLookup load() {
        var libName = libName();
        var platform = osArch();

        // 1. Co-located with binary (native-image mode)
        var colocated = Path.of(libName);
        if (Files.exists(colocated)) {
            return SymbolLookup.libraryLookup(colocated, LIB_ARENA);
        }

        // 2. Extract from classpath native/{os}-{arch}/
        var resource = "native/" + platform + "/" + libName;
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                // 3. Try from project native/ dir (dev mode)
                var devPath = Path.of("native", platform, libName);
                if (Files.exists(devPath)) {
                    return SymbolLookup.libraryLookup(devPath, LIB_ARENA);
                }
                throw new RuntimeException("Native library not found: " + resource + " (also tried " + devPath + ")");
            }
            var tmpDir = Files.createTempDirectory("libsql-native");
            var tmpFile = tmpDir.resolve(libName);
            Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            tmpFile.toFile().deleteOnExit();
            tmpDir.toFile().deleteOnExit();
            return SymbolLookup.libraryLookup(tmpFile, LIB_ARENA);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract native library", e);
        }
    }

    static String osArch() {
        var osName = System.getProperty("os.name").toLowerCase();
        var os = osName.contains("mac") || osName.contains("darwin") ? "darwin"
                : osName.contains("win") ? "windows" : "linux";
        var arch = System.getProperty("os.arch");
        var normalizedArch = switch (arch) {
            case "aarch64" -> "aarch64";
            case "amd64", "x86_64" -> "amd64";
            default -> arch;
        };
        return os + "-" + normalizedArch;
    }

    static String libName() {
        var osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) return "liblibsql.dylib";
        if (osName.contains("win")) return "libsql.dll";
        return "liblibsql.so";
    }
}
