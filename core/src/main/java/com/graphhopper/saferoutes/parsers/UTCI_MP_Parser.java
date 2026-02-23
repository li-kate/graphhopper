package com.graphhopper.saferoutes.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UTCI_MP_Parser implements TagParser {

    private static final Logger logger = LoggerFactory.getLogger(UTCI_MP_Parser.class);

    private final DecimalEncodedValue utciRawMPEnc;         // utci
    private final DecimalEncodedValue utciNormalizedMPEnc;  // utci_normalized
    private static final double DEFAULT_UTCI = 25.0; // Moderate condition

    // Min and max used for normalization
    private static final double UTCI_MIN = 0.0;
    private static final double UTCI_MAX = 45.0;

    public UTCI_MP_Parser(DecimalEncodedValue utciRawMPEnc, DecimalEncodedValue utciNormalizedMPEnc) {
        this.utciRawMPEnc = utciRawMPEnc;
        this.utciNormalizedMPEnc = utciNormalizedMPEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String utci_MP_Tag = way.getTag("utci_mp");
        double rawUTCI = DEFAULT_UTCI;

        if (utci_MP_Tag != null) {
            try {
                rawUTCI = Double.parseDouble(utci_MP_Tag);
            } catch (NumberFormatException e) {
                logger.warn("Invalid UTCI value: {}. Using default {}", utci_MP_Tag, DEFAULT_UTCI);
            }
        }

        // Normalize to 0–1 range
        double normalized = Math.max(0, Math.min(1, (rawUTCI - UTCI_MIN) / (UTCI_MAX - UTCI_MIN)));

//        logger.info("Edge ID {} UTCI tag: {}, raw: {}, normalized: {}", edgeId, utciTag, rawUTCI, normalized);

        // Store into edge
        utciRawMPEnc.setDecimal(false, edgeId, edgeIntAccess, rawUTCI);           // renamed key: "utci"
        utciNormalizedMPEnc.setDecimal(false, edgeId, edgeIntAccess, normalized); // renamed key: "utci_normalized"
    }
}
