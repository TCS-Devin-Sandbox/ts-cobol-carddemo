# s05-missing-account
Customers and cross-references exist but ACCTDATA is empty. The first keyed
account read returns status 23 and abends before writing a statement.

The off-host oracle uses tools/statements_prep.py scratch copies under work/:
PSA/TCB/TIOT traversal is skipped, COSTM01.CPY case and CUSTREC.cpy tabs are
normalized. Originals under app/ are unchanged. This proves statement processing,
not real PSA/TCB/TIOT traversal. The z/OS Running JCL / DD Names from TIOT / UCB
SYSOUT diagnostics are not reproduced. See docs/BASELINE.md for legacy quirks.
Error cases record off-host RC 11: CBSTM03A calls CEE3ABD without the code
argument its local stand-in expects, causing SIGSEGV after the error display.
This does not prove z/OS LE abend-code equivalence.
