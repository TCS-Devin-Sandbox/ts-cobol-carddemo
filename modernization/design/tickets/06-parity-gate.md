# INT-06 · Run candidate parity and technical release-readiness rehearsal

**Work item:** INT-06 — Run candidate parity and technical release-readiness rehearsal.

**Source:** TCS-Devin-Sandbox/ts-cobol-carddemo · `demo/03-rules @ 0dd0a8e672cbdf46479b45b02a5ba8c650de6561` · stream root `INTCALC` (`app/jcl/INTCALC.jcl`). Manifest’s estate baseline: `41519206b887cd0123ea850b46a96cdc9baa69af`.

**Context:** [INT-ARCH v1.0](../architecture.md) A1–A4, all INT-MAP rows, INT-R01–R10 and decision register; INT-Q01–Q06=A, INT-H01–H09=A where evidenced; manifest interest-1–interest-4. Use [INT-MAP v1.0](../data-map-interest.md) and the [decision register](../../rules/interest/decisions.md). OPEN decisions remain OPEN; preserve legacy behaviour. A gap is not permission to guess.

**Scope:** `interest-batch/src/test/java/**/gate/**`, `interest-batch/src/test/resources/gate/**`, `interest-batch/scripts/parity/**` and `interest-batch/docs/verification/**`. No product/schema/codec edits. Frozen comparator, fixtures, rules and shared production interfaces. Exclude production approvals and any real cutover, system tickets, scheduler/host changes or deployment.

**Dependencies:** 04 and 05 (transitively 01–03).

**Acceptance:** All seven candidate cases -> legacy record order, exact field values, RC/SYSOUT, expected dataset counts and field/byte checks (including filler/padding under fixture framing). Assert PG15 per-case transaction/delta counts and exact signed sums with last account excluded from delta; no published effects for failure scenarios. Simulated unstaged/stale snapshot, unknown apply receipt, restoration rehearsal and post-consumption hold -> fail closed as A4. Any mismatch -> nonzero gate and precise diagnostic; do not alter goldens or waive it.

**Verification:** `mvn clean verify`; run unchanged `python3 tools/parity/compare.py interest <case> --actual <run-artifacts>` from root for each case (candidate artifact shape from goldens; compare bytes separately); `make parity CHAIN=interest` may establish the COBOL baseline but is not candidate proof. Archive final-revision compare output, byte checks, SQL totals and rehearsal evidence. Identify outstanding live release sign-offs by register/manifest references, not tickets. Tie evidence to the final candidate revision (commit SHA, commands, output, DB assertions and fixture cases).

**Deliverables:** One branch and one open PR limited to this item, its test/fixture output and any gaps or unverified assumptions.

**Ticket writes:** Prohibited.

**Restrictions:** No edits to `app/`, `fixtures/*/expected`, other fixtures, `tools/parity`, the design queue or the decision register; no merge or deploy.
