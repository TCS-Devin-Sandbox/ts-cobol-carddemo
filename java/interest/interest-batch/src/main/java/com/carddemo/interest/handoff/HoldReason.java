package com.carddemo.interest.handoff;

/**
 * Why a publication stays HELD (INT-ARCH A3 Publish row, A4; INT-H06 OPEN). None of these emits the INTCALC success
 * condition; a held run is resolved only by a verified matching receipt, never by a blind retry.
 */
public enum HoldReason {
    /** Another run of the same destination/cycle holds the live release claim. */
    CLAIM_CONFLICT,
    /** No scheduler/CICS/host fence was observed for the destination. */
    FENCE_MISSING,
    /** The fence is inactive, belongs to another cycle or changed during the handoff. */
    FENCE_STALE,
    /** The host (0) generation differs from the one recorded at claim time. */
    GENERATION_MOVED,
    /** A host account record no longer matches the computed preimage. */
    PREIMAGE_MISMATCH,
    /** A payload TRAN-ID already exists in the backup/current input or repeats inside the payload (INT-H05). */
    TXN_ID_COLLISION,
    /** An account patch was rejected or its outcome is unknown; earlier patches may be applied. */
    APPLY_UNCERTAIN,
    /** Write/catalog of SYSTRAN(+1) failed or its outcome is unknown. */
    CATALOG_AMBIGUOUS,
    /** No durable host receipt exists after apply/catalog. */
    RECEIPT_MISSING,
    /** The receipt does not match the packet (operation, generation, digest, count or account post-images). */
    RECEIPT_MISMATCH
}
