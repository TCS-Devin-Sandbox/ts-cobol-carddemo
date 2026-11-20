# TTU-01 · Pilot: COBTUPDT on t01-add

**Source:** `demo/07-handoff`; [MNTTRDB2.jcl](../../../../app/app-transaction-type-db2/jcl/MNTTRDB2.jcl) STEP1, [COBTUPDT](../../../../app/app-transaction-type-db2/cbl/COBTUPDT.cbl).

**Scope:** new module `java/trantype/trantype-batch`; driver that replaces `RUN PROGRAM(COBTUPDT)` in STEP1 against the frozen `carddemo` tables and `db/seed.sql`; trantype skills and a new `carddemo-stream-trantype` annex. Close gaps G1–G5 as far as t01 needs and list the rest as open.

**Acceptance:** `compare.py trantype-update t01-add` passes: SYSOUT (incl. `DSNRUN:` lines), `rc.json`, both table snapshots. Interest 7/7 unchanged. No standard is written; gaps are listed.

**Branch:** `trackB/trantype-update/pilot-*`.

**Ticket writes:** Prohibited.

**Restrictions:** no edits to `app/`, `fixtures/`, `tools/parity`, `db/postgres`, `db/seed.sql`, `java/interest`, `java/posting`, shared skills or frozen files; no merge.
