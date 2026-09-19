package de.agiehl.gmxbackup.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class FolderFilter {

    public boolean shouldInclude(String fullName, String[] attributes) {
        return !isTrash(fullName, attributes) && !isSpam(fullName, attributes);
    }

    public boolean isTrash(String fullName, String[] attributes) {
        return hasAttribute(attributes, "\\trash") || normalizedSegments(fullName)
                .anyMatch(Set.of("trash", "deleted", "deleted messages", "geloscht", "papierkorb")::contains);
    }

    public boolean isSpam(String fullName, String[] attributes) {
        return hasAttribute(attributes, "\\junk") || normalizedSegments(fullName)
                .anyMatch(Set.of("spam", "junk", "spamverdacht")::contains);
    }

    private boolean hasAttribute(String[] attributes, String expected) {
        return Arrays.stream(attributes == null ? new String[0] : attributes)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(expected::equals);
    }

    private java.util.stream.Stream<String> normalizedSegments(String fullName) {
        return Arrays.stream(fullName.split("[/\\\\]"))
                .map(this::normalize)
                .filter(value -> !value.isBlank());
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip();
    }
}
