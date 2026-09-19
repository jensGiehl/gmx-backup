package de.agiehl.gmxbackup.domain;

public record AttachmentMetadata(
        String filename,
        String path,
        String contentType,
        long size,
        boolean inline) {
}
