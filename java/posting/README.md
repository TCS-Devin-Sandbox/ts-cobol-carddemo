# POSTTRAN and TRANREPT

This module ports CBTRN02C (POSTTRAN/STEP15) as a Java 21 Spring Batch job.
It reads DALYTRAN in file order and updates the PostgreSQL account, category
balance and transaction masters in one step transaction. The fixture adapter
loads the input masters with the shared VSAM transport and exports TRANSACT,
ACCTDATA and TCATBALF plus the reject generation, STEP15 SYSOUT and return codes.
TRANREPT runs after POSTTRAN, including when posting has business rejects.
It backs up TRANSACT in key order, filters by the inclusive JCL processing-date
range, and stably sorts by card number. CBTRN03C then applies the separate
DATEPARM range and looks up accounts, types and categories in PostgreSQL.
The backup, sorted input and 133-column report use the same space-trimmed
line format as the fixture exports.

From the repository root:

```sh
docker compose up -d --wait postgres
mvn -B -f java/pom.xml -pl posting -am package
make parity-java CHAIN=posting CANDIDATE='java -jar java/posting/target/carddemo-posting.jar'
```

The candidate needs permission to create a schema in a **local fixture database**.
It creates and drops a distinct schema per invocation; it must not run against
production. Connection defaults are the Compose service's published
`localhost:5432/carddemo` and fixture credentials. Override them with
`POSTING_JDBC_URL`, `PGUSER` and `PGPASSWORD`. The harness passes the processing
clock; a business rejection sets STEP15 and job RC to 4 in `rc.json` without
failing the candidate process. Non-STEP15 entries are zero for the fixture's
successful POSTTRAN setup and wrapper steps, which the Java job does not execute.

## Legacy behavior preserved

- The limit check uses cycle credit minus cycle debit plus the transaction
  amount, not the current balance, and accepts equality. Negative amounts
  are added to the cycle debit as signed values.
- Expiration on the origin date is allowed. If both limit and expiry checks
  fail, the expiry reason wins.
- TRANSACT starts empty, as CBTRN02C opens it for OUTPUT, not I-O.
  Rejects preserve the original daily record and get a four-digit reason and
  its description.
- New category balances inherit the last successfully read category record's
  filler; COBOL `INITIALIZE` leaves filler unchanged. The create message and
  counters use the legacy SYSOUT text.
- The report adds the last transaction again at EOF before writing page and
  grand totals, never writes a final account subtotal, and is empty on a day
  with no qualifying transactions. A missing report lookup sets STEP10R RC 12.

This is a parity runner, not a production scheduler: a rerun starts from clean
fixture state, and the job does not support restart or cutover.
