# Statements chain (Java 21 / Spring Batch)

Port of `CREASTMT` (plan row D4): `DELDEF01` (IDCAMS), `STEP010` (SORT/OUTREC), `STEP020` (REPRO),
`STEP030` (IEFBR14) and `STEP040` (`CBSTM03A` with `CBSTM03B` as its file driver), as one Spring
Batch job of tasklet steps.

```sh
make parity-java CHAIN=statements   # builds the jar, starts PostgreSQL, runs every fixture
```

## Data

* Masters (`ACCTDATA`, `CARDXREF`, `CUSTDATA`) and the transaction input (`DALYTRAN.PS.INIT`, loaded as
  the `TRANSACT` KSDS) live in PostgreSQL with business column names (B4 schema from `java/common`),
  one private schema per run. `CBSTM03B`'s reads become SQL: XREFFILE in card-number order
  (`COLLATE "C"`), CUSTFILE and ACCTFILE by key.
* `TRXFL.SEQ`, `TRXFL.VSAM.KSDS`, `STATEMNT.PS` and `STATEMNT.HTML` are file exports in the legacy
  layouts (`COSTM01`, 80-byte text, 100-byte HTML), written trailing-space trimmed like the goldens.

## Legacy behaviour kept

* SORT is on card number then transaction ID; OUTREC `279:279,50` keeps only 50 of the 52 timestamp
  bytes, so `TRNX-PROC-TS` loses its last two characters.
* One statement per cross-reference, including cards with no transactions (total `.00`).
* Totals add signed amounts; overflow past `S9(9)V99` drops high-order digits (no `ON SIZE ERROR`).
* HTML name and address lines are cut at the first double space and end in two spaces; the name
  line therefore shows only the first name when the middle name is blank.
* An empty transaction file is an error (`ERROR READING TRNXFILE`, status 10); a missing customer or
  account is status 23. Each ends the step with RC 11 and the off-host CEE3ABD SYSOUT recorded by
  D2 (implicit CLOSE warnings in reverse open order); see `docs/BASELINE.md`. An empty XREF file
  gives empty reports at RC 0.
* `STEP020`-`STEP040` carry `COND=(0,NE)`: they are bypassed, with no return code, after a non-zero
  step.

## Not proven by the fixtures

* More than 51 cards or 10 transactions per card overflow the COBOL tables; the port stops with an
  error instead of reproducing the overflow.
* z/OS-only TIOT/UCB diagnostics are out of scope, as in the D2 oracle.
