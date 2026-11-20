package com.carddemo.interest.handoff;

import java.util.UUID;

/** The existing INTCALC success condition that unlocks COMBTRAN (INT-ARCH A2.4); emitted only after a verified receipt. */
public interface ReleaseSignal {
    void intcalcCompleted(UUID runId, String generation);
}
