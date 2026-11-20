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
