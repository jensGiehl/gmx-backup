package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.config.BackupProperties;
import de.agiehl.gmxbackup.domain.BackupCatalog;
import de.agiehl.gmxbackup.domain.EmailMetadata;
import de.agiehl.gmxbackup.util.ArchivePaths;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

@Service
public class BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);

    private final BackupProperties properties;
    private final FolderFilter folderFilter;
    private final MailArchiveWriter mailArchiveWriter;
    private final ArchiveIndexWriter indexWriter;

    public BackupService(
            BackupProperties properties,
            FolderFilter folderFilter,
            MailArchiveWriter mailArchiveWriter,
            ArchiveIndexWriter indexWriter) {
        this.properties = properties;
        this.folderFilter = folderFilter;
        this.mailArchiveWriter = mailArchiveWriter;
        this.indexWriter = indexWriter;
    }

    public void createBackup() throws Exception {
        var outputDirectory = properties.outputDirectory().toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);
        var session = Session.getInstance(mailProperties());
        Store store = null;
        try {
            store = session.getStore("imaps");
            LOGGER.info("Verbinde mit {}:{} als {}", properties.gmx().host(), properties.gmx().port(), properties.gmx().email());
            store.connect(properties.gmx().host(), properties.gmx().port(), properties.gmx().email(), properties.gmx().password());
            var folders = readableFolders(store);
            LOGGER.info("{} E-Mail-Ordner werden gesichert", folders.size());
            var emails = new ArrayList<EmailMetadata>();
            for (var folder : folders) {
                backupFolder(folder, outputDirectory, emails);
            }
            var folderNames = folders.stream().map(Folder::getFullName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            var catalog = new BackupCatalog(
                    properties.gmx().email(),
                    Instant.now().toString(),
                    emails.size(),
                    folderNames,
                    List.copyOf(emails));
            indexWriter.write(catalog, outputDirectory);
            LOGGER.info("Backup abgeschlossen: {} E-Mails in {}", emails.size(), outputDirectory);
            LOGGER.info("Übersicht: {}", outputDirectory.resolve("index.html"));
        } finally {
            if (store != null && store.isConnected()) {
                store.close();
            }
        }
    }

    private List<Folder> readableFolders(Store store) throws Exception {
        var folders = Arrays.stream(store.getDefaultFolder().list("*"))
                .filter(folder -> {
                    try {
                        return (folder.getType() & Folder.HOLDS_MESSAGES) != 0;
                    } catch (Exception exception) {
                        LOGGER.warn("Ordner {} kann nicht geprüft werden: {}", folder.getFullName(), exception.getMessage());
                        return false;
                    }
                })
                .filter(folder -> folderFilter.shouldInclude(folder.getFullName(), attributes(folder)))
                .sorted(Comparator.comparing(Folder::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return List.copyOf(folders);
    }

    private void backupFolder(Folder folder, Path outputDirectory, List<EmailMetadata> emails) throws Exception {
        try {
            folder.open(Folder.READ_ONLY);
            var count = folder.getMessageCount();
            LOGGER.info("Sichere Ordner '{}' mit {} E-Mails", folder.getFullName(), count);
            var folderDirectory = ArchivePaths.folderPath(outputDirectory.resolve("mail"), folder.getFullName(), folder.getSeparator());
            for (var start = 1; start <= count; start += properties.batchSize()) {
                var end = Math.min(start + properties.batchSize() - 1, count);
                var messages = folder.getMessages(start, end);
                prefetch(folder, messages);
                for (var message : messages) {
                    if (!message.isExpunged()) {
                        var uid = folder instanceof UIDFolder uidFolder ? uidFolder.getUID(message) : message.getMessageNumber();
                        emails.add(mailArchiveWriter.write(message, uid, folder.getFullName(), folderDirectory, outputDirectory));
                    }
                }
                LOGGER.info("Ordner '{}': {}/{} E-Mails gesichert", folder.getFullName(), end, count);
            }
        } finally {
            if (folder.isOpen()) {
                folder.close(false);
            }
        }
    }

    private void prefetch(Folder folder, Message[] messages) throws Exception {
        var profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(FetchProfile.Item.CONTENT_INFO);
        if (folder instanceof UIDFolder) {
            profile.add(UIDFolder.FetchProfileItem.UID);
        }
        folder.fetch(messages, profile);
    }

    private String[] attributes(Folder folder) {
        try {
            return folder instanceof IMAPFolder imapFolder ? imapFolder.getAttributes() : new String[0];
        } catch (Exception exception) {
            LOGGER.debug("IMAP-Attribute von '{}' konnten nicht gelesen werden", folder.getFullName(), exception);
            return new String[0];
        }
    }

    private Properties mailProperties() {
        var mail = new Properties();
        mail.setProperty("mail.imaps.ssl.enable", "true");
        mail.setProperty("mail.imaps.peek", "true");
        mail.setProperty("mail.imaps.connectiontimeout", String.valueOf(properties.connectionTimeout().toMillis()));
        mail.setProperty("mail.imaps.timeout", String.valueOf(properties.readTimeout().toMillis()));
        mail.setProperty("mail.imaps.writetimeout", String.valueOf(properties.readTimeout().toMillis()));
        mail.setProperty("mail.mime.address.strict", "false");
        return mail;
    }
}
