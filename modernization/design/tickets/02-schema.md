# INT-02 · Define PostgreSQL staging and metadata contracts

**Work item:** INT-02 — Define PostgreSQL staging and metadata contracts.

**Source:** TCS-Devin-Sandbox/ts-cobol-carddemo · `demo/03-rules @ 0dd0a8e672cbdf46479b45b02a5ba8c650de6561` · stream root `INTCALC` (`app/jcl/INTCALC.jcl`). Manifest’s estate baseline: `41519206b887cd0123ea850b46a96cdc9baa69af`.

**Context:** [INT-ARCH v1.0](../architecture.md) A1/A3; all INT-MAP rows and index/derived contracts; INT-R09/R10, INT-H03/H05/H06/H09=A. Use [INT-MAP v1.0](../data-map-interest.md) and the [decision register](../../rules/interest/decisions.md). OPEN decisions remain OPEN; preserve legacy behaviour. A gap is not permission to guess.

**Scope:** `interest-batch/src/main/resources/db/**` and `interest-batch/src/test/java/**/schema/**`. Add versioned SQL and Pg15 tests. Freeze module build, `carddemo.*`, copybook row names/widths and A1 ownership. No loader, calculator, pilot schema edits or host integrations.

**Dependencies:** 01 for the shared Maven module and integration-test harness.

**Acceptance:** Apply SQL to clean PG15 -> all `interest` / `interest_batch` tables and constraints present. Assert precise NUMERIC typmods, filler/key lengths and ordered BYTEA index, valid same-account xrefs with only one flagged AIX-first, per-run ordinal uniqueness but not global TRAN-ID uniqueness, distinct schema grants, and rejection of bad width/duplicate key. Permit absent cross-layout dependencies for INT-R10; do not use SQL defaults or cascades to hide missing data.

**Verification:** `mvn clean verify`; schema tests with i01-shaped valid rows plus SQL negative cases. Record migration inventory, DB version/metadata, positive/negative assertion output, and proof `carddemo.*` is untouched. Tie evidence to the final candidate revision (commit SHA, commands, output, DB assertions and fixture cases).

**Deliverables:** One branch and one open PR limited to this item, its test/fixture output and any gaps or unverified assumptions.

**Ticket writes:** Prohibited.

**Restrictions:** No edits to `app/`, `fixtures/*/expected`, other fixtures, `tools/parity`, the design queue or the decision register; no merge or deploy.
