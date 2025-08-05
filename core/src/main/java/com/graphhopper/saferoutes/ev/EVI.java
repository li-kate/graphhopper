package com.graphhopper.saferoutes.ev;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;

public class EVI {
    public static final String KEY = "evi"; // raw
    public static final String NORMALIZED_KEY = "evi_normalized";

    public static DecimalEncodedValue createRaw() {
        return new DecimalEncodedValueImpl(KEY, 14, 0, 0.0001,
                false, false, false);
    }

    public static DecimalEncodedValue createNormalized() {
        return new DecimalEncodedValueImpl(NORMALIZED_KEY, 14, 0, 0.0001,
                false, false, false);
    }
}
