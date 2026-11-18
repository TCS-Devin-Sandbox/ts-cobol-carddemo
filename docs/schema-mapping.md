# Schema mapping — DB2 → PostgreSQL 15

Source DDL: `app/app-transaction-type-db2/ddl/TRNTYPE.ddl`, `TRNTYCAT.ddl`; DCLGEN `dcl/DCLTRTYP.dcl`, `DCLTRCAT.dcl`. Target: `db/migration/V1__create_tables.sql` (Flyway).

| DB2 object / column | DB2 type | PostgreSQL | Notes |
|---|---|---|---|
| CARDDEMO.TRANSACTION_TYPE | table | `transaction_type` | schema prefix dropped; default `public` schema |
| TR_TYPE | CHAR(2) NOT NULL | `tr_type CHAR(2) NOT NULL` | PK `pk_transaction_type` |
| TR_DESCRIPTION | VARCHAR(50) NOT NULL (DCLGEN: S9(4) COMP length + X(50)) | `tr_description VARCHAR(50) NOT NULL` | trimmed on write (COBOL `FUNCTION TRIM`) |
| CARDDEMO.TRANSACTION_TYPE_CATEGORY | table | `transaction_type_category` | |
| TRC_TYPE_CODE | CHAR(2) NOT NULL | `trc_type_code CHAR(2) NOT NULL` | PK part 1; FK |
| TRC_TYPE_CATEGORY | CHAR(4) NOT NULL | `trc_type_category CHAR(4) NOT NULL` | PK part 2 |
| TRC_CAT_DATA | VARCHAR(50) NOT NULL | `trc_cat_data VARCHAR(50) NOT NULL` | |
| PRIMARY KEY(TR_TYPE) | unique index | `CONSTRAINT pk_transaction_type PRIMARY KEY` | violation: DB2 −803 → PG SQLSTATE 23505 |
| PRIMARY KEY(TRC_TYPE_CODE,TRC_TYPE_CATEGORY) | unique index | `pk_transaction_type_category` | |
| FOREIGN KEY … ON DELETE RESTRICT | RI | `fk_trc_type_code … ON DELETE RESTRICT` | violation: DB2 −532 → PG SQLSTATE 23503 |
| Seed data DB2LTTYP.ctl / DB2LTCAT.ctl | INSERT … SELECT … UNION ALL | `V2__seed_data.sql` multi-row INSERT | 7 types, 18 categories, values unchanged (incl. source typo 'REVERAL') |

## SQLCODE ↔ SQLSTATE

| DB2 SQLCODE | Meaning | PostgreSQL SQLSTATE | API mapping |
|---|---|---|---|
| 0 | success | 00000 | 200/201/204 |
| +100 | no row | (empty result / 0 rows updated) | 404 or upsert insert |
| −532 | delete restricted by FK | 23503 foreign_key_violation | 409 `FK_RESTRICT` |
| −803 | duplicate key | 23505 unique_violation | 409 `DUPLICATE_KEY` |
| −911 | deadlock / timeout | 40P01 deadlock_detected, 40001, 55P03 lock_not_available | 409 `LOCK_ERROR` |
| other < 0 | failure | any other | 500 |

## CHAR padding decision
Keys stay `CHAR(n)` to keep DB2 fixed-length semantics: PostgreSQL blank-pads CHAR and ignores trailing blanks in comparisons, exactly like DB2. Because the application always normalises the type code to two digits (COBOL 1210-EDIT-TRANTYPE zero-pads, e.g. `1` → `01`), no padded values occur in practice. JPA maps the columns as `String` with `columnDefinition = "bpchar"`-compatible `@Column(length = 2)`; `ORDER BY tr_type` gives the same collation result as DB2 EBCDIC for the numeric-only key domain ('00'–'99'). Descriptions remain VARCHAR and are trimmed before storage.
