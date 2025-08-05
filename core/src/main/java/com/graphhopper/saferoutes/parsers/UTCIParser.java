package com.graphhopper.saferoutes.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UTCIParser implements TagParser {

    private static final Logger logger = LoggerFactory.getLogger(UTCIParser.class);

    private final DecimalEncodedValue utciRawEnc;         // utci
    private final DecimalEncodedValue utciNormalizedEnc;  // utci_normalized
    private static final double DEFAULT_UTCI = 25.0; // Moderate condition

    // Min and max used for normalization
    private static final double UTCI_MIN = 0.0;
    private static final double UTCI_MAX = 45.0;

    public UTCIParser(DecimalEncodedValue utciRawEnc, DecimalEncodedValue utciNormalizedEnc) {
        this.utciRawEnc = utciRawEnc;
        this.utciNormalizedEnc = utciNormalizedEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String utciTag = way.getTag("utci");
        double rawUTCI = DEFAULT_UTCI;

        if (utciTag != null) {
            try {
                rawUTCI = Double.parseDouble(utciTag);
            } catch (NumberFormatException e) {
                logger.warn("Invalid UTCI value: {}. Using default {}", utciTag, DEFAULT_UTCI);
            }
        }

        // Normalize to 0–1 range
        double normalized = Math.max(0, Math.min(1, (rawUTCI - UTCI_MIN) / (UTCI_MAX - UTCI_MIN)));

//        logger.info("Edge ID {} UTCI tag: {}, raw: {}, normalized: {}", edgeId, utciTag, rawUTCI, normalized);

        // Store into edge
        utciRawEnc.setDecimal(false, edgeId, edgeIntAccess, rawUTCI);           // renamed key: "utci"
        utciNormalizedEnc.setDecimal(false, edgeId, edgeIntAccess, normalized); // renamed key: "utci_normalized"
    }
}
