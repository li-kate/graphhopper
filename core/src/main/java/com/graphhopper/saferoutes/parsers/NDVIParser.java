package com.graphhopper.saferoutes.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NDVIParser implements TagParser {

    private static final Logger logger = LoggerFactory.getLogger(NDVIParser.class);

    private final DecimalEncodedValue ndviRawEnc;         // evi
    private final DecimalEncodedValue ndviNormalizedEnc;  // evi_normalized
    private static final double DEFAULT_NDVI = 0.0;

    public NDVIParser(DecimalEncodedValue ndviRawEnc, DecimalEncodedValue ndviNormalizedEnc) {
        this.ndviRawEnc = ndviRawEnc;
        this.ndviNormalizedEnc = ndviNormalizedEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String ndviTag = way.getTag("ndvi");
        double rawNDVI = DEFAULT_NDVI;

        if (ndviTag != null) {
            try {
                rawNDVI = Double.parseDouble(ndviTag);
            } catch (NumberFormatException e) {
                logger.warn("Invalid NDVI value: {}. Using default {}", ndviTag, DEFAULT_NDVI);
            }
        }

        // Normalize from [-1, 1] to [0, 1]
        double normalized = Math.max(0, Math.min(1, (rawNDVI + 1) / 2));

        // logger.info("Edge ID {} NDVI tag: {}, raw: {}, normalized: {}", edgeId, ndviTag, rawNDVI, normalized);

        // Store into edge
        ndviRawEnc.setDecimal(false, edgeId, edgeIntAccess, rawNDVI);           // renamed key: "ndvi"
        ndviNormalizedEnc.setDecimal(false, edgeId, edgeIntAccess, normalized); // renamed key: "ndvi_normalized"
    }
}
