package com.graphhopper.saferoutes.ev;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;

public class EVI {
    public static final String KEY = "evi";

    public static DecimalEncodedValue create() {
        return new DecimalEncodedValueImpl(KEY, 8, 1, false);
    }
}
