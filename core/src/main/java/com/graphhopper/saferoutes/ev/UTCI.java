package com.graphhopper.saferoutes.ev;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;

public class UTCI {
    public static final String KEY = "utci"; // raw
    public static final String NORMALIZED_KEY = "utci_normalized";

    public static DecimalEncodedValue createRaw() {
        return new DecimalEncodedValueImpl(KEY, 16, 4, false);
    }

    public static DecimalEncodedValue createNormalized() {
        return new DecimalEncodedValueImpl(NORMALIZED_KEY, 14, 0, 0.0001,
                false, false, false);
    }
}
