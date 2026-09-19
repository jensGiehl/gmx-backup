package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.domain.AttachmentMetadata;
import de.agiehl.gmxbackup.util.ArchivePaths;
import de.agiehl.gmxbackup.util.PathSanitizer;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeUtility;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Component
public class MimeContentExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MimeContentExtractor.class);

    public ExtractedContent extract(Part message, Path assetsDirectory, Path outputRoot) throws MessagingException, IOException {
        Files.createDirectories(assetsDirectory);
        var state = new ExtractionState(assetsDirectory, outputRoot);
        visitSafely(message, state);
        var rawBody = state.htmlBodies.isEmpty()
                ? plainTextAsHtml(String.join("\n\n", state.plainBodies))
                : String.join("<hr>", state.htmlBodies);
        return new ExtractedContent(
                sanitize(rawBody, state.contentReferences),
                String.join("\n\n", state.plainBodies),
                List.copyOf(state.attachments));
    }

    private void visit(Part part, ExtractionState state) throws MessagingException, IOException {
        var disposition = part.getDisposition();
        var filename = decodeFilename(part.getFileName());
        var explicitAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || StringUtils.hasText(filename);

        if (part.isMimeType("multipart/*")) {
            var multipart = (Multipart) part.getContent();
            for (var index = 0; index < multipart.getCount(); index++) {
                visitSafely(multipart.getBodyPart(index), state);
            }
            return;
        }

        if (part.isMimeType("message/rfc822")) {
            saveEmbeddedMessage(part, filename, state);
            return;
        }

        if (part.isMimeType("text/html") && !explicitAttachment) {
            state.htmlBodies.add((String) part.getContent());
            return;
        }

        if (part.isMimeType("text/plain") && !explicitAttachment) {
            state.plainBodies.add((String) part.getContent());
            return;
        }

        var inline = Part.INLINE.equalsIgnoreCase(disposition) || firstHeader(part, "Content-ID") != null;
        saveBinaryPart(part, filename, inline, state);
    }

    private void saveEmbeddedMessage(Part part, String filename, ExtractionState state) throws MessagingException, IOException {
        var contentType = parsedContentType(part);
        var target = uniqueTarget(state.assetsDirectory, StringUtils.hasText(filename) ? filename : "eingebettete-nachricht.eml", state.usedFilenames);
        try {
            var content = part.getContent();
            if (content instanceof Message message) {
                try (var output = Files.newOutputStream(target)) {
                    message.writeTo(output);
                }
            } else {
                try (var input = part.getInputStream()) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            state.attachments.add(metadata(target, contentType, false, state.outputRoot));
        } catch (MessagingException | IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    private void saveBinaryPart(Part part, String filename, boolean inline, ExtractionState state) throws MessagingException, IOException {
        var contentType = parsedContentType(part);
        var effectiveName = StringUtils.hasText(filename) ? filename : generatedFilename(part, state.attachments.size() + 1);
        var target = uniqueTarget(state.assetsDirectory, effectiveName, state.usedFilenames);
        try {
            try (var input = part.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            state.attachments.add(metadata(target, contentType, inline, state.outputRoot));
            var relative = state.assetsDirectory.getFileName() + "/" + target.getFileName();
            registerReference(firstHeader(part, "Content-ID"), relative, state.contentReferences);
            registerReference(firstHeader(part, "Content-Location"), relative, state.contentReferences);
        } catch (MessagingException | IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    private AttachmentMetadata metadata(Path target, String contentType, boolean inline, Path outputRoot) throws IOException {
        return new AttachmentMetadata(
                target.getFileName().toString(),
                ArchivePaths.portable(outputRoot, target),
                contentType,
                Files.size(target),
                inline);
    }

    private String sanitize(String html, Map<String, String> contentReferences) {
        var document = Jsoup.parseBodyFragment(html == null ? "" : html);
        document.select("img[src]").forEach(image -> {
            var source = image.attr("src").strip();
            var lookup = source.toLowerCase(Locale.ROOT).startsWith("cid:") ? source.substring(4) : source;
            lookup = trimBrackets(lookup).toLowerCase(Locale.ROOT);
            if (contentReferences.containsKey(lookup)) {
                image.attr("src", "backup:" + contentReferences.get(lookup));
            } else if (source.matches("(?i)^(https?:)?//.*")) {
                image.removeAttr("src");
                image.attr("alt", "Externes Bild blockiert: " + image.attr("alt"));
                image.addClass("blocked-remote-image");
            }
        });
        var safelist = Safelist.relaxed()
                .addTags("section", "article", "header", "footer")
                .addAttributes(":all", "class")
                .addProtocols("img", "src", "backup")
                .preserveRelativeLinks(true);
        var settings = new Document.OutputSettings().prettyPrint(false);
        var cleaned = Jsoup.parseBodyFragment(Jsoup.clean(document.body().html(), "", safelist, settings));
        cleaned.select("img[src^=backup:]").forEach(image -> image.attr("src", image.attr("src").substring("backup:".length())));
        cleaned.outputSettings(settings);
        return cleaned.body().html();
    }

    private String plainTextAsHtml(String text) {
        if (!StringUtils.hasText(text)) {
            return "<p class=\"text-secondary\">Diese Nachricht enthält keinen darstellbaren Text.</p>";
        }
        return "<pre class=\"mail-plain-text\">" + org.jsoup.nodes.Entities.escape(text) + "</pre>";
    }

    private String generatedFilename(Part part, int index) throws MessagingException {
        var contentType = parsedContentType(part).toLowerCase(Locale.ROOT);
        var extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            case "application/pdf" -> ".pdf";
            case "text/calendar" -> ".ics";
            default -> ".bin";
        };
        return "datei-" + index + extension;
    }

    private Path uniqueTarget(Path directory, String filename, Set<String> usedFilenames) {
        var sanitized = PathSanitizer.fileSegment(filename, "datei.bin");
        var dot = sanitized.lastIndexOf('.');
        var basename = dot > 0 ? sanitized.substring(0, dot) : sanitized;
        var extension = dot > 0 ? sanitized.substring(dot) : "";
        var candidate = directory.resolve(sanitized);
        var counter = 2;
        while (!usedFilenames.add(candidate.getFileName().toString().toLowerCase(Locale.ROOT))) {
            candidate = directory.resolve(basename + "-" + counter++ + extension);
        }
        return candidate;
    }

    private String decodeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        try {
            return MimeUtility.decodeText(filename);
        } catch (Exception ignored) {
            return filename;
        }
    }

    private String firstHeader(Part part, String name) throws MessagingException {
        var headers = part.getHeader(name);
        return headers == null || headers.length == 0 ? null : headers[0];
    }

    private String parsedContentType(Part part) throws MessagingException {
        return new ContentType(part.getContentType()).getBaseType();
    }

    private void visitSafely(Part part, ExtractionState state) {
        try {
            visit(part, state);
        } catch (Exception exception) {
            LOGGER.warn("Fehlerhafter MIME-Teil oder Anhang '{}' wird übersprungen: {}", filenameForLog(part), exception.getMessage());
        }
    }

    private String filenameForLog(Part part) {
        try {
            var filename = decodeFilename(part.getFileName());
            return StringUtils.hasText(filename) ? filename : part.getContentType();
        } catch (Exception exception) {
            return "unbekannt";
        }
    }

    private void registerReference(String header, String path, Map<String, String> references) {
        if (StringUtils.hasText(header)) {
            references.put(trimBrackets(header).toLowerCase(Locale.ROOT), path.replace('\\', '/'));
        }
    }

    private String trimBrackets(String value) {
        var result = value.strip();
        return result.startsWith("<") && result.endsWith(">") ? result.substring(1, result.length() - 1) : result;
    }

    public record ExtractedContent(String html, String plainText, List<AttachmentMetadata> attachments) {
    }

    private static final class ExtractionState {
        private final Path assetsDirectory;
        private final Path outputRoot;
        private final List<String> htmlBodies = new ArrayList<>();
        private final List<String> plainBodies = new ArrayList<>();
        private final List<AttachmentMetadata> attachments = new ArrayList<>();
        private final Map<String, String> contentReferences = new LinkedHashMap<>();
        private final Set<String> usedFilenames = new HashSet<>();

        private ExtractionState(Path assetsDirectory, Path outputRoot) {
            this.assetsDirectory = assetsDirectory;
            this.outputRoot = outputRoot;
        }
    }
}
