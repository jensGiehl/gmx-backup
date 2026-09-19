package de.agiehl.gmxbackup.util;

import java.nio.file.Path;
import java.util.Arrays;

public final class ArchivePaths {

    private ArchivePaths() {
    }

    public static Path folderPath(Path root, String fullName, char separator) {
        var separatorPattern = separator == 0 ? "[/\\\\]" : java.util.regex.Pattern.quote(String.valueOf(separator));
        return Arrays.stream(fullName.split(separatorPattern))
                .filter(segment -> !segment.isBlank())
                .map(PathSanitizer::folderSegment)
                .reduce(root, Path::resolve, Path::resolve);
    }

    public static String portable(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}
