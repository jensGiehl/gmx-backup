package de.agiehl.gmxbackup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("backup")
public record BackupProperties(
        Gmx gmx,
        @DefaultValue("backup") Path outputDirectory,
        @DefaultValue("100") int batchSize,
        @DefaultValue("30s") Duration connectionTimeout,
        @DefaultValue("60s") Duration readTimeout,
        @DefaultValue("false") boolean deleteAll,
        @DefaultValue("false") boolean deleteAfterBackup) {

    public BackupProperties {
        gmx = gmx == null ? new Gmx(null, null, "imap.gmx.net", 993) : gmx;
    }

    public void validate() {
        if (!StringUtils.hasText(gmx.email())) {
            throw new IllegalArgumentException("Die GMX E-Mail-Adresse fehlt: --backup.gmx.email=...");
        }
        if (!StringUtils.hasText(gmx.password())) {
            throw new IllegalArgumentException("Das GMX Passwort fehlt: --backup.gmx.password=...");
        }
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("backup.batch-size muss zwischen 1 und 1000 liegen");
        }
        if (deleteAll && deleteAfterBackup) {
            throw new IllegalArgumentException("backup.delete-all und backup.delete-after-backup dürfen nicht gleichzeitig aktiviert sein");
        }
    }

    public record Gmx(
            String email,
            String password,
            @DefaultValue("imap.gmx.net") String host,
            @DefaultValue("993") int port) {
    }
}
