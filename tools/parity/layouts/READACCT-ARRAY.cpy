      * CBACT01C ARRY-FILE FD (ARRYPS).
       01 ARR-ARRAY-REC.
          05  ARR-ACCT-ID                PIC 9(11).
          05  ARR-ACCT-BAL OCCURS 5 TIMES.
            10  ARR-ACCT-CURR-BAL        PIC S9(10)V99.
            10  ARR-ACCT-CURR-CYC-DEBIT  PIC S9(10)V99 COMP-3.
          05  ARR-FILLER                 PIC X(04).
