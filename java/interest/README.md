# INTCALC — monthly interest

Run from the repository root with JDK 21, Maven and Docker:

```sh
docker compose up -d --wait postgres
make parity-java CHAIN=interest
make parity-java CHAIN=interest CASE=i02-tie-half-cent
```

The candidate loads the four VSAM fixture inputs into a fresh PostgreSQL schema,
launches Spring Batch's `INTCALC` job (`STEP15`), then exports ACCTDATA, the first
SYSTRAN generation, SYSOUT and return codes for the existing comparator. It takes
the timestamp from the harness clock and the transaction-ID prefix from the
legacy INTCALC PARM (`2022071800`). The candidate drops only its own schema on
exit. For a different test database, set `INTCALC_JDBC_URL`, `PGUSER` and
`PGPASSWORD`; the test role must be allowed to create schemas.

Legacy behaviors preserved:

- CBACT04C truncates each category's interest toward zero at two decimals;
  the half-cent fixture distinguishes this from half-even rounding.
- A missing account-group rate falls back to DEFAULT and writes both legacy
  SYSOUT lines. An explicit zero rate does not fall back or emit a transaction.
- The previous account's balance and cycle totals are rewritten only when a
  new account is read. The final account still emits interest transactions
  but its balance and cycle totals remain untouched.
