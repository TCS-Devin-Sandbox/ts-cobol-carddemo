package com.carddemo.interest.calc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** INT-R10 / TN-03 / TN-04 / TN-07 / TN-09: CBACT04C 234–648, lab diagnostics only. */
public final class LegacyStatus {
    public enum Dd { TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT }
    public enum Operation { OPEN, READ, WRITE, REWRITE, CLOSE }
    public enum Outcome { SUCCESS, END_OF_INPUT, DEFAULT_RATE }
    public enum Profile {
        LAB_ASCII(StandardCharsets.US_ASCII, ByteOrder.BIG_ENDIAN),
        SYNTHETIC_CP037(Charset.forName("IBM037"), ByteOrder.BIG_ENDIAN),
        SYNTHETIC_LITTLE_ENDIAN_ASCII(StandardCharsets.US_ASCII, ByteOrder.LITTLE_ENDIAN);

        private final Charset charset;
        private final ByteOrder binaryOrder;
        Profile(Charset charset, ByteOrder binaryOrder) { this.charset = charset; this.binaryOrder = binaryOrder; }
    }

    private LegacyStatus() {}

    /** TN-04: MOVE zero to the binary word, replace its right byte, then MOVE to PIC 999. */
    public static String format(byte[] status, Profile profile) {
        if (status.length != 2) throw new IllegalArgumentException("Two status bytes required");
        String text = new String(status, profile.charset);
        boolean numeric = text.charAt(0) >= '0' && text.charAt(0) <= '9'
                && text.charAt(1) >= '0' && text.charAt(1) <= '9';
        String digits = numeric && text.charAt(0) != '9' ? "00" + text
                : text.charAt(0) + String.format(Locale.ROOT, "%03d",
                        Short.toUnsignedInt(ByteBuffer.wrap(new byte[]{0, status[1]}).order(profile.binaryOrder).getShort()) % 1000);
        return "FILE STATUS IS: NNNN" + digits;
    }

    /** INT-R10 / INT-H08: only the first exact DISCGRP miss is recoverable; only TCATBALF READ has EOF. */
    public static Outcome check(Dd dd, Operation operation, byte[] status, Profile profile,
            boolean defaultRead, List<String> restrictedPrefix) {
        if (status.length != 2) throw new IllegalArgumentException("Two status bytes required");
        String text = new String(status, profile.charset);
        if (text.equals("00")) return Outcome.SUCCESS;
        if (dd == Dd.TCATBALF && operation == Operation.READ && text.equals("10")) return Outcome.END_OF_INPUT;
        if (dd == Dd.DISCGRP && operation == Operation.READ && !defaultRead && text.equals("23")) return Outcome.DEFAULT_RATE;
        throw new Failure(dd, operation, status, profile, defaultRead, restrictedPrefix);
    }

    /** Restricted prefix is the observed DISPLAY sequence before this failed operation, excluding the banner. */
    public static class Failure extends RuntimeException {
        private final Dd dd;
        private final Operation operation;
        private final byte[] status;
        private final Profile profile;
        private final boolean defaultRead;
        private final List<String> restrictedPrefix;

        public Failure(Dd dd, Operation operation, byte[] status, Profile profile,
                boolean defaultRead, List<String> restrictedPrefix) {
            super("dd=" + dd + " op=" + operation + " status-hex=" + HexFormat.of().formatHex(status));
            if (status.length != 2) throw new IllegalArgumentException("Two status bytes required");
            this.dd = dd; this.operation = operation; this.status = status.clone(); this.profile = profile;
            this.defaultRead = defaultRead; this.restrictedPrefix = List.copyOf(restrictedPrefix);
        }

        public String dd() { return dd.name(); }
        public Operation operation() { return operation; }
        public String statusHex() { return HexFormat.of().formatHex(status); }
        public List<String> restrictedPrefix() { return restrictedPrefix; }

        public List<String> diagnostics() {
            return List.of(diagnostic(), format(status, profile), "ABENDING PROGRAM", "CEE3ABD: USER ABEND U+000000999");
        }

        private String diagnostic() {
            return switch (operation) {
                case OPEN -> switch (dd) {
                    case TCATBALF -> "ERROR OPENING TRANSACTION CATEGORY BALANCE";
                    case XREFFILE -> "ERROR OPENING CROSS REF FILE" + new String(status, profile.charset);
                    case DISCGRP -> "ERROR OPENING DALY REJECTS FILE";
                    case ACCTFILE -> "ERROR OPENING ACCOUNT MASTER FILE";
                    case TRANSACT -> "ERROR OPENING TRANSACTION FILE";
                };
                case READ -> switch (dd) {
                    case TCATBALF -> "ERROR READING TRANSACTION CATEGORY FILE";
                    case XREFFILE -> "ERROR READING XREF FILE";
                    case DISCGRP -> defaultRead ? "ERROR READING DEFAULT DISCLOSURE GROUP" : "ERROR READING DISCLOSURE GROUP FILE";
                    case ACCTFILE -> "ERROR READING ACCOUNT FILE";
                    case TRANSACT -> throw new IllegalArgumentException("No TRANSACT READ in CBACT04C");
                };
                case WRITE -> {
                    if (dd != Dd.TRANSACT) throw new IllegalArgumentException("Only TRANSACT WRITE in CBACT04C");
                    yield "ERROR WRITING TRANSACTION RECORD";
                }
                case REWRITE -> {
                    if (dd != Dd.ACCTFILE) throw new IllegalArgumentException("Only ACCTFILE REWRITE in CBACT04C");
                    yield "ERROR RE-WRITING ACCOUNT FILE";
                }
                case CLOSE -> switch (dd) {
                    case TCATBALF -> "ERROR CLOSING TRANSACTION BALANCE FILE";
                    case XREFFILE -> "ERROR CLOSING CROSS REF FILE";
                    case DISCGRP -> "ERROR CLOSING DISCLOSURE GROUP FILE";
                    case ACCTFILE -> "ERROR CLOSING ACCOUNT FILE";
                    case TRANSACT -> "ERROR CLOSING TRANSACTION FILE";
                };
            };
        }
    }
}
