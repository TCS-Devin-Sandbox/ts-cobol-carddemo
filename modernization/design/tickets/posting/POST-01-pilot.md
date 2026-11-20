# POST-01 · Pilot: thin slice across CBTRN02C and CBTRN03C

**Source:** `demo/07-handoff`; [POSTTRAN.jcl](../../../../app/jcl/POSTTRAN.jcl) STEP15, [TRANREPT.jcl](../../../../app/jcl/TRANREPT.jcl) STEP05R/STEP10R.

**Scope:** new module `java/posting/posting-batch` (Java 21, Spring Batch, PostgreSQL 15), its own schema (SH-01 option A), a candidate driver that runs the real setup jobs, REPROC and SORT and replaces only STEP15 and STEP10R, and the posting skills.

**Acceptance:** `fixtures/posting/p01-baseline-mixed` passes `compare.py posting p01-baseline-mixed` with both Java programs: TRANSACT, ACCTDATA, TCATBALF, DALYREJS(0) (empty), TRANREPT(0), both SYSOUTs, `rc.json`. Sort by ZD card and both date gates as in the JCL and DATEPARM. Interest 7/7 unchanged. Codec is `FixedRecordCodec`, unchanged.

**Deliverables:** one branch `trackA/posting/pilot-*`, one PR; promoted posting annex stating what it leaves out ([tracks §4](../../tracks.md#4--what-each-pilots-skills-leave-out)).

**Ticket writes:** Prohibited.

**Restrictions:** no edits to `app/`, `fixtures/`, `tools/parity`, `java/interest`, `db/postgres`, shared skills or frozen files; no merge.
