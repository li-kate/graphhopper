package com.graphhopper.saferoutes.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EVIParser implements TagParser {

    private static final Logger logger = LoggerFactory.getLogger(EVIParser.class);

    private final DecimalEncodedValue eviRawEnc;         // evi
    private final DecimalEncodedValue eviNormalizedEnc;  // evi_normalized
    private static final double DEFAULT_EVI = 0.0; // Moderate condition

    public EVIParser(DecimalEncodedValue eviRawEnc, DecimalEncodedValue eviNormalizedEnc) {
        this.eviRawEnc = eviRawEnc;
        this.eviNormalizedEnc = eviNormalizedEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String eviTag = way.getTag("evi");
        double rawEVI = DEFAULT_EVI;

        if (eviTag != null) {
            try {
                rawEVI = Double.parseDouble(eviTag);
            } catch (NumberFormatException e) {
                logger.warn("Invalid EVI value: {}. Using default {}", eviTag, DEFAULT_EVI);
            }
        }

        // Normalize from [-1, 1] to [0, 1]
        double normalized = Math.max(0, Math.min(1, (rawEVI + 1) / 2));

//        logger.info("Edge ID {} EVI tag: {}, raw: {}, normalized: {}", edgeId, eviTag, rawEVI, normalized);

        // Store into edge
        eviRawEnc.setDecimal(false, edgeId, edgeIntAccess, rawEVI);           // renamed key: "evi"
        eviNormalizedEnc.setDecimal(false, edgeId, edgeIntAccess, normalized); // renamed key: "evi_normalized"
    }
}
