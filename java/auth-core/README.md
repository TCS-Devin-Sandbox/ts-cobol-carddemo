# CP00 authorization core (G5)

`AuthorizationService(DataSource, Clock)` uses the migrated V1/V2 tables. Start a
serial working-storage scope with `newInvocation()` and call
`process(convertedRequestText, sendReply)` once per message in that scope. G6 owns
MQ/JMS configuration, the invocation boundary, and the nontransacted send;
the core neither receives nor acknowledges MQ messages.

| COPAUA0C | Behavior |
|---|---|
| 2100 | Unquoted CCPAURQY fields, fixed widths, 13-character NUMVAL staging. Expects a complete request in single-byte text; missing fields are rejected rather than reusing stale input. |
| 5100–5600 / 6000 | Card xref → account and customer; summary limits win when found. Profile is a no-op. Approval is inclusive of remaining limit. A missing card/account/customer supplies 3100 if the request declines; the funds reason is 4100 otherwise. 4200, 4300, 5100, 5200 and 9000 are unreachable here. |
| 6000 / 7100 (Q1/Q2) | The synchronous sender runs before any write, with JDBC transaction synchronization suspended; a successful send is not undone by a subsequent DB failure. The first-response layout is retained (63 CSV bytes and the final blank), but every reply is built fresh. |
| 5500 / 8400 (Q3) | A missing xref skips the summary read; a summary found on the previous lookup still supplies the decision balance, including any update from that request. An insert does not change the found flag. No summary/detail is written for a missing xref. |
| 8400 (Q4/Q5) | A decline adds the previous detail's amount in this invocation, initially zero. A new summary starts at zero balance. After deciding, the summary takes limits from the account work area, which retains the last successful master read (or starts at zero) when that read returns not found. |
| 8000 / 8500 | Summary and detail commit together in PostgreSQL; detail uses the clock's zone and inverted YYDDD/HHMMSSmmm. Duplicate keys fail rather than inventing a new timestamp. |

```sh
mvn -B -f java/pom.xml -pl auth-core -am test
```

The G5 CI job runs the paragraph-traced unit tests and a PostgreSQL 15
Testcontainers run against two requests reconstructed from the supplied
DFSURGU0 unload. It checks expected reply bytes, summary/detail changes, and
failure ordering. This is weaker than legacy parity: the expected replies are
source-derived, and CICS, IMS and IBM MQ are not exercised.
