package com.hospital.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns free text such as "  Blurred Vision ,EYE pain; " into a clean list of symptom phrases:
 *   - trims spaces and converts to lower case
 *   - splits on commas, semicolons, "|" and new lines
 *   - removes unusual characters and repeated spaces
 *   - removes duplicates but keeps the order
 *
 * Both the historical comparison and the specialist matcher use this same normalization.
 */
public final class SymptomUtils {

    public static final int MAX_PHRASE_LENGTH = 60;
    public static final int MAX_PHRASES = 20;

    private SymptomUtils() {
    }

    /** Splits and normalizes a text into symptom phrases (never null, no duplicates). */
    public static List<String> toList(String raw) {
        Set<String> phrases = new LinkedHashSet<>();
        if (raw == null) {
            return new ArrayList<>();
        }
        for (String part : raw.split("[,;|\\r\\n]+")) {
            String phrase = normalizeOne(part);
            if (!phrase.isEmpty()) {
                phrases.add(phrase);
            }
        }
        return new ArrayList<>(phrases);
    }

    public static Set<String> toSet(String raw) {
        return new LinkedHashSet<>(toList(raw));
    }

    /** Normalizes a single phrase. Returns "" when nothing useful is left. */
    public static String normalizeOne(String phrase) {
        if (phrase == null) {
            return "";
        }
        String s = phrase.toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^\\p{L}\\p{N} '\\-/]", " "); // keep letters, digits, space, ' - /
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > MAX_PHRASE_LENGTH) {
            s = s.substring(0, MAX_PHRASE_LENGTH).trim();
        }
        return s;
    }

    /** Joins phrases back into the text stored in the database: "a, b, c". */
    public static String join(Iterable<String> phrases) {
        return String.join(", ", phrases);
    }
}
