package com.graphhopper.saferoutes.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic parser for hourly UTCI fields.
 * Reads the OSM tag matching the EV key (e.g. "utci_14") and normalizes it.
 * One instance is created per hour — no copy-paste needed.
 */
public class UtciHourlyParser implements TagParser {

    private static final Logger logger = LoggerFactory.getLogger(UtciHourlyParser.class);

    private final String tagName;          // e.g. "utci_14" (same as EV key)
    private final DecimalEncodedValue rawEnc;
    private final DecimalEncodedValue normalizedEnc;

    /**
     * @param tagName       The OSM tag / EV key name, e.g. "utci_14"
     * @param rawEnc        Encoded value for storing the raw UTCI
     * @param normalizedEnc Encoded value for storing the normalized (0-1) UTCI
     */
    public UtciHourlyParser(String tagName, DecimalEncodedValue rawEnc, DecimalEncodedValue normalizedEnc) {
        this.tagName = tagName;
        this.rawEnc = rawEnc;
        this.normalizedEnc = normalizedEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String tagValue = way.getTag(tagName);
        double rawUtci = UtciConstants.DEFAULT_UTCI;

        if (tagValue != null) {
            try {
                rawUtci = Double.parseDouble(tagValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} value: {}. Using default {}", tagName, tagValue, UtciConstants.DEFAULT_UTCI);
            }
        }

        double normalized = UtciConstants.normalize(rawUtci);

        rawEnc.setDecimal(false, edgeId, edgeIntAccess, rawUtci);
        normalizedEnc.setDecimal(false, edgeId, edgeIntAccess, normalized);
    }
}