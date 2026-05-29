package com.graphhopper.saferoutes.ev;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;

/**
 * Generic encoded value for hourly UTCI fields (UTCI_07 through UTCI_20).
 * Instead of one class per hour, this single class handles all hours.
 *
 * Usage:
 *   UtciHourly.rawKey(14)        -> "UTCI_14"
 *   UtciHourly.normalizedKey(14) -> "UTCI_14_normalized"
 *   UtciHourly.createRaw(14)     -> DecimalEncodedValue for UTCI_14
 */
public class UtciHourly {

    public static final int MIN_HOUR = 7;
    public static final int MAX_HOUR = 20;

    /** e.g. "utci_07", "utci_14" — lowercase for GraphHopper EV names */
    public static String rawKey(int hour) {
        return String.format("utci_%02d", hour);
    }

    /** e.g. "utci_07_normalized", "utci_14_normalized" */
    public static String normalizedKey(int hour) {
        return rawKey(hour) + "_normalized";
    }

    public static DecimalEncodedValue createRaw(int hour) {
        return new DecimalEncodedValueImpl(rawKey(hour), 16, 4, false);
    }

    public static DecimalEncodedValue createNormalized(int hour) {
        return new DecimalEncodedValueImpl(normalizedKey(hour), 14, 0, 0.0001,
                false, false, false);
    }

    /** Check if a given encoded-value name matches any hourly UTCI key */
    public static boolean isUtciHourlyKey(String name) {
        if (name == null) return false;
        // Match "utci_XX" or "utci_XX_normalized"
        String base = name.replace("_normalized", "");
        if (!base.startsWith("utci_") || base.length() != 7) return false;
        try {
            int hour = Integer.parseInt(base.substring(5));
            return hour >= MIN_HOUR && hour <= MAX_HOUR;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Extract the hour from a key like "utci_14" or "utci_14_normalized" */
    public static int parseHour(String name) {
        String base = name.replace("_normalized", "");
        return Integer.parseInt(base.substring(5));
    }
}