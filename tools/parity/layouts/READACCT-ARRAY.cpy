      * CBACT01C ARRY-FILE FD (ARRYPS), with the five entries expanded.
       01 ARR-ARRAY-REC.
          05  ARR-ACCT-ID                PIC 9(11).
          05  ARR-ACCT-CURR-BAL-1        PIC S9(10)V99.
          05  ARR-ACCT-CURR-CYC-DEBIT-1  PIC S9(10)V99 COMP-3.
          05  ARR-ACCT-CURR-BAL-2        PIC S9(10)V99.
          05  ARR-ACCT-CURR-CYC-DEBIT-2  PIC S9(10)V99 COMP-3.
          05  ARR-ACCT-CURR-BAL-3        PIC S9(10)V99.
          05  ARR-ACCT-CURR-CYC-DEBIT-3  PIC S9(10)V99 COMP-3.
          05  ARR-ACCT-CURR-BAL-4        PIC S9(10)V99.
          05  ARR-ACCT-CURR-CYC-DEBIT-4  PIC S9(10)V99 COMP-3.
          05  ARR-ACCT-CURR-BAL-5        PIC S9(10)V99.
          05  ARR-ACCT-CURR-CYC-DEBIT-5  PIC S9(10)V99 COMP-3.
          05  ARR-FILLER                 PIC X(04).
