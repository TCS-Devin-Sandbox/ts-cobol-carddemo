# INT-04 · Integrate immutable snapshots, full legacy calculation and private restart

**Work item:** INT-04 — Integrate immutable snapshots, full legacy calculation and private restart.

**Source:** TCS-Devin-Sandbox/ts-cobol-carddemo · `demo/03-rules @ 0dd0a8e672cbdf46479b45b02a5ba8c650de6561` · stream root `INTCALC` (`app/jcl/INTCALC.jcl`). Manifest’s estate baseline: `41519206b887cd0123ea850b46a96cdc9baa69af`.

**Context:** [INT-ARCH v1.0](../architecture.md) A1–A3; A01–A13, X01–X04, B00–B05, D00–D05, T01–T14; INT-R01–R10; INT-Q01–Q06=A and INT-H01–H09=A where evidenced; TN-01–09. Use [INT-MAP v1.0](../data-map-interest.md) and the [decision register](../../rules/interest/decisions.md). OPEN decisions remain OPEN; preserve legacy behaviour. A gap is not permission to guess.

**Scope:** `interest-batch/src/{main,test}/java/**/{intake,job,calc}/**`, `interest-batch/src/{main,test}/resources/{intake,job}/**`. Freeze schema/codec interfaces, build config, input/export layouts, and A1 staged output for ticket 05. Only private target recovery; no scheduler overrides, new fee rules, publish or write to legacy data.

**Dependencies:** 02 and 03 (transitively 01).

**Acceptance:** Valid sealed snapshot -> legacy ordered outputs, non-final account patches and atomic checkpoint (delta and transactions appear together); input key and snapshot cannot change on replay. SQL asserts last/no-balance account exclusion, no writes to four sealed input tables, stable ordinal/delta counts and identical committed amounts after crashes before/after commit. Exact-rate zero skips (nonzero math can emit 0.00), DEFAULT/negative paths match rules; absent required lookup or malformed data -> declared phase error, U999/harness RC 12 for corresponding compute errors, no release-ready state. Multi-card with no evidence -> intake held, not guessed.

**Verification:** `mvn clean verify`; exercise all seven cases on private PG15, negative/missing lookups, zero-truncated output, first/last-only run, width/suffix overflow without dropping rows, and crash/resume. Capture SQL totals/counts/checkpoint and failure diagnostics. Tie evidence to the final candidate revision (commit SHA, commands, output, DB assertions and fixture cases).

**Deliverables:** One branch and one open PR limited to this item, its test/fixture output and any gaps or unverified assumptions.

**Ticket writes:** Prohibited.

**Restrictions:** No edits to `app/`, `fixtures/*/expected`, other fixtures, `tools/parity`, the design queue or the decision register; no merge or deploy.
