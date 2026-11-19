# INT-01 pilot specimens (synthetic)

Each directory is a copy of `fixtures/interest/i01-baseline/input` (ACCTDATA, CARDXREF, DISCGRP, TCATBALF)
with one targeted change. They exercise failure/rollback and lookup paths the i01 fixture does not reach.
Expected values are stated in the tests from the rule text, not recorded from a candidate run.

| Specimen | Change against i01 | Path |
|---|---|---|
| malformed-tcatbalf | 2nd TCATBALF sign byte `{` -> `X` | INT-R10 unreadable input |
| missing-input | DISCGRP dataset removed | INT-R10 unreadable input |
| missing-account | ACCTDATA row 00000002002 removed | INT-R10 ACCTFILE status 23 |
| missing-xref | CARDXREF row for 00000002002 removed | INT-R10 XREFFILE status 23 |
| missing-default-rate | acct 00000002001 group PLATINUM; DEFAULT 01/0001 removed | INT-H08 |
| default-fallback | acct 00000002001 group PLATINUM | INT-R03 |
| empty-tcatbalf | TCATBALF empty | behaviour m (empty input) |
