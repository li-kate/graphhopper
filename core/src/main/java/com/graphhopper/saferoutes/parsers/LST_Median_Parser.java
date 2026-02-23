package com.graphhopper.saferoutes.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LST_Median_Parser implements TagParser {

    private static final Logger logger = LoggerFactory.getLogger(LST_Median_Parser.class);

    private final DecimalEncodedValue LSTRawEnc;         // lst_kelvin
    private final DecimalEncodedValue LSTNormalizedEnc;  // lst_normalized
    private static final double DEFAULT_LST = 293.15; // Moderate condition

    // Min and max used for normalization
    private static final double LST_MIN = 273.15;
    private static final double LST_MAX = 343.15;

    public LST_Median_Parser(DecimalEncodedValue LSTRawEnc, DecimalEncodedValue LSTNormalizedEnc) {
        this.LSTRawEnc = LSTRawEnc;
        this.LSTNormalizedEnc = LSTNormalizedEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String LST_Median_Tag = way.getTag("lst_median");
        double rawLST = DEFAULT_LST;

        if (LST_Median_Tag != null) {
            try {
                rawLST = Double.parseDouble(LST_Median_Tag);
            } catch (NumberFormatException e) {
                logger.warn("Invalid LST Median value: {}. Using default {}", LST_Median_Tag, DEFAULT_LST);
            }
        }

        // Normalize to 0–1 range
        double normalized = Math.max(0, Math.min(1, (rawLST - LST_MIN) / (LST_MAX - LST_MIN)));

        // Store into edge
        LSTRawEnc.setDecimal(false, edgeId, edgeIntAccess, rawLST);           // renamed key: "lst_median"
        LSTNormalizedEnc.setDecimal(false, edgeId, edgeIntAccess, normalized); // renamed key: "lst_median_normalized"
    }
}
