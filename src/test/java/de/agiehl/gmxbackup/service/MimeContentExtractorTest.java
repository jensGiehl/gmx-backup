package de.agiehl.gmxbackup.service;

import jakarta.activation.DataHandler;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MimeContentExtractorTest {

    @TempDir
    Path temporaryDirectory;

    private final MimeContentExtractor extractor = new MimeContentExtractor();

    @Test
    void extractsInlineImagesAndAttachmentsAndBlocksRemoteContent() throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        var multipart = new MimeMultipart("related");

        var html = new MimeBodyPart();
        html.setContent("<script>alert(1)</script><p>Hallo</p><img src=\"cid:logo\"><img src=\"https://tracker.example/pixel\">", "text/html; charset=UTF-8");
        multipart.addBodyPart(html);

        var image = new MimeBodyPart();
        image.setDataHandler(new DataHandler(new ByteArrayDataSource(new byte[]{1, 2, 3}, "image/png")));
        image.setFileName("logo.png");
        image.setDisposition(Part.INLINE);
        image.setHeader("Content-ID", "<logo>");
        multipart.addBodyPart(image);

        var attachment = new MimeBodyPart();
        attachment.setText("Notiz", StandardCharsets.UTF_8.name());
        attachment.setFileName("notiz.txt");
        attachment.setDisposition(Part.ATTACHMENT);
        multipart.addBodyPart(attachment);

        message.setContent(multipart);
        message.saveChanges();

        var result = extractor.extract(message, temporaryDirectory.resolve("mail_dateien"), temporaryDirectory);

        assertThat(result.html()).contains("mail_dateien/logo.png", "Hallo", "Externes Bild blockiert");
        assertThat(result.html()).doesNotContain("script", "https://tracker.example");
        assertThat(result.attachments()).extracting("filename").containsExactly("logo.png", "notiz.txt");
        assertThat(Files.readAllBytes(temporaryDirectory.resolve("mail_dateien/logo.png"))).containsExactly(1, 2, 3);
    }
}
