package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.config.BackupProperties;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class MailboxDeletionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxDeletionService.class);

    private final BackupProperties properties;
    private final FolderFilter folderFilter;
    private final ImapStoreFactory storeFactory;

    public MailboxDeletionService(
            BackupProperties properties,
            FolderFilter folderFilter,
            ImapStoreFactory storeFactory) {
        this.properties = properties;
        this.folderFilter = folderFilter;
        this.storeFactory = storeFactory;
    }

    public void deleteAllEmails() throws Exception {
        Store store = null;
        try {
            LOGGER.warn("LÖSCHMODUS AKTIV: Es wird kein Backup erstellt");
            LOGGER.warn("Verbinde mit {}:{} als {}", properties.gmx().host(), properties.gmx().port(), properties.gmx().email());
            store = storeFactory.connect();
            var folders = messageFolders(store);
            LOGGER.warn("Alle E-Mails werden unwiderruflich aus {} Ordnern gelöscht: {}",
                    folders.size(),
                    folders.stream().map(Folder::getFullName).toList());
            var deletedCount = 0L;
            for (var folder : folders) {
                deletedCount += deleteFolderContents(folder);
            }
            LOGGER.warn("Löschvorgang abgeschlossen: {} E-Mails wurden endgültig entfernt", deletedCount);
        } finally {
            if (store != null && store.isConnected()) {
                store.close();
            }
        }
    }

    private List<Folder> messageFolders(Store store) throws Exception {
        return Arrays.stream(store.getDefaultFolder().list("*"))
                .filter(this::holdsMessages)
                .sorted(Comparator
                        .comparingInt(this::deletionPriority)
                        .thenComparing(Folder::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private long deleteFolderContents(Folder folder) throws Exception {
        var expungeOnClose = false;
        try {
            folder.open(Folder.READ_WRITE);
            if (folder.getMode() != Folder.READ_WRITE) {
                throw new IllegalStateException("Ordner kann nicht schreibend geöffnet werden: " + folder.getFullName());
            }
            var count = folder.getMessageCount();
            LOGGER.warn("Lösche {} E-Mails aus Ordner '{}'", count, folder.getFullName());
            for (var start = 1; start <= count; start += properties.batchSize()) {
                var end = Math.min(start + properties.batchSize() - 1, count);
                Message[] messages = folder.getMessages(start, end);
                folder.setFlags(messages, new Flags(Flags.Flag.DELETED), true);
                expungeOnClose = true;
                LOGGER.info("Ordner '{}': {}/{} E-Mails zum Löschen markiert", folder.getFullName(), end, count);
            }
            var expunged = folder.expunge().length;
            expungeOnClose = false;
            LOGGER.warn("Ordner '{}': {} E-Mails endgültig entfernt", folder.getFullName(), expunged);
            return expunged;
        } finally {
            if (folder.isOpen()) {
                folder.close(expungeOnClose);
            }
        }
    }

    private boolean holdsMessages(Folder folder) {
        try {
            return (folder.getType() & Folder.HOLDS_MESSAGES) != 0;
        } catch (Exception exception) {
            throw new IllegalStateException("Ordner kann nicht geprüft werden: " + folder.getFullName(), exception);
        }
    }

    private int deletionPriority(Folder folder) {
        return folderFilter.isTrash(folder.getFullName(), attributes(folder)) ? 1 : 0;
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
