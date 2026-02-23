package com.graphhopper.saferoutes.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UTCI_AP_Parser implements TagParser {

    private static final Logger logger = LoggerFactory.getLogger(UTCI_AP_Parser.class);

    private final DecimalEncodedValue utciRawAPEnc;         // utci
    private final DecimalEncodedValue utciNormalizedAPEnc;  // utci_normalized
    private static final double DEFAULT_UTCI = 25.0; // Moderate condition

    // Min and max used for normalization
    private static final double UTCI_MIN = 0.0;
    private static final double UTCI_MAX = 45.0;

    public UTCI_AP_Parser(DecimalEncodedValue utciRawAPEnc, DecimalEncodedValue utciNormalizedAPEnc) {
        this.utciRawAPEnc = utciRawAPEnc;
        this.utciNormalizedAPEnc = utciNormalizedAPEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String utci_AP_Tag = way.getTag("utci_ap");
        double rawUTCI = DEFAULT_UTCI;

        if (utci_AP_Tag != null) {
            try {
                rawUTCI = Double.parseDouble(utci_AP_Tag);
            } catch (NumberFormatException e) {
                logger.warn("Invalid UTCI value: {}. Using default {}", utci_AP_Tag, DEFAULT_UTCI);
            }
        }

        // Normalize to 0–1 range
        double normalized = Math.max(0, Math.min(1, (rawUTCI - UTCI_MIN) / (UTCI_MAX - UTCI_MIN)));

//        logger.info("Edge ID {} UTCI tag: {}, raw: {}, normalized: {}", edgeId, utciTag, rawUTCI, normalized);

        // Store into edge
        utciRawAPEnc.setDecimal(false, edgeId, edgeIntAccess, rawUTCI);           // renamed key: "utci"
        utciNormalizedAPEnc.setDecimal(false, edgeId, edgeIntAccess, normalized); // renamed key: "utci_normalized"
    }
}
