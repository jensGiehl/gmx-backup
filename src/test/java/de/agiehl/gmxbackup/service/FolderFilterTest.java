package de.agiehl.gmxbackup.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FolderFilterTest {

    private final FolderFilter filter = new FolderFilter();

    @Test
    void excludesTrashAndSpamByName() {
        assertThat(filter.shouldInclude("Gelöscht", new String[0])).isFalse();
        assertThat(filter.shouldInclude("Postfach/Spamverdacht", new String[0])).isFalse();
        assertThat(filter.shouldInclude("INBOX", new String[0])).isTrue();
        assertThat(filter.shouldInclude("Gesendet", new String[0])).isTrue();
    }

    @Test
    void excludesProviderSystemAttributes() {
        assertThat(filter.shouldInclude("Bin", new String[]{"\\Trash"})).isFalse();
        assertThat(filter.shouldInclude("Unwanted", new String[]{"\\Junk"})).isFalse();
    }

    @Test
    void identifiesTrashSeparatelyForDeletionOrdering() {
        assertThat(filter.isTrash("Gelöscht", new String[0])).isTrue();
        assertThat(filter.isTrash("Bin", new String[]{"\\Trash"})).isTrue();
        assertThat(filter.isTrash("Spamverdacht", new String[0])).isFalse();
    }
}
