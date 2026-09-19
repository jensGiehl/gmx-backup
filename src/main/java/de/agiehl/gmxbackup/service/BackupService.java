package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.config.BackupProperties;
import de.agiehl.gmxbackup.domain.BackupCatalog;
import de.agiehl.gmxbackup.domain.EmailMetadata;
import de.agiehl.gmxbackup.util.ArchivePaths;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
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

@Service
public class BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);

    private final BackupProperties properties;
    private final FolderFilter folderFilter;
    private final MailArchiveWriter mailArchiveWriter;
    private final ArchiveIndexWriter indexWriter;
    private final ImapStoreFactory storeFactory;

    public BackupService(
            BackupProperties properties,
            FolderFilter folderFilter,
            MailArchiveWriter mailArchiveWriter,
            ArchiveIndexWriter indexWriter,
            ImapStoreFactory storeFactory) {
        this.properties = properties;
        this.folderFilter = folderFilter;
        this.mailArchiveWriter = mailArchiveWriter;
        this.indexWriter = indexWriter;
        this.storeFactory = storeFactory;
    }

    public void createBackup() throws Exception {
        var outputDirectory = properties.outputDirectory().toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);
        Store store = null;
        try {
            LOGGER.info("Verbinde mit {}:{} als {}", properties.gmx().host(), properties.gmx().port(), properties.gmx().email());
            store = storeFactory.connect();
            var folders = readableFolders(store);
            LOGGER.info("{} E-Mail-Ordner werden gesichert", folders.size());
            var emails = new ArrayList<EmailMetadata>();
            var failedFolders = 0;
            var failedMessages = 0;
            for (var folder : folders) {
                try {
                    failedMessages += backupFolder(folder, outputDirectory, emails);
                } catch (Exception exception) {
                    failedFolders++;
                    LOGGER.error("Ordner '{}' konnte nicht vollständig verarbeitet werden und wird übersprungen: {}",
                            folder.getFullName(), exception.getMessage(), exception);
                }
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
            if (failedFolders > 0 || failedMessages > 0) {
                LOGGER.warn("Backup mit übersprungenen Elementen abgeschlossen: {} Ordner, {} E-Mails", failedFolders, failedMessages);
            }
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

    private int backupFolder(Folder folder, Path outputDirectory, List<EmailMetadata> emails) throws Exception {
        var failedMessages = 0;
        var expungeOnClose = false;
        try {
            var requestedMode = properties.deleteAfterBackup() ? Folder.READ_WRITE : Folder.READ_ONLY;
            folder.open(requestedMode);
            var canDelete = properties.deleteAfterBackup() && folder.getMode() == Folder.READ_WRITE;
            if (properties.deleteAfterBackup() && !canDelete) {
                LOGGER.warn("Ordner '{}' ist nur lesbar; E-Mails werden gesichert, aber nicht gelöscht", folder.getFullName());
            }
            var count = folder.getMessageCount();
            LOGGER.info("Sichere Ordner '{}' mit {} E-Mails", folder.getFullName(), count);
            var folderDirectory = ArchivePaths.folderPath(outputDirectory.resolve("mail"), folder.getFullName(), folder.getSeparator());
            for (var start = 1; start <= count; start += properties.batchSize()) {
                var end = Math.min(start + properties.batchSize() - 1, count);
                var messages = folder.getMessages(start, end);
                try {
                    prefetch(folder, messages);
                } catch (Exception exception) {
                    LOGGER.warn("Vorabladen in Ordner '{}' fehlgeschlagen; E-Mails werden einzeln abgerufen: {}",
                            folder.getFullName(), exception.getMessage());
                }
                for (var message : messages) {
                    if (!message.isExpunged()) {
                        try {
                            var uid = folder instanceof UIDFolder uidFolder ? uidFolder.getUID(message) : message.getMessageNumber();
                            var metadata = mailArchiveWriter.write(message, uid, folder.getFullName(), folderDirectory, outputDirectory);
                            emails.add(metadata);
                            if (canDelete) {
                                message.setFlag(jakarta.mail.Flags.Flag.DELETED, true);
                                expungeOnClose = true;
                                LOGGER.info("Gesicherte E-Mail '{}' zum Löschen markiert", metadata.subject());
                            }
                        } catch (Exception exception) {
                            failedMessages++;
                            LOGGER.error("E-Mail Nummer {} in Ordner '{}' wird nach einem Fehler übersprungen: {}",
                                    message.getMessageNumber(), folder.getFullName(), exception.getMessage(), exception);
                        }
                    }
                }
                LOGGER.info("Ordner '{}': {}/{} E-Mails gesichert", folder.getFullName(), end, count);
            }
            if (expungeOnClose) {
                var expunged = folder.expunge().length;
                expungeOnClose = false;
                LOGGER.warn("Ordner '{}': {} erfolgreich gesicherte E-Mails vom Server gelöscht", folder.getFullName(), expunged);
            }
            return failedMessages;
        } finally {
            if (folder.isOpen()) {
                folder.close(expungeOnClose);
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

}
