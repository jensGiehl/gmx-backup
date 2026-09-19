package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.config.BackupProperties;
import de.agiehl.gmxbackup.domain.BackupCatalog;
import de.agiehl.gmxbackup.domain.EmailMetadata;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deletesOnlySuccessfullyBackedUpMessagesAndContinuesAfterFailure() throws Exception {
        var properties = new BackupProperties(
                new BackupProperties.Gmx("mail@gmx.de", "secret", "imap.gmx.net", 993),
                temporaryDirectory,
                100,
                Duration.ofSeconds(30),
                Duration.ofSeconds(60),
                false,
                true);
        var storeFactory = mock(ImapStoreFactory.class);
        var writer = mock(MailArchiveWriter.class);
        var indexWriter = mock(ArchiveIndexWriter.class);
        var store = mock(Store.class);
        var root = mock(Folder.class);
        var folder = mock(Folder.class);
        var failedMessage = mock(Message.class);
        var successfulMessage = mock(Message.class);

        when(storeFactory.connect()).thenReturn(store);
        when(store.getDefaultFolder()).thenReturn(root);
        when(root.list("*")).thenReturn(new Folder[]{folder});
        when(folder.getType()).thenReturn(Folder.HOLDS_MESSAGES);
        when(folder.getFullName()).thenReturn("INBOX");
        when(folder.getSeparator()).thenReturn('/');
        when(folder.getMode()).thenReturn(Folder.READ_WRITE);
        when(folder.getMessageCount()).thenReturn(2);
        when(folder.getMessages(1, 2)).thenReturn(new Message[]{failedMessage, successfulMessage});
        when(folder.expunge()).thenReturn(new Message[]{successfulMessage});
        when(folder.isOpen()).thenReturn(true);
        when(failedMessage.isExpunged()).thenReturn(false);
        when(successfulMessage.isExpunged()).thenReturn(false);
        when(failedMessage.getMessageNumber()).thenReturn(1);
        when(successfulMessage.getMessageNumber()).thenReturn(2);
        when(store.isConnected()).thenReturn(true);
        when(writer.write(eq(failedMessage), anyLong(), anyString(), any(Path.class), any(Path.class)))
                .thenThrow(new IOException("Defekte Nachricht"));
        when(writer.write(eq(successfulMessage), anyLong(), anyString(), any(Path.class), any(Path.class)))
                .thenReturn(metadata());

        new BackupService(properties, new FolderFilter(), writer, indexWriter, storeFactory).createBackup();

        verify(failedMessage, never()).setFlag(Flags.Flag.DELETED, true);
        verify(successfulMessage).setFlag(Flags.Flag.DELETED, true);
        verify(folder).expunge();
        var catalog = ArgumentCaptor.forClass(BackupCatalog.class);
        verify(indexWriter).write(catalog.capture(), eq(temporaryDirectory.toAbsolutePath().normalize()));
        assertThat(catalog.getValue().emailCount()).isEqualTo(1);
        verify(store).close();
    }

    private EmailMetadata metadata() {
        return new EmailMetadata(
                "INBOX:2",
                2,
                "INBOX",
                "Erfolgreich",
                List.of("sender@example.com"),
                List.of(),
                List.of("mail@gmx.de"),
                List.of(),
                List.of(),
                "2026-09-19T10:00:00Z",
                "2026-09-19T10:00:00Z",
                "<message@example.com>",
                List.of(),
                "text/plain",
                100,
                "Inhalt",
                "mail/INBOX/mail.html",
                "mail/INBOX/mail.eml",
                List.of());
    }
}
