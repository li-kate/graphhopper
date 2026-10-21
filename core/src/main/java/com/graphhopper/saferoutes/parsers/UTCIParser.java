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

    public UTCIParser(DecimalEncodedValue utciRawEnc, DecimalEncodedValue utciNormalizedEnc) {
        this.utciRawEnc = utciRawEnc;
        this.utciNormalizedEnc = utciNormalizedEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String utciTag = way.getTag("utci");
        double rawUTCI = UtciConstants.DEFAULT_UTCI;

        if (utciTag != null) {
            try {
                rawUTCI = Double.parseDouble(utciTag);
            } catch (NumberFormatException e) {
                logger.warn("Invalid UTCI value: {}. Using default {}", utciTag, UtciConstants.DEFAULT_UTCI);
            }
        }

        // Normalize to 0–1 range
        double normalized = UtciConstants.normalize(rawUTCI);

//        logger.info("Edge ID {} UTCI tag: {}, raw: {}, normalized: {}", edgeId, utciTag, rawUTCI, normalized);

        // Store into edge
        utciRawEnc.setDecimal(false, edgeId, edgeIntAccess, rawUTCI);           // renamed key: "utci"
        utciNormalizedEnc.setDecimal(false, edgeId, edgeIntAccess, normalized); // renamed key: "utci_normalized"
    }
}
