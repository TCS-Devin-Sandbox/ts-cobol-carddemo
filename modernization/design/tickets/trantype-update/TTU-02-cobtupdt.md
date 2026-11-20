# TTU-02 · COBTUPDT: the rest of trantype-update

**Scope:** package `…trantype.update`.

**Acceptance:** t01–t07 pass (update, delete, comments, `+100` as recorded in t05 — SH-03 stays OPEN, negative SQLCODE text `-403` and RC 4 in t06, mixed batch t07). COBTUPDT is callable in-process per C1–C7 of [shared-layouts §3](../../shared-layouts.md#3--program-cobtupdt--run-by-trantype-update-called-by-trantype-dynamic), with a test proving it. Interest 7/7 unchanged.

**Waits for:** TTU-01. **Branch:** `trackB/trantype-update/*`.

**Ticket writes:** Prohibited.

**Restrictions:** TTU-01 restrictions apply.
