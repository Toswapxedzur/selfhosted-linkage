package net.blopybox.selfhosted_linkage.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * Finds the native transport helper (linkage-iroh / netbird). Order:
 * 1. an override dropped into {@code <stateDir>/bin/}; 2. the copy bundled in the jar under
 * {@code /helpers/<os>-<arch>/} (extracted on first use); 3. the system PATH + usual install dirs.
 */
public final class HelperBinaries {
    private HelperBinaries() {}

    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String o = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
        String a = (arch.contains("aarch64") || arch.contains("arm64")) ? "arm64" : "x64";
        return o + "-" + a;
    }

    public static Path locate(String name, Path stateDir) throws IOException {
        boolean win = platform().startsWith("windows");
        String file = win ? name + ".exe" : name;
        Path binDir = stateDir.resolve("bin");

        Path override = binDir.resolve(file);
        if (Files.isExecutable(override)) return override;

        String res = "/helpers/" + platform() + "/" + file;
        try (InputStream in = HelperBinaries.class.getResourceAsStream(res)) {
            if (in != null) {
                Files.createDirectories(binDir);
                Files.copy(in, override, StandardCopyOption.REPLACE_EXISTING);
                if (!win) override.toFile().setExecutable(true, true);
                return override;
            }
        }

        String path = System.getenv("PATH");
        List<String> extra = List.of("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin");
        for (String dir : ((path == null ? "" : path) + java.io.File.pathSeparator + String.join(java.io.File.pathSeparator, extra))
                .split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) continue;
            Path p = Path.of(dir, file);
            if (Files.isExecutable(p)) return p;
        }
        throw new IOException("helper '" + name + "' not found (looked in " + binDir + ", bundled " + res + ", PATH)");
    }
}
