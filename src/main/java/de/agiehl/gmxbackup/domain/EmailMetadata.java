package de.agiehl.gmxbackup.domain;

import java.util.List;

public record EmailMetadata(
        String id,
        long uid,
        String folder,
        String subject,
        List<String> from,
        List<String> replyTo,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String sentAt,
        String receivedAt,
        String messageId,
        List<String> flags,
        String contentType,
        int size,
        String excerpt,
        String htmlFile,
        String emlFile,
        List<AttachmentMetadata> attachments) {
}
