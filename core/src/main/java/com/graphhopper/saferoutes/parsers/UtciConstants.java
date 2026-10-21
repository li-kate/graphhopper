package com.graphhopper.saferoutes.parsers;

/**
 * Shared configuration for all UTCI parsers.
 * Change the default and normalization range here once — every UTCI parser
 * (UTCIParser, UTCI_AP_Parser, UTCI_MP_Parser, UTCI_EP_Parser, UtciHourlyParser)
 * picks up the new values automatically.
 */
public final class UtciConstants {

    /** Value used when an edge has no UTCI tag or the tag is unparseable. */
    public static final double DEFAULT_UTCI = 25.0; // Moderate condition

    /** Lower bound used for normalizing raw UTCI into the 0–1 range. */
    public static final double UTCI_MIN = 0.0;

    /** Upper bound used for normalizing raw UTCI into the 0–1 range. */
    public static final double UTCI_MAX = 60.0;

    private UtciConstants() {
        // utility class — no instances
    }

    /** Normalize a raw UTCI value into the clamped 0–1 range. */
    public static double normalize(double rawUtci) {
        return Math.max(0, Math.min(1, (rawUtci - UTCI_MIN) / (UTCI_MAX - UTCI_MIN)));
    }
}
