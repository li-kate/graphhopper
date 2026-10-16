package com.graphhopper.saferoutes.ev;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;

public class UTCI_AP {
    public static final String KEY = "utci_ap"; // raw
    public static final String NORMALIZED_KEY = "utci_ap_normalized";

    public static DecimalEncodedValue createRaw() {
        // factor 0.0001 => 4 decimal places; 19 bits covers the 0..45 UTCI range (max ~52.4)
        return new DecimalEncodedValueImpl(KEY, 19, 0, 0.0001, false, false, false);
    }

    public static DecimalEncodedValue createNormalized() {
        return new DecimalEncodedValueImpl(NORMALIZED_KEY, 14, 0, 0.0001,
                false, false, false);
    }
}
