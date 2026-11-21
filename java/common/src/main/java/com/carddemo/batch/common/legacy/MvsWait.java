package com.carddemo.batch.common.legacy;

import java.time.Duration;

/**
 * Java replacement for the HLASM timed wait {@code MVSWAIT} (app/asm/MVSWAIT.asm, {@code STIMER WAIT} via the
 * {@code ASMWAIT} macro) and its batch driver {@code COBSWAIT} (job {@code WAITSTEP}). The parameter is a fullword
 * count of hundredths of a second; the routine always returns RC 0 and has no other output. The default waiter is a
 * no-op, because a wait has no functional effect on a batch chain; {@link #SLEEP} keeps the real delay.
 */
public final class MvsWait {
    public static final int RETURN_CODE = 0;
    /** {@code MVSWAIT-TIME PIC 9(8) COMP} in {@code COBSWAIT}. */
    public static final long MAX_COBSWAIT_CENTISECONDS = 99_999_999L;
    private static final long MAX_FULLWORD = 0xFFFF_FFFFL;
    private static final int PARM_LENGTH = 8;

    @FunctionalInterface
    public interface Waiter {
        void await(Duration interval) throws InterruptedException;
    }

    public static final Waiter NO_OP = interval -> { };
    public static final Waiter SLEEP = Thread::sleep;

    private final Waiter waiter;

    public MvsWait() {
        this(NO_OP);
    }

    public MvsWait(Waiter waiter) {
        this.waiter = waiter;
    }

    /** {@code CALL 'MVSWAIT' USING MVSWAIT-TIME}: waits {@code centiseconds} hundredths of a second, returns RC 0. */
    public int call(long centiseconds) throws InterruptedException {
        waiter.await(interval(centiseconds));
        return RETURN_CODE;
    }

    /** {@code COBSWAIT}: {@code ACCEPT PARM-VALUE FROM SYSIN} (first 8 bytes of the card), then {@code MVSWAIT}. */
    public int cobswait(String sysinCard) throws InterruptedException {
        return call(parseParm(sysinCard));
    }

    public static Duration interval(long centiseconds) {
        if (centiseconds < 0 || centiseconds > MAX_FULLWORD) {
            throw new IllegalArgumentException("MVSWAIT interval is an unsigned fullword, got " + centiseconds);
        }
        return Duration.ofMillis(centiseconds * 10);
    }

    /**
     * The documented SYSIN format: eight decimal digits of centiseconds in columns 1-8, e.g. {@code 00003600} for
     * 36 seconds; the rest of the card is a comment. Anything else is rejected: moving a non-numeric
     * {@code PIC X(8)} into {@code PIC 9(8) COMP} is compiler-dependent on the host.
     */
    public static long parseParm(String sysinCard) {
        String parm = sysinCard.length() >= PARM_LENGTH ? sysinCard.substring(0, PARM_LENGTH) : sysinCard;
        if (!parm.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("COBSWAIT PARM must be 8 digits of centiseconds, got '" + parm + "'");
        }
        return Long.parseLong(parm);
    }
}
