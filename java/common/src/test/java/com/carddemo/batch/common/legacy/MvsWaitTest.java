package com.carddemo.batch.common.legacy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Formats documented for MVSWAIT / COBSWAIT in docs/LEGACY-LANGUAGES.md and app/jcl/WAITSTEP.jcl. */
class MvsWaitTest {
    @Test
    void waitstepSysinCardIsThirtySixSeconds() throws Exception {
        List<Duration> waits = new ArrayList<>();
        int rc = new MvsWait(waits::add).cobswait("00003600      VALUE IN CENTISECONDS");
        assertEquals(0, rc);
        assertEquals(List.of(Duration.ofSeconds(36)), waits);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,10", "100,1000", "3600,36000", "99999999,999999990", "4294967295,42949672950"})
    void centisecondsToInterval(long centiseconds, long millis) {
        assertEquals(Duration.ofMillis(millis), MvsWait.interval(centiseconds));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 4294967296L})
    void intervalOutsideUnsignedFullwordIsRejected(long centiseconds) {
        assertThrows(IllegalArgumentException.class, () -> MvsWait.interval(centiseconds));
    }

    @ParameterizedTest
    @CsvSource({"00000000,0", "00003600,3600", "99999999,99999999", "00000001 trailing comment,1"})
    void parmIsFirstEightDigits(String card, long centiseconds) {
        assertEquals(centiseconds, MvsWait.parseParm(card));
        assertTrue(MvsWait.parseParm(card) <= MvsWait.MAX_COBSWAIT_CENTISECONDS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3600", "3600    ", "0000360A", "-0003600", ""})
    void parmOutsideDocumentedFormatIsRejected(String card) {
        assertThrows(IllegalArgumentException.class, () -> MvsWait.parseParm(card));
    }

    @Test
    void defaultIsNoOpBatchStep() throws Exception {
        long start = System.nanoTime();
        assertEquals(0, new MvsWait().cobswait("99999999"));
        assertTrue(System.nanoTime() - start < Duration.ofSeconds(5).toNanos());
    }

    @Test
    void sleepWaiterKeepsTheRealDelay() throws Exception {
        long start = System.nanoTime();
        assertEquals(0, new MvsWait(MvsWait.SLEEP).call(5));
        assertTrue(System.nanoTime() - start >= Duration.ofMillis(50).toNanos());
    }
}
