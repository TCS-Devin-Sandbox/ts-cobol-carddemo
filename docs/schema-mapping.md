# Schema mapping — DB2 for z/OS → PostgreSQL 15

Sources: `ddl/TRNTYPE.ddl`, `ddl/TRNTYCAT.ddl`, `ddl/XTRNTYPE.ddl`, `ddl/XTRNTYCAT.ddl`, `dcl/DCLTRTYP.dcl`, `dcl/DCLTRCAT.dcl`, `ctl/DB2CREAT.ctl`, `ctl/DB2LTTYP.ctl`, `ctl/DB2LTCAT.ctl`, `jcl/CREADB21.jcl`. Targets: `db/migration/V1__create_tables.sql`, `db/migration/V2__seed_data.sql` (Flyway; the API loads them from `classpath:db/migration`, which the Maven build maps to this directory).

## Tables and columns

| DB2 object | DB2 type / constraint | COBOL host (DCLGEN) | PostgreSQL object | PostgreSQL type / constraint | Notes |
|---|---|---|---|---|---|
| `CARDDEMO.TRANSACTION_TYPE` | table in `CARDDEMO.CARDSPC1`, CCSID EBCDIC | `DCLTRANSACTION-TYPE` | `transaction_type` (default schema) | table | DB2 schema `CARDDEMO` → PostgreSQL default schema (configurable via `spring.flyway.schemas`) |
| `TR_TYPE` | `CHAR(2) NOT NULL` | `DCL-TR-TYPE PIC X(2)` | `tr_type` | `CHAR(2) COLLATE "C" NOT NULL` | kept CHAR (see padding) |
| `TR_DESCRIPTION` | `VARCHAR(50) NOT NULL` | `DCL-TR-DESCRIPTION` = `-LEN S9(4) COMP` + `-TEXT X(50)` | `tr_description` | `VARCHAR(50) NOT NULL` | length prefix → native varchar |
| `PRIMARY KEY(TR_TYPE)` + `XTRAN_TYPE` unique index ASC | PK | | `pk_transaction_type` | `PRIMARY KEY (tr_type)` | PG creates the unique btree index implicitly; separate `XTRAN_TYPE` not needed |
| `CARDDEMO.TRANSACTION_TYPE_CATEGORY` | table, CCSID EBCDIC | `DCLTRANSACTION-TYPE-CATEGORY` | `transaction_type_category` | table | |
| `TRC_TYPE_CODE` | `CHAR(2) NOT NULL` | `DCL-TRC-TYPE-CODE PIC X(2)` | `trc_type_code` | `CHAR(2) COLLATE "C" NOT NULL` | |
| `TRC_TYPE_CATEGORY` | `CHAR(4) NOT NULL` | `DCL-TRC-TYPE-CATEGORY PIC X(4)` | `trc_type_category` | `CHAR(4) COLLATE "C" NOT NULL` | |
| `TRC_CAT_DATA` | `VARCHAR(50) NOT NULL` | `-LEN` + `-TEXT X(50)` | `trc_cat_data` | `VARCHAR(50) NOT NULL` | |
| `PRIMARY KEY(TRC_TYPE_CODE,TRC_TYPE_CATEGORY)` + `X_TRAN_TYPE_CATG` | PK | | `pk_transaction_type_category` | `PRIMARY KEY (trc_type_code, trc_type_category)` | |
| `FOREIGN KEY TRC_TYPE_CODE ... REFERENCES TRANSACTION_TYPE (TR_TYPE) ON DELETE RESTRICT` | FK | | `fk_trc_type_code` | `FOREIGN KEY ... REFERENCES transaction_type (tr_type) ON DELETE RESTRICT` | DB2 SQLCODE −532 ↔ PG SQLSTATE `23503` (foreign_key_violation) |

## SQLCODE ↔ SQLSTATE mapping used by the API

| DB2 SQLCODE | Meaning | PostgreSQL SQLSTATE / JPA signal | API result |
|---|---|---|---|
| 0 | success | — | 200/201/204 |
| +100 | row not found | empty `Optional` / 0 rows updated | 404 (or INSERT for CTTU upsert) |
| −532 | delete restricted by FK | `23503` → `DataIntegrityViolationException` | 409 `CHILD_RECORDS_EXIST` |
| −803 | duplicate key on INSERT | `23505` → `DataIntegrityViolationException` | 409 `DUPLICATE_KEY` |
| −911 | deadlock / timeout, rollback | `40P01`, `40001`, `55P03` → `PessimisticLockingFailureException` / `CannotAcquireLockException` | 409 `LOCK_ERROR` |
| other < 0 | any other error | other `DataAccessException` | 500 `INTERNAL_ERROR` |

## CHAR padding decision

- DB2 `CHAR(n)` and PostgreSQL `CHAR(n)` (`bpchar`) are both fixed-length, blank-padded, and both ignore trailing blanks in comparisons. Keeping `CHAR` therefore preserves the source semantics of `WHERE TR_TYPE = :DCL-TR-TYPE` and of the keyset predicates `>=` / `<` with a `PIC X(2)` host variable.
- The application never stores a padded value: CTTU zero-pads the type code to 2 digits (1210-EDIT-TRANTYPE, `05`), and the CTLI type filter must be exactly 2 digits (1220-EDIT-TYPECD). The API normalises `{trType}` the same way, so `tr_type` always contains exactly two digits and padding never occurs in practice.
- When PostgreSQL returns a `CHAR(n)` value it includes the padding; the JPA entity maps it to `String` and the API trims nothing for codes (they are always full length). Category codes are 4 digits (`0001`).
- `COLLATE "C"` gives byte-order sorting so `ORDER BY tr_type` is deterministic and independent of the database locale. DB2 sorts by EBCDIC code page; for digit-only keys EBCDIC (`X'F0'..X'F9'`) and ASCII/UTF-8 orders are identical. If non-digit keys were ever introduced, EBCDIC (letters before digits) and "C" (digits before letters) orders would differ — not reachable through the edits in source.
- `VARCHAR(50)` descriptions are stored without padding. COBOL moves `TRIM(...)` into the 50-byte text field (9200 / 9600) and DB2 stores the host length; the API stores the trimmed value.
- `LIKE` is case-sensitive in both DB2 (EBCDIC, default) and PostgreSQL (`LIKE`, not `ILIKE`), so the description filter keeps source behaviour.

## Seed data

`V2__seed_data.sql` contains the 7 `TRANSACTION_TYPE` rows of `DB2LTTYP.ctl` and the 18 `TRANSACTION_TYPE_CATEGORY` rows of `DB2LTCAT.ctl` verbatim (including the source spelling `REVERAL` for type `06`). Every seeded type has at least one category, so deleting any seeded type is rejected by the FK (−532 path).

## Not migrated

`DB2CREAT.ctl` storage group / tablespace / CCSID EBCDIC clauses, `DB2FREE.ctl`, `DB2TEP41.ctl`, `DB2TIAD1.ctl` (plan/bind/grants), `REPROCT.ctl` (VSAM REPRO), and the TRANEXTR VSAM extract — see `docs/deviations.md`.
