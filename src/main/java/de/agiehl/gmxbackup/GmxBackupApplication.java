package de.agiehl.gmxbackup;

import de.agiehl.gmxbackup.config.BackupProperties;
import de.agiehl.gmxbackup.service.BackupService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BackupProperties.class)
public class GmxBackupApplication implements ApplicationRunner {

    private final BackupProperties properties;
    private final BackupService backupService;

    public GmxBackupApplication(BackupProperties properties, BackupService backupService) {
        this.properties = properties;
        this.backupService = backupService;
    }

    public static void main(String[] args) {
        SpringApplication.run(GmxBackupApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        properties.validate();
        backupService.createBackup();
    }
}
