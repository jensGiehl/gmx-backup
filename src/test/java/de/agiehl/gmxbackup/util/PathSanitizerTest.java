package de.agiehl.gmxbackup.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PathSanitizerTest {

    @Test
    void sanitizesWindowsNamesAndInvalidCharacters() {
        assertThat(PathSanitizer.folderSegment("CON")).isEqualTo("_CON");
        assertThat(PathSanitizer.folderSegment("Rechnung: April?. ")).isEqualTo("Rechnung_ April_");
    }

    @Test
    void recreatesMailboxHierarchy() {
        var path = ArchivePaths.folderPath(Path.of("mail"), "Archiv/2026/Kunden", '/');

        assertThat(path).isEqualTo(Path.of("mail", "Archiv", "2026", "Kunden"));
    }
}
