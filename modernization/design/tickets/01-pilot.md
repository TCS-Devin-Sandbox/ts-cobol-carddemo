# INT-01 · Pilot: read, compute and persist the simplest fixture

**Work item:** INT-01 — Pilot: read, compute and persist the simplest fixture.

**Source:** TCS-Devin-Sandbox/ts-cobol-carddemo · `demo/03-rules @ 0dd0a8e672cbdf46479b45b02a5ba8c650de6561` · stream root `INTCALC` (`app/jcl/INTCALC.jcl`). Manifest’s estate baseline: `41519206b887cd0123ea850b46a96cdc9baa69af`.

**Context:** [INT-ARCH v1.0](../architecture.md) A1–A3; A01/A03/A09–A10/A12, X01/X03, B01–B04, D01–D04, T01–T14; INT-R01/R02/R05–R09; INT-Q01/Q02/Q05=A; INT-H02=A; TN-02/06. Use [INT-MAP v1.0](../data-map-interest.md) and the [decision register](../../rules/interest/decisions.md). OPEN decisions remain OPEN; preserve legacy behaviour. A gap is not permission to guess.

**Scope:** `interest-batch/pom.xml`, `interest-batch/src/{main,test}/java/**/pilot/**`, `interest-batch/src/{main,test}/resources/pilot/**`. Bootstrap a Java 21/Spring Batch Maven module and one PostgreSQL 15 pilot step. Use private disposable `interest_pilot` tables and the simple fixture profile. Freeze the copybook layouts, source input/expected dataset path conventions and clock/PARM. Exclude generic codecs, production DDL, full error/SYSOUT parity, restart and any bridge.

**Dependencies:** None. Independent of the production schema and codecs.

**Acceptance:** Read real i01 inputs, not transcribed rows. Exact default-free keyed lookups and decimal calculation -> two staged transactions `2022071800000001` / `...000002`, amounts 10.00 and 37.00 in order, sum 47.00, correct cards/constants and both timestamps `2022-07-05-10.00.00.000000`. SQL asserts two transaction rows and one delta: account `00000002001` has 1010.00 with zero cycle totals; `00000002002` stays 2400.00 with no delta. Unreadable input or missing lookup -> failed step, no committed pilot outputs.

**Verification:** From this module run `mvn clean verify` on Java 21 against PostgreSQL 15. Compare pilot-generated T01–T13 and A03/A09–A10 against decoded i01 golden records (including the untouched account); report the matched rows and cents plus rollback assertions. Evidence must clearly distinguish field slice from full parity. Tie evidence to the final candidate revision (commit SHA, commands, output, DB assertions and fixture cases).

**Deliverables:** One branch and one open PR limited to this item, its test/fixture output and any gaps or unverified assumptions.

**Ticket writes:** Prohibited.

**Restrictions:** No edits to `app/`, `fixtures/*/expected`, other fixtures, `tools/parity`, the design queue or the decision register; no merge or deploy.
