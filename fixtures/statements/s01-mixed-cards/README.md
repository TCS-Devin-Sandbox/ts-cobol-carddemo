# s01-mixed-cards
Transactions arrive in transaction-ID order, interleaving two cards; SORT orders
by card then transaction ID and OUTREC builds the 32-byte composite key before
REPRO. Purchases, a payment and a refund prove signed amounts and per-card totals
(-75.00 and 64.50). A third cross-reference with no transactions still gets a
zero-total statement. Both 80-byte text and 100-byte HTML output are captured.

The off-host oracle uses tools/statements_prep.py scratch copies under work/:
PSA/TCB/TIOT traversal is skipped, COSTM01.CPY case and CUSTREC.cpy tabs are
normalized. Originals under app/ are unchanged. This proves statement processing,
not real PSA/TCB/TIOT traversal. The z/OS Running JCL / DD Names from TIOT / UCB
SYSOUT diagnostics are not reproduced. See docs/BASELINE.md for legacy quirks.
Error cases record off-host RC 11: CBSTM03A calls CEE3ABD without the code
argument its local stand-in expects, causing SIGSEGV after the error display.
This does not prove z/OS LE abend-code equivalence.
