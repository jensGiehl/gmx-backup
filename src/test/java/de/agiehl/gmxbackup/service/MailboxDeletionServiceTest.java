package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.config.BackupProperties;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailboxDeletionServiceTest {

    @Test
    void deletesEveryMessageAndProcessesTrashLast() throws Exception {
        var properties = new BackupProperties(
                new BackupProperties.Gmx("mail@gmx.de", "secret", "imap.gmx.net", 993),
                Path.of("backup"),
                100,
                Duration.ofSeconds(30),
                Duration.ofSeconds(60),
                true,
                false);
        var storeFactory = mock(ImapStoreFactory.class);
        var store = mock(Store.class);
        var root = mock(Folder.class);
        var inbox = folder("INBOX");
        var trash = folder("Gelöscht");
        when(storeFactory.connect()).thenReturn(store);
        when(store.getDefaultFolder()).thenReturn(root);
        when(root.list("*")).thenReturn(new Folder[]{trash, inbox});
        when(store.isConnected()).thenReturn(true);

        new MailboxDeletionService(properties, new FolderFilter(), storeFactory).deleteAllEmails();

        var order = inOrder(inbox, trash);
        order.verify(inbox).open(Folder.READ_WRITE);
        order.verify(trash).open(Folder.READ_WRITE);
        var flags = ArgumentCaptor.forClass(Flags.class);
        verify(inbox).setFlags(any(Message[].class), flags.capture(), eq(true));
        assertThat(flags.getValue().contains(Flags.Flag.DELETED)).isTrue();
        verify(trash).setFlags(any(Message[].class), any(Flags.class), eq(true));
        verify(inbox).expunge();
        verify(trash).expunge();
        verify(store).close();
    }

    private Folder folder(String name) throws Exception {
        var folder = mock(Folder.class);
        var message = mock(Message.class);
        when(folder.getFullName()).thenReturn(name);
        when(folder.getType()).thenReturn(Folder.HOLDS_MESSAGES);
        when(folder.getMode()).thenReturn(Folder.READ_WRITE);
        when(folder.getMessageCount()).thenReturn(1);
        when(folder.getMessages(1, 1)).thenReturn(new Message[]{message});
        when(folder.expunge()).thenReturn(new Message[]{message});
        when(folder.isOpen()).thenReturn(true);
        return folder;
    }
}
