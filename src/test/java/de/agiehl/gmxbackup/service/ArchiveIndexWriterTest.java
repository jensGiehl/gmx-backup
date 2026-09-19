package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.domain.BackupCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveIndexWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsSelfContainedOfflineIndex() throws Exception {
        var catalog = new BackupCatalog("mail@gmx.de", "2026-09-19T08:00:00Z", 0, List.of("INBOX"), List.of());

        new ArchiveIndexWriter().write(catalog, temporaryDirectory);

        assertThat(temporaryDirectory.resolve("index.html")).content().contains("metadata.js", "assets/archive.js");
        assertThat(temporaryDirectory.resolve("metadata.json")).content().contains("mail@gmx.de", "INBOX");
        assertThat(temporaryDirectory.resolve("assets/bootstrap.min.css")).isNotEmptyFile();
        assertThat(temporaryDirectory.resolve("assets/bootstrap.bundle.min.js")).isNotEmptyFile();
        assertThat(Files.list(temporaryDirectory.resolve("assets"))).hasSize(4);
    }
}
