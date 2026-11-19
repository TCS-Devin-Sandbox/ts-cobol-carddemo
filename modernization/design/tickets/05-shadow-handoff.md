# INT-05 · Build export packet and simulated fail-closed handoff

**Work item:** INT-05 — Build export packet and simulated fail-closed handoff.

**Source:** TCS-Devin-Sandbox/ts-cobol-carddemo · `demo/03-rules @ 0dd0a8e672cbdf46479b45b02a5ba8c650de6561` · stream root `INTCALC` (`app/jcl/INTCALC.jcl`). Manifest’s estate baseline: `41519206b887cd0123ea850b46a96cdc9baa69af`.

**Context:** [INT-ARCH v1.0](../architecture.md) A1/A3/A4; A01/A03/A09–A10 and T01–T14; INT-R05/R06/R08/R10, INT-H03/H05/H06=A; manifest interest-1, interest-3, interest-4. Use [INT-MAP v1.0](../data-map-interest.md) and the [decision register](../../rules/interest/decisions.md). OPEN decisions remain OPEN; preserve legacy behaviour. A gap is not permission to guess.

**Scope:** `interest-batch/src/{main,test}/java/**/handoff/**`, `interest-batch/src/{main,test}/resources/handoff/**`. Consumes the A1 staged-schema contract (no shared edits). Define deterministic package/manifest and simulate fence/VSAM/GDG/receipt with a test double, not host commands. Exclude real publishing, production configuration, console operations or fallback execution.

**Dependencies:** 02 and 03 (transitively 01); seeded work rows mean it does not wait for 04.

**Acceptance:** Seed i01-shaped computed rows -> two ordered 350-byte payload records, exact counts/digest, one guarded delta. SQL asserts one publication record/claim per run and no RELEASED mark without verified matching receipt. Wrong preimage, stale/missing fence, backup ID collision, missing result after apply/catalog or shadow mode -> no success condition; repeated verified receipt does not duplicate a generation. Simulate partial account apply and ambiguous catalog; keep held until verified outcome.

**Verification:** `mvn clean verify`; PG15 checks of publication states; compare i01 package fields against golden and validate record geometry/hash; capture ordering, shadow/no-live-effect and fault/duplicate injection evidence. Tie evidence to the final candidate revision (commit SHA, commands, output, DB assertions and fixture cases).

**Deliverables:** One branch and one open PR limited to this item, its test/fixture output and any gaps or unverified assumptions.

**Ticket writes:** Prohibited.

**Restrictions:** No edits to `app/`, `fixtures/*/expected`, other fixtures, `tools/parity`, the design queue or the decision register; no merge or deploy.
