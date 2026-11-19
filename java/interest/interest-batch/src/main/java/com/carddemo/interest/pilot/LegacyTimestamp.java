package com.carddemo.interest.pilot;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * INT-Q05 (OPEN, legacy kept) / TN-02: Z-GET-DB2-FORMAT-TIMESTAMP (CBACT04C.cbl:613-626) formats
 * FUNCTION CURRENT-DATE (local wall clock, not the PARM or cycle date) as YYYY-MM-DD-HH.MM.SS.hh0000.
 */
public final class LegacyTimestamp {

    private final Clock clock;

    public LegacyTimestamp(Clock clock) {
        this.clock = clock;
    }

    /** One sample; the caller uses the same value for TRAN-ORIG-TS and TRAN-PROC-TS. */
    public String sample() {
        LocalDateTime now = LocalDateTime.now(clock);
        int hundredths = now.getNano() / 10_000_000;
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond(), hundredths);
    }
}
