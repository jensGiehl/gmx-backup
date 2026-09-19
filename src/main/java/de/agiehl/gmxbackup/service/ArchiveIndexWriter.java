package de.agiehl.gmxbackup.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.agiehl.gmxbackup.domain.BackupCatalog;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class ArchiveIndexWriter {

    private static final String WEBJAR_ROOT = "META-INF/resources/webjars/bootstrap/5.3.8/";
    private final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    public void write(BackupCatalog catalog, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        var json = gson.toJson(catalog);
        Files.writeString(outputDirectory.resolve("metadata.json"), json, StandardCharsets.UTF_8);
        Files.writeString(
                outputDirectory.resolve("metadata.js"),
                "window.GMX_BACKUP_DATA = " + json + ";\n",
                StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("index.html"), indexHtml(), StandardCharsets.UTF_8);
        copyAssets(outputDirectory.resolve("assets"));
    }

    private void copyAssets(Path assetsDirectory) throws IOException {
        Files.createDirectories(assetsDirectory);
        copy(WEBJAR_ROOT + "css/bootstrap.min.css", assetsDirectory.resolve("bootstrap.min.css"));
        copy(WEBJAR_ROOT + "js/bootstrap.bundle.min.js", assetsDirectory.resolve("bootstrap.bundle.min.js"));
        copy("archive-assets/archive.css", assetsDirectory.resolve("archive.css"));
        copy("archive-assets/archive.js", assetsDirectory.resolve("archive.js"));
    }

    private void copy(String classpathLocation, Path target) throws IOException {
        try (var input = new ClassPathResource(classpathLocation).getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String indexHtml() {
        return """
                <!doctype html>
                <html lang="de">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="color-scheme" content="light">
                  <title>GMX E-Mail-Archiv</title>
                  <link href="assets/bootstrap.min.css" rel="stylesheet">
                  <link href="assets/archive.css" rel="stylesheet">
                </head>
                <body>
                  <header class="archive-hero">
                    <div class="container py-4 py-lg-5">
                      <div class="d-flex align-items-center gap-3">
                        <div class="archive-mark" aria-hidden="true">✉</div>
                        <div>
                          <p class="eyebrow mb-1">Lokales Archiv</p>
                          <h1 class="h2 mb-1">GMX E-Mail-Backup</h1>
                          <p id="archiveSummary" class="mb-0 text-white-50"></p>
                        </div>
                      </div>
                    </div>
                  </header>
                  <main class="container py-3 py-lg-4">
                    <section class="search-panel shadow-sm" aria-label="Archiv durchsuchen">
                      <label for="searchInput" class="form-label fw-semibold">E-Mails durchsuchen</label>
                      <div class="input-group input-group-lg">
                        <span class="input-group-text" aria-hidden="true">⌕</span>
                        <input id="searchInput" class="form-control" type="search" placeholder="Betreff, Absender, Empfänger oder Inhalt …" autocomplete="off">
                        <button id="clearSearch" class="btn btn-outline-secondary" type="button">Leeren</button>
                      </div>
                      <div class="row g-2 mt-1">
                        <div class="col-12 col-md-7">
                          <label for="folderSelect" class="visually-hidden">Ordner</label>
                          <select id="folderSelect" class="form-select"></select>
                        </div>
                        <div class="col-12 col-md-5">
                          <label for="sortSelect" class="visually-hidden">Sortierung</label>
                          <select id="sortSelect" class="form-select">
                            <option value="newest">Neueste zuerst</option>
                            <option value="oldest">Älteste zuerst</option>
                            <option value="subject">Betreff A–Z</option>
                          </select>
                        </div>
                      </div>
                    </section>
                    <div class="d-flex justify-content-between align-items-center mt-4 mb-2">
                      <h2 class="h5 mb-0">Nachrichten</h2>
                      <span id="resultCount" class="badge text-bg-light"></span>
                    </div>
                    <section id="mailList" class="mail-list" aria-live="polite"></section>
                    <div id="emptyState" class="empty-state d-none">
                      <div class="display-5" aria-hidden="true">⌕</div>
                      <h2 class="h5">Keine E-Mail gefunden</h2>
                      <p class="text-secondary mb-0">Passe Suche oder Ordnerauswahl an.</p>
                    </div>
                  </main>
                  <footer class="container pb-4 text-center small text-secondary">
                    Rein lokales, schreibgeschütztes Archiv · Externe Bilder in E-Mails werden blockiert
                  </footer>
                  <script src="metadata.js"></script>
                  <script src="assets/bootstrap.bundle.min.js"></script>
                  <script src="assets/archive.js"></script>
                </body>
                </html>
                """;
    }
}
