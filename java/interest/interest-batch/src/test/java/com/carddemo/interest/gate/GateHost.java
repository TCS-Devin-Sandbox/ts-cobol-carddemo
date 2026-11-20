package com.carddemo.interest.gate;

import com.carddemo.interest.handoff.HostBridge;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class GateHost implements HostBridge {
    final Map<String, byte[]> accounts = new LinkedHashMap<>();
    final Map<String, byte[]> beforeJournal = new LinkedHashMap<>();
    final Map<String, String> appliedHashes = new HashMap<>();
    final Map<String, byte[]> generations = new LinkedHashMap<>();
    final Map<String, byte[]> quarantine = new LinkedHashMap<>();
    final Map<String, Receipt> receipts = new HashMap<>();
    Fence fence;
    boolean changeFenceAfterPreflight;
    boolean unknownApply;
    int unknownApplyAt = 1;
    boolean ambiguousCatalog;
    boolean consumed;
    int applyCalls;
    int catalogCalls;
    int restored;
    int signals;

    GateHost(String destination, String cycle) {
        fence = new Fence("gate-fence", destination, cycle, true);
    }
    static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public Optional<Fence> observeFence(String destination) { return Optional.of(fence); }
    @Override public String currentGeneration(String destination) {
        return generations.isEmpty() ? NO_GENERATION : new ArrayList<>(generations.keySet()).getLast();
    }
    @Override public Set<String> existingTransactionIds(String destination) {
        if (changeFenceAfterPreflight) fence = new Fence("changed-fence", destination, fence.cycle(), true);
        return Set.of();
    }
    @Override public Optional<byte[]> readAccount(Fence observed, String account) {
        return Optional.ofNullable(accounts.get(account)).map(byte[]::clone);
    }
    @Override public ApplyStatus applyAccountPatch(Fence observed, String operation, String account, byte[] preimage, byte[] postimage) {
        applyCalls++;
        if (!observed.equals(fence) || !MessageDigest.isEqual(digest(accounts.get(account)), preimage))
            return ApplyStatus.PRECONDITION_FAILED;
        beforeJournal.putIfAbsent(account, accounts.get(account).clone());
        accounts.put(account, postimage.clone());
        appliedHashes.put(account, HexFormat.of().formatHex(digest(postimage)));
        return unknownApply && applyCalls >= unknownApplyAt ? ApplyStatus.UNKNOWN : ApplyStatus.APPLIED;
    }
    @Override public CatalogStatus writeAndCatalog(Fence observed, String operation, String generation, byte[] payload) {
        if (consumed) throw new IllegalStateException("No publication after consumption");
        catalogCalls++;
        if (!observed.equals(fence) || generations.containsKey(generation)) return CatalogStatus.FAILED;
        generations.put(generation, payload.clone());
        if (ambiguousCatalog) return CatalogStatus.UNKNOWN;
        receipts.put(operation, new Receipt(operation, generation, HexFormat.of().formatHex(digest(payload)),
                payload.length / 350, appliedHashes, List.of(generation)));
        return CatalogStatus.CATALOGED;
    }
    @Override public Optional<Receipt> receipt(String operation) { return Optional.ofNullable(receipts.get(operation)); }

    void restoreJournalAndQuarantine(String generation) {
        if (consumed) throw new IllegalStateException("Manual incident reconciliation required");
        beforeJournal.forEach((account, raw) -> { accounts.put(account, raw.clone()); restored++; });
        byte[] payload = generations.remove(generation);
        if (payload != null) quarantine.put(generation, payload);
    }
}
