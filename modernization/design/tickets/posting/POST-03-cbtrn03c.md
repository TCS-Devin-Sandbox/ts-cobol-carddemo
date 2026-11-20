# POST-03 · CBTRN03C: the rest of the report

**Scope:** package `…posting.report` in `java/posting/posting-batch`. POSTTRAN STEP15 stays legacy.

**Acceptance:** p01–p07: `TRANREPT(0)` (133-byte CVTRA07Y lines, edited amounts, account/page/grand totals, recorded EOF shape, zero-byte p07), STEP10R SYSOUT and RC 0 after POSTTRAN RC 4 (p02–p05). Interest 7/7 unchanged.

**Waits for:** POST-01. **Branch:** `trackA/posting/cbtrn03c-*`.

**Ticket writes:** Prohibited.

**Restrictions:** do not edit `…posting.post`, the module POM or migrations (propose to Track A lead); POST-01 restrictions apply.
