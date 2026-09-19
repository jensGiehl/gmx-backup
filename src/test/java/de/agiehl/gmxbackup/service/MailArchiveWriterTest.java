package de.agiehl.gmxbackup.service;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HexFormat;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MailArchiveWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesDateTimestampAndContentHashForFileAndDirectoryNames() throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("sender@example.com"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("receiver@example.com"));
        message.setSubject("Ein Betreff, der nicht im Dateinamen landet");
        var sentAt = Instant.parse("2026-09-19T10:15:30.123Z");
        message.setSentDate(Date.from(sentAt));
        message.setText("Nachrichteninhalt");
        message.saveChanges();

        var folderDirectory = temporaryDirectory.resolve("mail").resolve("INBOX");
        var metadata = new MailArchiveWriter(new MimeContentExtractor())
                .write(message, 42, "INBOX", folderDirectory, temporaryDirectory);

        var emlPath = temporaryDirectory.resolve(metadata.emlFile());
        var htmlPath = temporaryDirectory.resolve(metadata.htmlFile());
        var expectedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_SSS")
                .format(message.getSentDate().toInstant().atZone(ZoneId.systemDefault()));
        var expectedHash = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(emlPath)), 0, 8);
        var expectedBaseName = expectedDate + "_" + expectedHash;

        assertThat(emlPath.getFileName().toString()).isEqualTo(expectedBaseName + ".eml");
        assertThat(htmlPath.getFileName().toString()).isEqualTo(expectedBaseName + ".html");
        assertThat(folderDirectory.resolve(expectedBaseName)).isDirectory();
        assertThat(folderDirectory).isDirectoryNotContaining(path -> path.getFileName().toString().endsWith(".eml.tmp"));
    }
}
