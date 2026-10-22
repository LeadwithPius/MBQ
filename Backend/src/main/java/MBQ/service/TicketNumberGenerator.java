package com.MBQ.service;

import java.util.Locale;

/**
 * Builds human-friendly ticket numbers like "OPD-007" or "EME-012".
 * The prefix comes from the department name; the number restarts at 001 every day.
 */
public final class TicketNumberGenerator {

    private TicketNumberGenerator() {
    }

    /** "OPD" -> "OPD", "Emergency" -> "EME", "Maternity" -> "MAT". */
    public static String prefixFor(String departmentName) {
        String lettersOnly = departmentName == null
                ? ""
                : departmentName.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (lettersOnly.isEmpty()) {
            return "DEP";
        }
        return lettersOnly.substring(0, Math.min(3, lettersOnly.length()));
    }

    /** prefix + "-" + at least 3 digits, e.g. ("OPD", 7) -> "OPD-007", ("OPD", 1234) -> "OPD-1234". */
    public static String format(String prefix, long sequence) {
        return String.format(Locale.ROOT, "%s-%03d", prefix, sequence);
    }
}
