package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.domain.AttachmentMetadata;
import de.agiehl.gmxbackup.domain.EmailMetadata;
import de.agiehl.gmxbackup.util.ArchivePaths;
import de.agiehl.gmxbackup.util.PathSanitizer;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
public class MailArchiveWriter {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final MimeContentExtractor contentExtractor;

    public MailArchiveWriter(MimeContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;
    }

    public EmailMetadata write(Message message, long uid, String folderName, Path folderDirectory, Path outputRoot)
            throws MessagingException, IOException {
        Files.createDirectories(folderDirectory);
        var subject = StringUtils.hasText(message.getSubject()) ? message.getSubject() : "Ohne Betreff";
        var baseName = createBaseName(message, uid, subject);
        var htmlPath = folderDirectory.resolve(baseName + ".html");
        var emlPath = folderDirectory.resolve(baseName + ".eml");
        var assetsPath = folderDirectory.resolve(baseName + "_dateien");

        try (var output = Files.newOutputStream(emlPath)) {
            message.writeTo(output);
        }
        var extracted = contentExtractor.extract(message, assetsPath, outputRoot);
        var metadata = toMetadata(message, uid, folderName, subject, extracted, htmlPath, emlPath, outputRoot);
        Files.writeString(htmlPath, renderMessage(metadata, extracted.html(), htmlPath, outputRoot), StandardCharsets.UTF_8);
        return metadata;
    }

    private EmailMetadata toMetadata(
            Message message,
            long uid,
            String folderName,
            String subject,
            MimeContentExtractor.ExtractedContent extracted,
            Path htmlPath,
            Path emlPath,
            Path outputRoot) throws MessagingException {
        var messageId = firstHeader(message, "Message-ID");
        var id = folderName + ":" + uid;
        var excerptSource = StringUtils.hasText(extracted.plainText())
                ? extracted.plainText()
                : Jsoup.parse(extracted.html()).text();
        var excerpt = excerptSource.replaceAll("\\s+", " ").strip();
        if (excerpt.length() > 320) {
            excerpt = excerpt.substring(0, 320).stripTrailing() + "…";
        }
        return new EmailMetadata(
                id,
                uid,
                folderName,
                subject,
                addresses(message.getFrom()),
                addresses(message.getReplyTo()),
                addresses(message.getRecipients(Message.RecipientType.TO)),
                addresses(message.getRecipients(Message.RecipientType.CC)),
                addresses(message.getRecipients(Message.RecipientType.BCC)),
                isoDate(message.getSentDate()),
                isoDate(message.getReceivedDate()),
                messageId,
                flags(message.getFlags()),
                message.getContentType(),
                message.getSize(),
                excerpt,
                ArchivePaths.portable(outputRoot, htmlPath),
                ArchivePaths.portable(outputRoot, emlPath),
                extracted.attachments());
    }

    private String renderMessage(EmailMetadata mail, String body, Path htmlPath, Path outputRoot) {
        var relativeAssets = htmlPath.getParent().relativize(outputRoot.resolve("assets")).toString().replace('\\', '/');
        var attachmentSection = mail.attachments().isEmpty() ? "" : renderAttachments(mail.attachments(), htmlPath, outputRoot);
        return """
                <!doctype html>
                <html lang="de">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="color-scheme" content="light">
                  <title>%s</title>
                  <link href="%s/bootstrap.min.css" rel="stylesheet">
                  <link href="%s/archive.css" rel="stylesheet">
                </head>
                <body class="mail-page">
                  <main class="container py-3 py-lg-5">
                    <a class="btn btn-sm btn-outline-primary mb-3" href="%s">← Zur Übersicht</a>
                    <article class="mail-sheet shadow-sm">
                      <header class="mail-header">
                        <div class="mail-folder">%s</div>
                        <h1>%s</h1>
                        <dl class="mail-meta">
                          <dt>Von</dt><dd>%s</dd>
                          <dt>An</dt><dd>%s</dd>
                          <dt>Datum</dt><dd>%s</dd>
                        </dl>
                      </header>
                      %s
                      <section class="mail-body">%s</section>
                    </article>
                  </main>
                  <script src="%s/bootstrap.bundle.min.js"></script>
                </body>
                </html>
                """.formatted(
                escape(mail.subject()),
                relativeAssets,
                relativeAssets,
                htmlPath.getParent().relativize(outputRoot.resolve("index.html")).toString().replace('\\', '/'),
                escape(mail.folder()),
                escape(mail.subject()),
                escape(join(mail.from())),
                escape(join(mail.to())),
                escape(displayDate(mail.sentAt(), mail.receivedAt())),
                attachmentSection,
                body,
                relativeAssets);
    }

    private String renderAttachments(List<AttachmentMetadata> attachments, Path htmlPath, Path outputRoot) {
        var items = new StringBuilder();
        attachments.forEach(attachment -> {
            var absolute = outputRoot.resolve(attachment.path());
            var link = htmlPath.getParent().relativize(absolute).toString().replace('\\', '/');
            items.append("<a class=\"attachment\" href=\"")
                    .append(escape(link))
                    .append("\" download><span aria-hidden=\"true\">📎</span> ")
                    .append(escape(attachment.filename()))
                    .append(" <small>")
                    .append(humanSize(attachment.size()))
                    .append("</small></a>");
        });
        return "<section class=\"mail-attachments\"><h2>Anhänge</h2><div class=\"attachment-list\">" + items + "</div></section>";
    }

    private String createBaseName(Message message, long uid, String subject) throws MessagingException {
        var date = message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate();
        var datePart = date == null
                ? "ohne-datum"
                : FILE_DATE.format(date.toInstant().atZone(ZoneId.systemDefault()));
        return datePart + "_" + Math.max(uid, message.getMessageNumber()) + "_" + PathSanitizer.fileSegment(subject, "ohne-betreff");
    }

    private List<String> addresses(Address[] values) {
        if (values == null) {
            return List.of();
        }
        return Arrays.stream(values)
                .map(address -> address instanceof InternetAddress internetAddress ? internetAddress.toUnicodeString() : address.toString())
                .toList();
    }

    private List<String> flags(Flags flags) {
        var system = Arrays.stream(flags.getSystemFlags()).map(Object::toString);
        var user = Arrays.stream(flags.getUserFlags());
        return java.util.stream.Stream.concat(system, user).sorted().toList();
    }

    private String firstHeader(Message message, String name) throws MessagingException {
        var values = message.getHeader(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private String isoDate(Date date) {
        return date == null ? null : date.toInstant().toString();
    }

    private String displayDate(String sentAt, String receivedAt) {
        return sentAt != null ? sentAt : receivedAt != null ? receivedAt : "Unbekannt";
    }

    private String join(List<String> values) {
        return values.isEmpty() ? "—" : String.join(", ", values);
    }

    private String humanSize(long bytes) {
        if (bytes < 1_024) {
            return bytes + " B";
        }
        if (bytes < 1_048_576) {
            return "%.1f KB".formatted(bytes / 1_024.0);
        }
        return "%.1f MB".formatted(bytes / 1_048_576.0);
    }

    private String escape(String value) {
        return Entities.escape(value == null ? "" : value);
    }
}
