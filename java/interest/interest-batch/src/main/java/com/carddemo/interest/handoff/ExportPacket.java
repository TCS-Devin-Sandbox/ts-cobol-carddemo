package com.carddemo.interest.handoff;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * INT-ARCH A2.3 / A4: the deterministic export packet for one COMPUTED run - the ordered fixed 350-byte SYSTRAN
 * payload (T01-T14, RECFM=F, no framing), its count and SHA-256, the guarded A03/A09/A10 account patches and a
 * canonical manifest. Immutable; every byte array is defensively copied.
 */
public record ExportPacket(UUID runId, String mode, String destination, String cycle, String feed,
        List<PayloadRecord> records, List<GuardedDelta> deltas, byte[] payloadDigest, String manifest) {

    public static final int LRECL = 350;

    public ExportPacket {
        records = List.copyOf(records);
        deltas = List.copyOf(deltas);
        payloadDigest = payloadDigest.clone();
        Objects.requireNonNull(manifest);
    }

    @Override
    public byte[] payloadDigest() {
        return payloadDigest.clone();
    }

    public long recordCount() {
        return records.size();
    }

    public boolean shadow() {
        return "shadow".equals(mode);
    }

    /** The SYSTRAN(+1) bytes in emit-ordinal order: count x 350 bytes, no LF/RDW. */
    public byte[] payload() {
        byte[] out = new byte[records.size() * LRECL];
        int at = 0;
        for (PayloadRecord r : records) {
            byte[] raw = r.raw();
            System.arraycopy(raw, 0, out, at, LRECL);
            at += LRECL;
        }
        return out;
    }

    public byte[] manifestBytes() {
        return manifest.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] manifestDigest() {
        return Digests.sha256(manifestBytes());
    }

    public String payloadDigestHex() {
        return HexFormat.of().formatHex(payloadDigest);
    }

    /** One staged interest_tx row (T01 is the legacy ID; order is the emit ordinal, never the ID - INT-R08). */
    public record PayloadRecord(long ordinal, String txnId, byte[] raw) {
        public PayloadRecord {
            if (raw.length != LRECL) {
                throw new IllegalArgumentException("record geometry");
            }
            raw = raw.clone();
        }

        @Override
        public byte[] raw() {
            return raw.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PayloadRecord p && p.ordinal == ordinal && p.txnId.equals(txnId)
                    && Arrays.equals(p.raw, raw);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ordinal, txnId, Arrays.hashCode(raw));
        }
    }

    /**
     * A conditional account patch (INT-R06/R07, TN-06): apply only when the host record still has the preimage
     * fingerprint; afterwards the host record must have the post-image fingerprint.
     */
    public record GuardedDelta(String acctId, BigDecimal oldCurrentBalance, BigDecimal newCurrentBalance,
            BigDecimal oldCycleCredit, BigDecimal newCycleCredit, BigDecimal oldCycleDebit, BigDecimal newCycleDebit,
            byte[] preimageFingerprint, byte[] postImage) {

        public GuardedDelta {
            preimageFingerprint = preimageFingerprint.clone();
            postImage = postImage.clone();
        }

        @Override
        public byte[] preimageFingerprint() {
            return preimageFingerprint.clone();
        }

        @Override
        public byte[] postImage() {
            return postImage.clone();
        }

        public byte[] postImageFingerprint() {
            return Digests.sha256(postImage);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GuardedDelta d && d.acctId.equals(acctId)
                    && Arrays.equals(d.preimageFingerprint, preimageFingerprint) && Arrays.equals(d.postImage, postImage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(acctId, Arrays.hashCode(preimageFingerprint), Arrays.hashCode(postImage));
        }
    }
}
