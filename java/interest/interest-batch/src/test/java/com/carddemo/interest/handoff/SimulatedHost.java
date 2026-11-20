package com.carddemo.interest.handoff;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory fence/VSAM/GDG/receipt double; no host command is ever issued (INT-05 restriction). */
final class SimulatedHost implements HostBridge {

    enum ReceiptMode { RECORD, NONE, WRONG_DIGEST }

    Optional<Fence> fence = Optional.empty();
    final Map<String, byte[]> accounts = new HashMap<>();
    final List<String> generations = new ArrayList<>();
    final Map<String, byte[]> generationBytes = new LinkedHashMap<>();
    final Set<String> existingIds = new HashSet<>();
    final Map<String, ApplyStatus> applyFault = new HashMap<>();
    final Map<String, Boolean> applyFaultStillWrites = new HashMap<>();
    CatalogStatus catalogReply = CatalogStatus.CATALOGED;
    boolean catalogWritesAnyway;
    ReceiptMode receiptMode = ReceiptMode.RECORD;
    Runnable afterPreflight = () -> {};
    boolean generationFailsAfterMutation;
    int hostCalls;
    int mutations;
    int catalogCalls;
    final Map<String, Receipt> receipts = new HashMap<>();
    private final Map<String, Map<String, String>> applied = new HashMap<>();

    SimulatedHost(String destination, String cycle) {
        fence = Optional.of(new Fence("F-" + cycle, destination, cycle, true));
    }

    @Override
    public Optional<Fence> observeFence(String destination) {
        hostCalls++;
        return fence;
    }

    @Override
    public String currentGeneration(String destination) {
        hostCalls++;
        if (generationFailsAfterMutation && mutations > 0) {
            throw new IllegalStateException("simulated host error");
        }
        return generations.isEmpty() ? NO_GENERATION : generations.get(generations.size() - 1);
    }

    @Override
    public Set<String> existingTransactionIds(String destination) {
        hostCalls++;
        afterPreflight.run();
        return Set.copyOf(existingIds);
    }

    @Override
    public Optional<byte[]> readAccount(Fence fence, String acctId) {
        hostCalls++;
        return Optional.ofNullable(accounts.get(acctId)).map(byte[]::clone);
    }

    @Override
    public ApplyStatus applyAccountPatch(Fence fence, String operationId, String acctId, byte[] preimageFingerprint,
            byte[] postImage) {
        hostCalls++;
        mutations++;
        ApplyStatus fault = applyFault.get(acctId);
        if (fault == ApplyStatus.PRECONDITION_FAILED) {
            return fault;
        }
        if (!MessageDigest.isEqual(Digests.sha256(accounts.get(acctId)), preimageFingerprint)) {
            return ApplyStatus.PRECONDITION_FAILED;
        }
        if (fault == null || applyFaultStillWrites.getOrDefault(acctId, false)) {
            accounts.put(acctId, postImage.clone());
            applied.computeIfAbsent(operationId, k -> new HashMap<>())
                    .put(acctId, HexFormat.of().formatHex(Digests.sha256(postImage)));
        }
        return fault == null ? ApplyStatus.APPLIED : fault;
    }

    @Override
    public CatalogStatus writeAndCatalog(Fence fence, String operationId, String expectedGeneration, byte[] payload) {
        hostCalls++;
        mutations++;
        catalogCalls++;
        if (catalogReply == CatalogStatus.CATALOGED || catalogWritesAnyway) {
            generations.add(expectedGeneration);
            generationBytes.put(expectedGeneration, payload.clone());
            String digest = HexFormat.of().formatHex(Digests.sha256(payload));
            if (receiptMode == ReceiptMode.WRONG_DIGEST) {
                digest = "0".repeat(64);
            }
            receipts.put(operationId, new Receipt(operationId, expectedGeneration, digest, payload.length / 350,
                    applied.getOrDefault(operationId, Map.of()), List.of(expectedGeneration)));
        }
        return catalogReply;
    }

    @Override
    public Optional<Receipt> receipt(String operationId) {
        hostCalls++;
        return receiptMode == ReceiptMode.NONE ? Optional.empty() : Optional.ofNullable(receipts.get(operationId));
    }
}
