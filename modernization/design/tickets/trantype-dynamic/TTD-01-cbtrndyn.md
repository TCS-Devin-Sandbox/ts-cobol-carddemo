# TTD-01 · CBTRNDYN: dynamic SQL and dynamic call

**Source:** [MNTTRDYN.jcl](../../../../app/app-transaction-type-db2/jcl/MNTTRDYN.jcl) STEP1 `PARMS('COBTUPDT')`, [CBTRNDYN](../../../../app/app-transaction-type-db2/cbl/CBTRNDYN.cbl).

**Scope:** package `…trantype.dynamic`. Statement assembly from DYNSQL, PREPARE/EXECUTE (G6), call by the PARM name through a closed registry whose only proven entry is COBTUPDT (G7), shared unit of work, STOP RUN/RC semantics (C4–C5).

**Acceptance:** d01–d03 pass, incl. the `-400` failure of COBTUPDT after a failed EXECUTE (d03, SH-05 stays OPEN) and the absence of `CBTRNDYN: END`; t01–t07 still pass; interest 7/7 unchanged.

**Waits for:** TTU-02. **Branch:** `trackB/trantype-dynamic/*`.

**Ticket writes:** Prohibited.

**Restrictions:** do not edit `…trantype.update` (change by register row); TTU-01 restrictions apply.
