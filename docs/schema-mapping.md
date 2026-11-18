# Phase 3 — Schema mapping: DB2 (CARDDEMO) → PostgreSQL 15

Sources: `ctl/DB2CREAT.ctl` (authoritative create script run by `CREADB21`), `ddl/TRNTYPE.ddl`, `ddl/TRNTYCAT.ddl`, `ddl/XTRNTYPE.ddl`, `ddl/XTRNTYCAT.ddl`, host structures `dcl/DCLTRTYP.dcl`, `dcl/DCLTRCAT.dcl`.
Targets: `db/migration/V1__create_tables.sql`, `db/migration/V2__seed_data.sql` (Flyway; packaged onto the API classpath as `db/migration`).

## 1. Objects

| DB2 object | PostgreSQL object | Notes |
|---|---|---|
| Database `CARDDEMO`, stogroup `AWST1STG`, BP0, `CCSID EBCDIC` | Database `carddemo` (UTF-8) | Physical storage clauses dropped. EBCDIC → UTF-8: seed data is plain A–Z/0–9/space, no conversion issues. |
| Tablespaces `CARDSPC1`, `CARDSTTC` (SEGSIZE 4, LOCKSIZE TABLE) | default tablespace | PostgreSQL uses row-level locks (MVCC) instead of table locks — fewer -911-style conflicts. |
| Schema/qualifier `CARDDEMO.` | `public` schema (unqualified) | Configurable via `spring.flyway.schemas` if required. |
| `CARDDEMO.TRANSACTION_TYPE` | `transaction_type` | |
| `CARDDEMO.TRANSACTION_TYPE_CATEGORY` | `transaction_type_category` | |
| Unique index `CARDDEMO.XTRAN_TYPE (TR_TYPE ASC)` | implicit index of `pk_transaction_type` | PK index serves the keyset scans in both directions (B-tree is bidirectional). |
| Unique index `CARDDEMO.X_TRAN_TYPE_CATG (TRC_TYPE_CODE, TRC_TYPE_CATEGORY)` | implicit index of `pk_transaction_type_category` | Leading column also serves the FK check on parent delete. |
| `ALTER TABLE … FOREIGN KEY (TRC_TYPE_CODE) REFERENCES TRANSACTION_TYPE (TR_TYPE) ON DELETE RESTRICT` | `fk_trc_type_code … ON DELETE RESTRICT` | Violation: DB2 SQLCODE -532 ⇔ PG SQLSTATE `23503`. |
| `GRANT … TO PUBLIC` | not migrated | Access is controlled by the API's DB user + Spring Security (admin role). |

## 2. Columns

| Table | DB2 column | DB2 type | COBOL host var (DCL) | PostgreSQL column | PG type | JPA field | Notes |
|---|---|---|---|---|---|---|---|
| TRANSACTION_TYPE | TR_TYPE | CHAR(2) NOT NULL, PK | `DCL-TR-TYPE PIC X(2)` | tr_type | CHAR(2) NOT NULL, PK | `TransactionType.trType : String` | Padding decision below |
| TRANSACTION_TYPE | TR_DESCRIPTION | VARCHAR(50) NOT NULL | `DCL-TR-DESCRIPTION` (LEN S9(4) COMP + TEXT X(50)) | tr_description | VARCHAR(50) NOT NULL | `trDescription : String` | Stored trimmed (deviation D-08) |
| TRANSACTION_TYPE_CATEGORY | TRC_TYPE_CODE | CHAR(2) NOT NULL, PK1, FK | `DCL-TRC-TYPE-CODE X(2)` | trc_type_code | CHAR(2) NOT NULL | `TransactionTypeCategory.id.trcTypeCode` | |
| TRANSACTION_TYPE_CATEGORY | TRC_TYPE_CATEGORY | CHAR(4) NOT NULL, PK2 | `DCL-TRC-TYPE-CATEGORY X(4)` | trc_type_category | CHAR(4) NOT NULL | `id.trcTypeCategory` | |
| TRANSACTION_TYPE_CATEGORY | TRC_CAT_DATA | VARCHAR(50) NOT NULL | `DCL-TRC-CAT-DATA` | trc_cat_data | VARCHAR(50) NOT NULL | `trcCatData` | The task brief lists this column without NOT NULL; the DB2 DDL declares NOT NULL, which is kept. |

## 3. CHAR(2) padding decision

- **Kept as `CHAR(2)`** (not `VARCHAR`/`SMALLINT`) to preserve the DB2 key domain and its string ordering (`'01' < '02' < … < '10'`), which drives the keyset paging (`>=`, `<`, `ORDER BY`).
- PostgreSQL `bpchar` blank-pads shorter values and ignores trailing blanks in comparisons, matching DB2 CHAR semantics.
- The API normalizes every key to exactly two digits before it reaches SQL (COTRTUPC `1210-EDIT-TRANTYPE`: `NUMVAL` → `PIC 99`), and filter keys must already be two digits (COTRTLIC `1220-EDIT-TYPECD`). So no padded value like `'1 '` is ever stored. A one-character key would otherwise sort before `'01'` (`'1 '` > `'09'` lexically) and break paging.
- Hibernate maps the columns with `@JdbcTypeCode(SqlTypes.CHAR)` and `length = 2` / `4` so `ddl-auto=validate` passes.
- No `CHECK (tr_type ~ '^[0-9]{2}$')` is added because DB2 has none. The application layer enforces it. Adding one is a recommended hardening step once batch loaders are migrated.

## 4. SQLCODE ↔ SQLSTATE

| DB2 SQLCODE | Meaning | PostgreSQL SQLSTATE | Spring exception | API mapping |
|---|---|---|---|---|
| 0 | success | 00000 | — | 2xx |
| +100 | no row | (0 rows / empty result) | `Optional.empty()` / update count 0 | 404 `NOT_FOUND` |
| -532 | delete restricted by RI | 23503 `foreign_key_violation` | `DataIntegrityViolationException` (cause `PSQLException` 23503) | 409 `FK_RESTRICT` |
| -803 | duplicate key | 23505 `unique_violation` | `DataIntegrityViolationException` (23505) / `DuplicateKeyException` | 409 `DUPLICATE_KEY` |
| -911 / -913 | deadlock / timeout | 40P01 `deadlock_detected`, 55P03 `lock_not_available`, 40001 | `PessimisticLockingFailureException` / `CannotAcquireLockException` | 409 `LOCK_CONFLICT` |
| other < 0 | error | any | `DataAccessException` | 500 `DB_ERROR` |

## 5. Seed data (CREADB21)

| Step | Control member | Target | Rows |
|---|---|---|---|
| CRCRDDB (DSNTIAD) | DB2CREAT | DDL | — |
| LDTTYPE/RUNTEP2 (DSNTEP4) | DB2LTTYP | transaction_type | 7 (`01 PURCHASE`, `02 PAYMENT`, `03 CREDIT`, `04 AUTHORIZATION`, `05 REFUND`, `06 REVERAL` [source spelling kept], `07 ADJUSTMENT`) |
| LDTCCAT (DSNTEP4) | DB2LTCAT | transaction_type_category | 18 (types 01–07) |

Every seeded type has at least one category, so deleting any seeded type is rejected (FK RESTRICT), as in DB2.

Verified locally: both migrations apply cleanly on `postgres:15-alpine`; 7 + 18 rows; `DELETE FROM transaction_type WHERE tr_type='01'` fails with `fk_trc_type_code` (SQLSTATE 23503).
