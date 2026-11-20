# PR #21 review reproductions

Starting revision: `34da217c17f1ef9e5cf62067c000bbef084c1008`, `demo/05-g2-logic`.

## 1. Abandoned COMPUTING run — real

`INT_H06_A3_operatorAbandonedComputingRequiresExplicitResumeAndPreservesCheckpoint`
simulates JVM death by retaining COMPUTING business state and STARTED Spring Batch
execution metadata, both without a checkpoint and after the first group commits.
Before the fix, the explicit operator-confirmed resume failed:

```text
Unexpected exception thrown: java.lang.IllegalArgumentException: Resume requires FAILED run and identical identity
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

Chosen contract: `--interest.close.abandoned=true` is the operator's confirmation that
the named COMPUTING run's JVM is dead. Stop/fence that process before using this override;
it is not a heartbeat or automatic liveness detector. The same run ID and immutable
identity/snapshot checks remain mandatory. Inside the locked resume transaction, the
coordinator marks the abandoned run FAILED, logs its masked abandonment event, then
resumes from the private checkpoint with a new Spring Batch attempt. A fresh launch
never adopts an existing run, even when the operator flag is supplied.

After the fix, all five INT-H06 integration tests passed:

```text
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The new assertion covers empty/nonempty checkpoints, identity mismatch refusal,
unconfirmed COMPUTING refusal, no automatic restart, committed-byte preservation,
unique ordinals and byte-exact final datasets/SYSOUT. This is a simulated crash state,
not a physical kill of a live JVM or mainframe recovery verification.

## 2. First-return evidence cannot reach the coordinator — real

`INT_H03_firstReturnEvidencePropertyReachesCoordinatorAndSelectedCardIsEmitted`
adds a second card, explicitly selects the higher card in an external evidence file,
and invokes the production coordinator with the evidence-file property. Before the fix:

```text
TARGET_INTAKE_HELD ==> expected: <0> but was: <12>
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

Set `--interest.close.first-return-evidence=/path/first-return.tsv` for fresh intake.
The UTF-8 file has no header; each row is exactly three tab-separated fields:
11-digit account ID, 16-digit observed first-return card number, and a nonblank
external evidence reference. Duplicate accounts, malformed fields and unreadable files
hold intake with an INT-H03 target reason and no READY run. An absent/empty file supplies
no evidence: single-card input still computes, but multi-card input remains HELD.
The selected card must exist among that account's captured XREF rows. Resume reuses
the sealed selection rather than rereading this external file. File contents and
identifiers are not written to ordinary logs; provenance remains externally supplied,
not authenticated or asserted to be mainframe evidence by the Java job.

After the fix, all three INT-H03 integration tests passed:

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The coordinator IT proves COMPUTED, the persisted evidence/first-card selection,
the selected card in the emitted transaction, unchanged input bytes/manifests,
empty evidence behavior, malformed/duplicate proof refusal and unreadable-file hold.

## 3. Target-error reconstruction reaches future missing lookups — real

`TN_01_INT_R10_targetFailureTranscriptStopsAtReachedGroupNotLaterMissingAccount`
adds a third TCATBALF account with no account/XREF row and injects a target error
before or after the first or second group commits. The third group is never processed.
Before the fix, the first injected target error nevertheless generated a legacy U999:

```text
Unreached third group must not invent a legacy abend ==> expected: <false> but was: <true>
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

The coordinator tracks the last group actually entered by the processor, seeded by
the last committed checkpoint on resume. Target-failure reconstruction stops after
that account, before displaying or looking up any later group. Tracking actual
processing rather than blindly including the checkpoint's next key also handles
an after-commit exception, where the next group has not been reached. No reached
group means no input reconstruction. Explicit typed legacy failures retain their
existing operation diagnostics; missing-lookup reconstruction stays bounded too.

The two new transcript tests plus the existing missing-lookup and typed-operation
failure tests passed:

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The four target-error scenarios assert exact restricted SYSOUT (only the reached
prefix), TARGET_COMPUTE_FAILED/FAILED/RC12, retained committed row counts and no
dataset export. The empty-progress export test proves that even a sealed snapshot
with missing account rows cannot invent a legacy failure before any group is reached.

## Final combined verification after all three fixes

`mvn -B -f java/interest/interest-batch/pom.xml clean verify`, Java 21:

```text
[INFO] Tests run: 63, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

All seven real-driver runs used `close_java.py` and returned STEP15 RC=0;
the unchanged comparator returned seven PASS lines and seven report sections.
`test-output.txt`, `e2e-run.log`, `compare-stdout.txt`, `compare-report.md`,
`restart-and-failure-cases.txt` and this report reflect this verification.
`compare-stdout.txt` and `compare-report.md` are byte-identical to their prior passing
captures. Historical combined-check-branch, SQL, Python and full legacy-parity evidence
was retained, not rerun in this review. All 42 checklist annotations remain PASS.
