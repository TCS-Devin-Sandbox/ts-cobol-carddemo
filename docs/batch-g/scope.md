# Batch G scope: authorization (IMS + Db2 + MQ) and VSAM/MQ

Status: proposal for review. Docs only; no port starts until the owner confirms the
IMS and MQ targets in [section 4](#4-decisions-needed-before-any-port-pr). Every fact
below is cited in [inventory.md](inventory.md) (`A/` = `app/app-authorization-ims-db2-mq/`,
`V/` = `app/app-vsam-mq/`). Standing decisions apply: Java 21, Spring Boot/Batch,
PostgreSQL 15; business names in the database and API, copybook names in codecs;
legacy quirks reproduced and documented; parity against COBOL wherever it runs
off-host, weaker proof flagged where it cannot.

## 1. What the modules do

| Unit | Entry | What it does | Data and messages |
|---|---|---|---|
| CP00 / COPAUA0C | MQ trigger (CICS), no screen | Loop: GET a CSV request, look up card -> account -> customer, approve if amount <= limit - balance, PUT1 the CSV reply, write summary + detail to IMS, SYNCPOINT; ends after 501 messages or 5 s idle | MQ request (trigger queue), reply to `ReplyToQ`; CICS files CCXREF, ACCTDAT, CUSTDAT; IMS PAUTSUM0 REPL/ISRT, PAUTDTL1 ISRT; TD queue CSSL for errors |
| CPVS / COPAUS0C | CICS screen COPAU0A | Account summary (counts, balances) and 5-row authorization list with PF7/PF8 paging; `S` selects a row -> CPVD | CICS files CXACAIX, ACCTDAT, CUSTDAT; IMS GU PAUTSUM0, GNP PAUTDTL1 |
| CPVD / COPAUS1C | CICS screen COPAU01A | Detail of one authorization; PF8 next; PF5 toggles fraud (F <-> R) | IMS GU/GNP/REPL PAUTDTL1; LINK COPAUS2C |
| COPAUS2C | LINK from CPVD | INSERT the detail into AUTHFRDS; on -803 UPDATE `AUTH_FRAUD`, `FRAUD_RPT_DATE`. "Remove" keeps the row with `R` | Db2 `CARDDEMO.AUTHFRDS` |
| CBPAUP0C (CBPAUP0J) | IMS BMP | Walk every root, delete details older than N days, delete root when approved count <= 0, checkpoint | IMS GN/GNP/DLET/CHKP; SYSIN `days,chkp-freq,chkp-disp,debug` |
| PAUDBLOD (LOADPADB) | IMS BMP | Flat ROOT (100) + CHILD (6-byte packed key + 200) files -> IMS ISRT | CBLTDLI |
| PAUDBUNL (UNLDPADB) | IMS DLI | IMS -> the same flat ROOT/CHILD files | CBLTDLI |
| DBUNLDGS (UNLDGSAM) | IMS DLI | IMS -> GSAM ROOT (100) and CHILD (200, **no parent key**) | CBLTDLI, GSAM PCBs |
| DBPAUTP0 job | DFSURGU0 | IMS utility unload; the supplied `.dat` is this format | VB records |
| CDRA / COACCT01 | MQ trigger (CICS) | `INQA` + account id -> labelled 252-byte account record padded to 1000, to fixed `CARD.DEMO.REPLY.ACCT` | CICS ACCTDAT read; MQ request/reply/`CARD.DEMO.ERROR` |
| CDRD / CODATE01 | MQ trigger (CICS) | Ignores the request; replies `SYSTEM DATE : MM-DD-YYYY` + `SYSTEM TIME : HH:MM:SS` padded to 1000, to `CARD.DEMO.REPLY.DATE` | MQ only, CICS clock |

**IMS database DBPAUTP0** (HIDAM, index DBPAUTX0): root `PAUTSUM0` (100 bytes,
CIPAUSMY, key `ACCNTID` = packed account id) -> child `PAUTDTL1` (200 bytes,
CIPAUDTY, key `PAUT9CTS` = 8 bytes of two packed *inverted* date/time fields, so
GNP returns newest first). No secondary index. The supplied unload holds 22 roots and
202 details (measured, inventory section 8).

**Db2 AUTHFRDS**: 26 columns, PK (`CARD_NUM`, `AUTH_TS`), written only by COPAUS2C.

**Messages**: authorization request = 18 comma-separated fields (CCPAURQY order), no
quoting; reply = 6 fields each followed by a comma, approved amount as
`-zzzzzzzzz9.99`; CorrelId echoed, nonpersistent, expiry 5 s, outside syncpoint.
CDRA/CDRD: fixed 1000-byte request (`X(4)` function + `9(11)` key) and reply, MsgId
and CorrelId both echoed, inside syncpoint. Queue names in the READMEs do not match
the code; the code wins (inventory section 5).

### Quirks a port must decide on (inventory section 6)

| # | Quirk | Proposed handling |
|---|---|---|
| Q1 | Reply sent **before** IMS write, outside syncpoint (README's "two-phase commit" does not cover MQ) | Reproduce order; document. A target that commits DB first is a deviation needing sign-off |
| Q2 | Reply STRING pointer never reset: first reply is 64 bytes (63 + one stray byte), later replies append and can overflow | Reproduce the first-message reply exactly; do **not** reproduce the accumulation (it corrupts replies); log as deviation |
| Q3 | Summary-found flag not reset between messages | Reproduce per-invocation state in the decision service; test it |
| Q4 | Decline adds the *previous* detail's amount to declined total | Reproduce; test |
| Q5 | New summary starts credit balance at 0, not account balance; limit overwritten after the decision | Reproduce |
| Q6 | Only "insufficient funds" (4100) is ever emitted; 3100 only via flags | Reproduce; list unused codes |
| Q7 | Purge: YYDDD subtraction wrong across years; approved count tested twice, declined never; summary decrements never written back; checkpoint after freq+1 roots; shipped SYSIN = 0 days | Reproduce all; flag the shipped SYSIN as an ops decision |
| Q8 | Fraud `AUTH_TS` built from original date + processing time | Reproduce; key collisions documented |
| Q9 | CPVS keeps 20 previous-page keys with no bound | Bound at 20 in the API with an error, document |
| Q10 | DBUNLDGS child output lacks the parent key, so it cannot be reloaded by PAUDBLOD | Port as-is; note |

## 2. Target proposals for the IMS hierarchical data

| Option | Shape | For | Against |
|---|---|---|---|
| **A. Two relational tables (recommended)** | `pending_auth_summary` (PK `account_id`) and `pending_auth_detail` (PK `account_id`, `auth_date_inv`, `auth_time_inv`; FK to summary, `ON DELETE` restricted) plus `auth_fraud` for AUTHFRDS, all in the B3 schema | Matches every access path (GU root by key, GNP children in key order = `ORDER BY auth_date_inv, auth_time_inv`, REPL/DLET by key); joins to `account`/`card_xref`; IMS + Db2 two-phase commit becomes one PG transaction; same pattern as B3 | Hierarchy is enforced by FK, not storage; inverted keys kept as columns to keep GNP order and codec round-trip (an extra `auth_ts` column can be derived for reporting) |
| B. One JSONB document per account | `pending_auth(account_id, summary jsonb, details jsonb[])` | Closest to the segment tree; one row per GU | Every detail insert rewrites the row; fraud and purge become JSON surgery; no typed `NUMERIC(p,s)`; departs from B3 conventions |
| C. Keep IMS (on z/OS or a rehost runtime) | Java calls IMS via a connector | No data migration | No off-host runtime, so no proof here; keeps the licence the migration is meant to remove |

Codec stays on the copybook names (CIPAUSMY, CIPAUDTY) so the DFSURGU0 unload and the
flat/GSAM extracts round-trip byte-for-byte; the columns use business names, with the
mapping in the schema PR.

## 3. Target proposals for the MQ queues

| Option | Shape | For | Against |
|---|---|---|---|
| **A. Keep IBM MQ through JMS (recommended for CP00)** | Spring JMS listener on the request queue, reply to the message's `ReplyToQ` with CorrelId, nonpersistent, 5 s expiry; IBM MQ client in prod | POS clients keep their wire contract and queue names; trigger monitor replaced by a listener; cut over without client changes | Needs an MQ queue manager in prod; IBM MQ dev image may not be pullable here, so tests use ActiveMQ Artemis through the same JMS API (weaker: MQMD specifics such as CCSID conversion and MQ expiry units are not exercised) |
| **B. REST endpoints (recommended for CDRA/CDRD)** | `GET /api/accounts/{id}/inquiry`, `GET /api/system-time` in the online API, plus the authorization decision behind `POST /api/authorizations` for the UI and tests | No broker; same stack as Batch E; no client exists in the repo for CDRA/CDRD (README describes a client the code does not contain) | Changes the contract for any real MQ client; loses queue buffering |
| C. Kafka or another broker | Topics for request/reply | Scale, replay | No native request/reply; new infrastructure; no client benefit |

The decision logic is transport-agnostic in every option (G5), so the owner can pick
A, B or both per transaction. If CDRA/CDRD keep MQ, they get a JMS adapter like G6.

## 4. Decisions needed before any port PR

1. **IMS target:** option A (two tables + `auth_fraud` in PG) is the default proposal.
2. **MQ target:** IBM MQ via JMS for CP00; REST for CDRA/CDRD (default proposal).
3. **Q1/Q2 deviations:** keep reply-before-write order; fix the reply-buffer
   accumulation (default) rather than reproduce it.
4. **Purge SYSIN:** port reads the same SYSIN; whether prod keeps `00` days is ops'.

## 5. PR rows

None of these programs runs off-host (no IMS, MQ, CICS; CBPAUP0C also needs the
EXEC DLI translator), so **no row has COBOL parity**. The strongest proof available
is the supplied DFSURGU0 unload: real IMS bytes the codecs and loaders must
round-trip. Rows marked *weaker* prove against source-derived expectations only.

| PR | Scope | Touches | Proves itself by (off-host) | Depends on |
|---|---|---|---|---|
| G0 | This scope | `docs/batch-g/**`, plan link | Review | none |
| G1 | Segment codecs CIPAUSMY, CIPAUDTY, AUTHFRDS host layout, and a DFSURGU0 unload reader/writer (RDW + segment prefix) in `java/common` | `java/common/**` | Decode the supplied `.dat`: 22 roots, 202 details, 2 metadata records; re-encode byte-identical (51,736 bytes) | B2, decision 1 |
| G2 | Flyway schema: `pending_auth_summary`, `pending_auth_detail`, `auth_fraud` + `docs/schema-mapping-auth.md` | `java/common/src/main/resources/db/migration/**`, docs | Testcontainers PG15: migrate, boundary values (max COMP-3, negative), child order by inverted key = newest first | B3, decision 1 |
| G3 | Data movement replacing PAUDBLOD, PAUDBUNL, DBUNLDGS: unload -> PG, PG -> flat ROOT/CHILD (100/206) and GSAM (100/200) | `java/auth-data/**` | `.dat` -> PG -> unload byte-identical; PG -> flat -> PG -> flat identical; counts/checksums report like F1. *Weaker*: no legacy flat file to compare (PAUDBUNL cannot run) | G1, G2 |
| G4 | Purge CBPAUP0C as a Spring Batch job (SYSIN parse, Q7 quirks, checkpoint -> chunk commit) | `java/auth-purge/**` | Load the `.dat`, frozen clock 2022-07-05, run with shipped SYSIN and with `05`: expected deletions computed from the source rules and checked in as fixtures. *Weaker*: no COBOL run | G3 |
| G5 | Authorization decision service (COPAUA0C 2100-8500): CSV parse, decision, reply build, summary/detail write in one PG transaction; Q1-Q6 | `java/auth-core/**` (library) | Paragraph-traced unit tests; Testcontainers run over requests built from `.dat` details; reply bytes asserted. *Weaker* | G2, decisions 2-3 |
| G6 | CP00 MQ adapter: JMS listener, ReplyToQ + CorrelId, nonpersistent, 5 s expiry, error log replacing CSSL | `java/auth-mq/**` | Embedded Artemis integration test, byte-exact request/reply. *Weaker*: not IBM MQ | G5, decision 2 |
| G7 | CPVS summary + list API/UI (COPAUS0C), reusing CAVW account/xref lookup | online api/ui, `docs/online/CPVS.md` | API + UI tests per paragraph incl. paging and the 20-page bound | G2, E4 |
| G8 | CPVD detail + fraud toggle (COPAUS1C + COPAUS2C): insert-or-update `auth_fraud` and REPL detail in one transaction, rollback on failure | online api/ui, `docs/online/CPVD.md` | API tests incl. F -> R -> F, duplicate key update, forced failure rollback | G7 |
| G9 | CDRA account inquiry (COACCT01): labelled 252/1000-byte reply as REST (or JMS) | online api or `java/vsam-mq/**` | Unit tests on reply bytes from posting fixture accounts (signed display, padding), NOTFND and bad-function errors. *Weaker* | B3, decision 2 |
| G10 | CDRD system date/time (CODATE01) | same as G9 | Frozen-clock test of the exact 1000-byte reply. *Weaker* | B1, decision 2 |
| G11 | Tracker sync for batch G | `docs/MIGRATION-PLAN.md` | Each row checked against GitHub merged state | after the batch |

Parallelism: G1 and G2 together; then G3, G5, G7, G9, G10 in parallel; G4 after G3,
G6 after G5, G8 after G7. Each code row adds its own CI job (plan rule 6) and stays
under the ~600-line budget.

Optional, not proposed: a `CBLTDLI` stub in C over flat files would let PAUDBLOD and
PAUDBUNL compile and run under GnuCOBOL, giving a legacy oracle for G3. It proves the
stub as much as the code and does not help CBPAUP0C (EXEC DLI) or the CICS programs,
so it is listed only in case the owner wants it.
