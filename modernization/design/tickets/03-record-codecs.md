# INT-03 · Reversible fixed-record codecs and encoded keys

**Work item:** INT-03 — Reversible fixed-record codecs and encoded keys.

**Source:** TCS-Devin-Sandbox/ts-cobol-carddemo · `demo/03-rules @ 0dd0a8e672cbdf46479b45b02a5ba8c650de6561` · stream root `INTCALC` (`app/jcl/INTCALC.jcl`). Manifest’s estate baseline: `41519206b887cd0123ea850b46a96cdc9baa69af`.

**Context:** [INT-ARCH v1.0](../architecture.md) A2; all INT-MAP rows; INT-R08/R09; INT-Q05=A, INT-H03=A; TN-01/02/06. Use [INT-MAP v1.0](../data-map-interest.md) and the [decision register](../../rules/interest/decisions.md). OPEN decisions remain OPEN; preserve legacy behaviour. A gap is not permission to guess.

**Scope:** `interest-batch/src/{main,test}/java/**/codec/**`, `interest-batch/src/test/resources/codec/**`. Frozen interface: decode `(layout, profile, raw bytes)` to typed fields plus original bytes, derive exact legacy key bytes, and encode the validated record/patch; keep test codecs separate from intake. Exclude production CCSID certification, AIX evidence generation, date parsing, COMP-3 support for unobserved layouts and SQL migrations.

**Dependencies:** 01 for the shared Maven module and test conventions; no schema dependency.

**Acceptance:** Valid harness input and synthetic cp037 bytes -> exact fixed records, preserved blank/filler/leading zero fields, signed amounts and unaltered-field bytes. Bad lengths, invalid code page/overpunch/digits or wrong padding -> structured rejection with layout/row/offset. SQL assertion: N/A (no DB writes); byte/key order verified independently.

**Verification:** `mvn clean verify`; round-trip fixture i01 and i06 signed data plus synthetic locale-order, signed-zero, altered ACCT fields, exact 50/300/350-byte framing and unmappable input; test unchanged bytes separately from field comparison. Tie evidence to the final candidate revision (commit SHA, commands, output, DB assertions and fixture cases).

**Deliverables:** One branch and one open PR limited to this item, its test/fixture output and any gaps or unverified assumptions.

**Ticket writes:** Prohibited.

**Restrictions:** No edits to `app/`, `fixtures/*/expected`, other fixtures, `tools/parity`, the design queue or the decision register; no merge or deploy.
