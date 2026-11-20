package com.carddemo.interest.handoff;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publish adapter's writes (INT-ARCH A1): interest.publication and only the run state column, under the
 * interest_publish grants. One row per run (PK); one live claim per destination/cycle under an advisory lock;
 * RELEASED only together with RECEIVED and a verified receipt.
 */
public final class PublicationLedger {

    public enum State { PRIVATE, PREPARED, HELD, RECEIVED }

    public record Publication(UUID runId, State state, String destination, String priorGeneration,
            String currentGeneration, String operationId, Optional<HoldReason> holdReason) {}

    public sealed interface Claim {
        record Claimed(Publication publication) implements Claim {}
        record Existing(Publication publication) implements Claim {}
        record Conflict() implements Claim {}
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PublicationLedger(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public Optional<Publication> find(UUID runId) {
        return jdbc.query("""
                SELECT state, destination, prior_generation, current_generation, operation_id, receipt->>'reason'
                FROM interest.publication WHERE run_id = ?""",
                (rs, n) -> new Publication(runId, State.valueOf(rs.getString(1)), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5),
                        Optional.ofNullable(rs.getString(6)).map(HoldReason::valueOf)), runId).stream().findFirst();
    }

    public Claim claim(ExportPacket packet, String priorGeneration, String nextGeneration, String operationId,
            String deltaPreconditions) {
        return tx.execute(status -> {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", packet.destination() + "|" + packet.cycle());
            Optional<Publication> existing = find(packet.runId());
            if (existing.isPresent()) {
                return new Claim.Existing(existing.get());
            }
            if (!packet.shadow()) {
                Integer others = jdbc.queryForObject("""
                        SELECT count(*) FROM interest.publication p JOIN interest.run r USING (run_id)
                        WHERE p.destination = ? AND r.cycle = ? AND r.mode = 'live' AND p.run_id <> ?
                          AND p.state IN ('PREPARED','HELD','RECEIVED')""",
                        Integer.class, packet.destination(), packet.cycle(), packet.runId());
                if (others != null && others > 0) {
                    return new Claim.Conflict();
                }
            }
            State state = packet.shadow() ? State.PRIVATE : State.PREPARED;
            jdbc.update("""
                    INSERT INTO interest.publication (run_id, state, payload_checksum, record_count, destination,
                      prior_generation, current_generation, delta_preconditions, operation_id, receipt)
                    VALUES (?,?,?,?,?,?,?,?::jsonb,?,NULL)""",
                    packet.runId(), state.name(), packet.payloadDigest(), packet.recordCount(), packet.destination(),
                    priorGeneration, nextGeneration, deltaPreconditions, operationId);
            return new Claim.Claimed(find(packet.runId()).orElseThrow());
        });
    }

    /** PREPARED/HELD -> HELD with the reason; the run is HELD, never RELEASED. */
    public void hold(UUID runId, HoldReason reason) {
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE interest.publication SET state = 'HELD',
                      receipt = jsonb_build_object('verified', false, 'reason', ?::text)
                    WHERE run_id = ? AND state IN ('PREPARED','HELD')""", reason.name(), runId);
            jdbc.update("UPDATE interest.run SET state = 'HELD' WHERE run_id = ? AND state IN ('COMPUTED','HELD')", runId);
        });
    }

    /**
     * PREPARED/HELD -> RECEIVED with the verified receipt, and run -> RELEASED, in one transaction. Returns false when
     * another call already released it, so the success condition is emitted once.
     */
    public boolean release(UUID runId, String verifiedReceiptJson) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            int published = jdbc.update("""
                    UPDATE interest.publication SET state = 'RECEIVED', receipt = ?::jsonb
                    WHERE run_id = ? AND state IN ('PREPARED','HELD')""", verifiedReceiptJson, runId);
            if (published != 1) {
                return false;
            }
            int released = jdbc.update(
                    "UPDATE interest.run SET state = 'RELEASED' WHERE run_id = ? AND state IN ('COMPUTED','HELD')", runId);
            if (released != 1) {
                status.setRollbackOnly();
                return false;
            }
            return true;
        }));
    }
}
