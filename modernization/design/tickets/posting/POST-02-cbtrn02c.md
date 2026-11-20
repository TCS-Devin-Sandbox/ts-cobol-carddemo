# POST-02 · CBTRN02C: the rest of posting

**Scope:** package `…posting.post` in `java/posting/posting-batch`. TRANREPT STEP10R stays legacy.

**Acceptance:** p01–p07: TRANSACT, ACCTDATA, TCATBALF, `DALYREJS(0)` (REJTRAN layout, reasons 100–103, 103 over 102), POSTTRAN STEP15 SYSOUT and RC (0 or 4); category create (p06); signed CYC-DEBIT (p06); empty day (p07). Interest 7/7 unchanged.

**Waits for:** POST-01. **Branch:** `trackA/posting/cbtrn02c-*`.

**Ticket writes:** Prohibited.

**Restrictions:** do not edit `…posting.report`, the module POM or migrations (propose to Track A lead); POST-01 restrictions apply.
