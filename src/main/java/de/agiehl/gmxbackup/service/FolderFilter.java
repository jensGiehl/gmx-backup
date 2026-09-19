package de.agiehl.gmxbackup.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class FolderFilter {

    private static final Set<String> EXCLUDED_NAMES = Set.of(
            "spam", "junk", "spamverdacht", "trash", "deleted", "deleted messages", "geloscht", "papierkorb");

    public boolean shouldInclude(String fullName, String[] attributes) {
        var systemFolder = Arrays.stream(attributes == null ? new String[0] : attributes)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals("\\trash") || value.equals("\\junk"));
        if (systemFolder) {
            return false;
        }
        return Arrays.stream(fullName.split("[/\\\\]"))
                .map(this::normalize)
                .noneMatch(EXCLUDED_NAMES::contains);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip();
    }
}
