package de.agiehl.gmxbackup.service;

import de.agiehl.gmxbackup.config.BackupProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class ImapStoreFactory {

    private final BackupProperties properties;

    public ImapStoreFactory(BackupProperties properties) {
        this.properties = properties;
    }

    public Store connect() throws MessagingException {
        var session = Session.getInstance(mailProperties());
        var store = session.getStore("imaps");
        store.connect(properties.gmx().host(), properties.gmx().port(), properties.gmx().email(), properties.gmx().password());
        return store;
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
