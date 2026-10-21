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

    public UTCI_MP_Parser(DecimalEncodedValue utciRawMPEnc, DecimalEncodedValue utciNormalizedMPEnc) {
        this.utciRawMPEnc = utciRawMPEnc;
        this.utciNormalizedMPEnc = utciNormalizedMPEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String utci_MP_Tag = way.getTag("utci_mp");
        double rawUTCI = UtciConstants.DEFAULT_UTCI;

        if (utci_MP_Tag != null) {
            try {
                rawUTCI = Double.parseDouble(utci_MP_Tag);
            } catch (NumberFormatException e) {
                logger.warn("Invalid UTCI value: {}. Using default {}", utci_MP_Tag, UtciConstants.DEFAULT_UTCI);
            }
        }

        // Normalize to 0–1 range
        double normalized = UtciConstants.normalize(rawUTCI);

//        logger.info("Edge ID {} UTCI tag: {}, raw: {}, normalized: {}", edgeId, utciTag, rawUTCI, normalized);

        // Store into edge
        utciRawMPEnc.setDecimal(false, edgeId, edgeIntAccess, rawUTCI);           // renamed key: "utci"
        utciNormalizedMPEnc.setDecimal(false, edgeId, edgeIntAccess, normalized); // renamed key: "utci_normalized"
    }
}
