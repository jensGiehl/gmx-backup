package de.agiehl.gmxbackup.domain;

import java.util.List;

public record BackupCatalog(
        String account,
        String createdAt,
        int emailCount,
        List<String> folders,
        List<EmailMetadata> emails) {
}
