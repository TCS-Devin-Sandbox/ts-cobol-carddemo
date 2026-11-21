# FEECALC Java 21 port

Ports CBFEEDRV and the PL/I CBFEECAL without a database. Run tests from the
repository root:

```
mvn -B -f java/pom.xml -pl fees -am test
```

`com.carddemo.batch.fees.FeeDriver` is a command-line entry point taking
`ACCTFILE FEEOUT` paths. Supply sequential, account-key-ordered, fixed 300-byte
records (an export of the indexed master), not newline-delimited fixture files.
It uses the common CVACT01Y codec with the default off-host Latin-1 encoding.
Output is fixed 80-byte records, without newlines, as in the FEECALC JCL.
The shared codec also encodes FEE-LINE's text/edited fields, retaining their
COBOL names. No account is updated; one line is written per account, including
accounts with no fee. The average daily balance is not written to FEEOUT.

## Quirks reproduced

* Request balance is current cycle **credit minus debit**, not current balance.
  COBOL's 12-digit receiving field retains low-order digits on overflow.
* Positive balance maps to 10 late days; all other balances to zero. Cycle days
  are always 30. Fee, average and RC are initialized to zero on each call.
* BigDecimal with `RoundingMode.DOWN` truncates both decimal assignments toward
  zero, including negative average daily balances (not half-even rounding).
* Average is calculated before the no-late-days early return.
* ZERODIVIDE returns fee zero, average zero, RC 8; SIZE returns fee zero, RC 8
  and leaves the average unchanged. Fee size is checked **before** the cap.
  Both handlers emit their CBFEECAL account diagnostic to an injectable
  `PrintStream` (SYSPRINT); `FeeDriver.main` defaults it to `System.out`,
  separate from FEEOUT. PL/I `PUT SKIP LIST` formatting is approximated by
  one newline-terminated message, retaining the literal's trailing blank plus
  one list-item separator blank before the account ID (two blanks total).
  Host-specific PL/I printer spacing, pagination and carriage control are not
  oracle-verified.
* GOLD uses case-sensitive, blank-padded CHAR comparison; balance below 100
  is waived with RC 4, after the late-days check.
* Fee is 2.5 percent, truncated to cents, then floor 5.00, then cap 35.00.
  A negative balance passed directly to CBFEECAL with positive late days is
  lifted to the floor unless GOLD-waived; the normal driver supplies zero late
  days for that balance.
* FEEOUT contains account, group, floating-minus edited balance and fee, and
  two-digit RC, separated/padded exactly to 80 bytes; it does not contain average.

## Evidence limit — weaker proof

There is **no off-host PL/I compiler/oracle**. This port is verified only
against the six documented worked examples in `docs/LEGACY-LANGUAGES.md`;
additional boundary and driver tests encode source-derived expectations, not
independent oracle results. These tests are weaker proof than golden-file
parity. `make parity` checks the existing runnable legacy chains, **not FEECALC**.
The migration tracker is unchanged: C1/C2 still say `not started` despite their
merged implementations, so this change follows that precedent.
