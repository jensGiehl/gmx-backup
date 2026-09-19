package de.agiehl.gmxbackup.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class PathSanitizer {

    private static final Set<String> WINDOWS_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private PathSanitizer() {
    }

    public static String folderSegment(String value) {
        var cleaned = normalize(value)
                .replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_")
                .replaceAll("[. ]+$", "")
                .strip();
        if (cleaned.isBlank()) {
            return "Unbenannt";
        }
        if (WINDOWS_NAMES.contains(cleaned.toUpperCase(Locale.ROOT))) {
            return "_" + cleaned;
        }
        return truncate(cleaned, 80);
    }

    public static String fileSegment(String value, String fallback) {
        var cleaned = folderSegment(value == null ? "" : value)
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return "Unbenannt".equals(cleaned) ? fallback : truncate(cleaned, 72);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength).stripTrailing();
    }
}
