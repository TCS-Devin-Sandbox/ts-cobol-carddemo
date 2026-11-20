package com.carddemo.interest.pilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LegacyTimestampTest {

    private static LegacyTimestamp at(String localDateTime) {
        ZoneId zone = ZoneOffset.UTC;
        return new LegacyTimestamp(Clock.fixed(LocalDateTime.parse(localDateTime).atZone(zone).toInstant(), zone));
    }

    @Test
    void INT_Q05_TN02_frozenLabClockFormatsDb2Timestamp() {
        assertThat(at("2022-07-05T10:00:00.00").sample()).isEqualTo("2022-07-05-10.00.00.000000");
    }

    @Test
    void INT_Q05_hundredthsTruncateAndFourZerosAppended() {
        assertThat(at("2022-07-05T23:59:58.379").sample()).isEqualTo("2022-07-05-23.59.58.370000");
    }

    @Test
    void INT_Q05_clockIsIndependentOfParm() {
        TransactionIdSource ids = new TransactionIdSource("2022071800");
        assertThat(ids.idFor(1)).startsWith("2022071800");
        assertThat(at("2022-07-05T10:00:00").sample()).startsWith("2022-07-05");
    }
}
