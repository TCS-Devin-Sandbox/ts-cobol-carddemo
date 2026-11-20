package com.carddemo.interest.handoff;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The mainframe side of the INT-ARCH A4 cutover bridge. This ticket ships no host implementation: tests use a
 * simulated double; real fence, VSAM, GDG and receipt operations are owner prerequisites (INT-H06 OPEN).
 */
public interface HostBridge {

    /** The effective writer fence for a destination, if one is observed. */
    Optional<Fence> observeFence(String destination);

    /** Absolute name of the destination's current (0) generation, or {@link #NO_GENERATION}. */
    String currentGeneration(String destination);

    /** TRAN-IDs already present in the destination's current generation and backup input (A4 collision preflight). */
    Set<String> existingTransactionIds(String destination);

    /** The host ACCTDATA record (300 bytes) read under the fence, if present. */
    Optional<byte[]> readAccount(Fence fence, String acctId);

    /** Conditional REWRITE: replace the record only if its SHA-256 equals {@code preimageFingerprint}. */
    ApplyStatus applyAccountPatch(Fence fence, String operationId, String acctId, byte[] preimageFingerprint,
            byte[] postImage);

    /** Write and catalog exactly one RECFM=F LRECL=350 generation named {@code expectedGeneration}. */
    CatalogStatus writeAndCatalog(Fence fence, String operationId, String expectedGeneration, byte[] payload);

    /** The durable receipt recorded for an operation, if any. */
    Optional<Receipt> receipt(String operationId);

    String NO_GENERATION = "NONE";

    record Fence(String fenceId, String destination, String cycle, boolean active) {}

    enum ApplyStatus { APPLIED, PRECONDITION_FAILED, UNKNOWN }

    enum CatalogStatus { CATALOGED, FAILED, UNKNOWN }

    /**
     * Host-recorded result: the operation, the absolute generation cataloged, its SHA-256 and record count, and
     * the SHA-256 of every account record after its patch.
     */
    record Receipt(String operationId, String generation, String payloadSha256, long recordCount,
            Map<String, String> accountPostImageSha256, List<String> generationsCataloged) {
        public Receipt {
            accountPostImageSha256 = Map.copyOf(accountPostImageSha256);
            generationsCataloged = List.copyOf(generationsCataloged);
        }
    }
}
