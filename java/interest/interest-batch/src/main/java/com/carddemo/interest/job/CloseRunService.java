package com.carddemo.interest.job;

import com.carddemo.interest.calc.AccountGroups;
import com.carddemo.interest.calc.GroupCalculation;
import com.carddemo.interest.calc.GroupWriter;
import com.carddemo.interest.calc.InterestRules;
import com.carddemo.interest.calc.LegacyStatus;
import com.carddemo.interest.intake.SnapshotIntake;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class CloseRunService {
    /** A1 immutable run identity; only shadow/private computation is enabled in INT-04. */
    public record Identity(String parm, String cycle, String feed, String buildRevision, String ruleRevision, String clockProfile) {
        List<String> values() { return List.of(parm, cycle, feed, buildRevision, ruleRevision, clockProfile); }
    }
    public record Result(UUID runId, UUID snapshotId, int rc, String category) {}
    private final CloseStore store;
    private final SnapshotIntake intake;
    private final CloseExporter exporter;
    private final InterestRules rules;
    private final JobRepository repository;
    private final JobLauncher launcher;
    private final PlatformTransactionManager manager;
    private final GroupWriter.CommitHook hook;
    private final Environment environment;

    public CloseRunService(CloseStore store, SnapshotIntake intake, CloseExporter exporter, InterestRules rules,
            JobRepository repository, JobLauncher launcher, PlatformTransactionManager manager, GroupWriter.CommitHook hook, Environment environment) {
        this.store = store; this.intake = intake; this.exporter = exporter; this.rules = rules;
        this.repository = repository; this.launcher = launcher; this.manager = manager; this.hook = hook;
        this.environment = environment;
    }

    /** INT-H06: explicit resume only; COMPUTING additionally requires operator confirmation of abandonment. */
    public Result run(Path input, Path output, Identity identity, UUID resume) throws Exception {
        UUID run = resume == null ? UUID.randomUUID() : resume;
        UUID snapshot;
        try { InterestRules.validateParm(identity.parm()); }
        catch (IllegalArgumentException e) {
            if (resume != null) throw new IllegalArgumentException("Resume identity mismatch");
            exporter.export(run, null, output, 12, "TARGET_PARM_HELD");
            return new Result(run, null, 12, "TARGET_PARM_HELD");
        }
        if (resume == null) {
            var capture = intake.load(input);
            snapshot = capture.snapshotId();
            if (!capture.sealed()) {
                exporter.export(run, snapshot, output, 12, "TARGET_INTAKE_HELD");
                return new Result(run, snapshot, 12, "TARGET_INTAKE_HELD");
            }
            store.jdbc().update("INSERT INTO interest.run VALUES (?,?,?,?,?,'shadow',?,?,?,'READY')", run, snapshot,
                    identity.parm(), identity.cycle(), identity.feed(), identity.buildRevision(), identity.ruleRevision(), identity.clockProfile());
        } else {
            snapshot = new TransactionTemplate(manager).execute(status -> {
                var rows = store.jdbc().queryForList("SELECT * FROM interest.run WHERE run_id=? FOR UPDATE", run);
                if (rows.size() != 1) throw new IllegalArgumentException("Unknown resume run");
                var row = rows.getFirst();
                List<Object> saved = List.of(row.get("parm"), row.get("cycle"), row.get("feed"), row.get("build_revision"), row.get("rule_revision"), row.get("clock_profile"));
                boolean abandoned = "COMPUTING".equals(row.get("state"))
                        && environment.getProperty("interest.close.abandoned", Boolean.class, false);
                if (!saved.equals(identity.values()) || !"shadow".equals(row.get("mode")) || !("FAILED".equals(row.get("state")) || abandoned))
                    throw new IllegalArgumentException("Resume requires FAILED or explicitly abandoned COMPUTING run and identical identity");
                UUID sealed = (UUID) row.get("snapshot_id");
                if (!"SEALED/LAB_ASCII".equals(store.jdbc().queryForObject("SELECT state || '/' || codec_profile FROM interest.snapshot WHERE snapshot_id=?", String.class, sealed)))
                    throw new IllegalArgumentException("Resume requires SEALED snapshot");
                for (var entry : java.util.Map.of(com.carddemo.interest.codec.FixedRecordCodec.Layout.ACCOUNT, "ACCTDATA",
                        com.carddemo.interest.codec.FixedRecordCodec.Layout.XREF, "CARDXREF",
                        com.carddemo.interest.codec.FixedRecordCodec.Layout.TCATBAL, "TCATBALF",
                        com.carddemo.interest.codec.FixedRecordCodec.Layout.DISCGRP, "DISCGRP").entrySet()) {
                    String expected = store.jdbc().queryForObject("SELECT manifest->?->>'record_sha256' FROM interest.snapshot WHERE snapshot_id=?",
                            String.class, entry.getValue(), sealed);
                    if (!SnapshotIntake.recordDigest(store.rows(sealed, entry.getKey())).equals(expected))
                        throw new IllegalArgumentException("Resume snapshot digest mismatch");
                }
                if (abandoned) {
                    store.jdbc().update("UPDATE interest.run SET state='FAILED' WHERE run_id=?", run);
                    LoggerFactory.getLogger(CloseRunService.class).info("event=run-abandoned run={} category=TARGET_RUN_ABANDONED", run);
                }
                store.jdbc().update("UPDATE interest.run SET state='COMPUTING' WHERE run_id=?", run);
                return sealed;
            });
        }
        if (resume == null) store.jdbc().update("UPDATE interest.run SET state='COMPUTING' WHERE run_id=? AND state='READY'", run);
        int rc = 12;
        String category = "TARGET_COMPUTE_FAILED";
        LegacyStatus.Failure legacyFailure = null;
        try {
            var checkpoint = store.checkpoint(run);
            var step = new StepBuilder("interestClose.compute", repository)
                    .<AccountGroups.Group, GroupCalculation.Outcome>chunk(1, manager)
                    .reader(new AccountGroups(store, snapshot, checkpoint))
                    .processor(new GroupCalculation(store, snapshot, run, rules, identity.parm(), checkpoint.ordinal()))
                    .writer(new GroupWriter(store, run, hook)).build();
            var job = new JobBuilder("interestClose", repository).start(step).build();
            var execution = launcher.run(job, new JobParametersBuilder().addString("runId", run.toString())
                    .addString("attempt", UUID.randomUUID().toString()).toJobParameters());
            if (execution.getStatus() == BatchStatus.COMPLETED) { rc = 0; category = "COMPUTED"; }
            else legacyFailure = execution.getAllFailureExceptions().stream().map(CloseRunService::legacyFailure)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        } catch (Exception e) {
            legacyFailure = legacyFailure(e);
        }
        if (legacyFailure != null) {
            category = legacyFailure instanceof GroupCalculation.MissingLookup ? "REQUIRED_LOOKUP_MISSING" : "LEGACY_IO_FAILURE";
            LoggerFactory.getLogger(CloseRunService.class).warn("event=legacy-io-failed run={} dd={} op={} status-hex={}",
                    run, legacyFailure.dd(), legacyFailure.operation(), legacyFailure.statusHex());
        }
        store.jdbc().update("UPDATE interest.run SET state=? WHERE run_id=?", rc == 0 ? "COMPUTED" : "FAILED", run);
        try {
            exporter.export(run, snapshot, output, rc, category, legacyFailure);
        } catch (Exception e) {
            store.jdbc().update("UPDATE interest.run SET state='FAILED' WHERE run_id=?", run);
            exporter.export(run, snapshot, output, 12, "TARGET_EXPORT_FAILED");
            rc = 12; category = "TARGET_EXPORT_FAILED";
        }
        LoggerFactory.getLogger(CloseRunService.class).info("event=run-finished run={} phase=compute category={} rc={}", run, category, rc);
        return new Result(run, snapshot, rc, category);
    }

    private static LegacyStatus.Failure legacyFailure(Throwable e) {
        return e instanceof LegacyStatus.Failure failure ? failure : e.getCause() == null ? null : legacyFailure(e.getCause());
    }
}
