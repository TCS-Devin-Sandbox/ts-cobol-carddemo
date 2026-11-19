-- Source: app/app-transaction-type-db2/ctl/DB2CREAT.ctl, ddl/TRNTYPE.ddl, ddl/TRNTYCAT.ddl
-- DB2 CARDDEMO.TRANSACTION_TYPE / CARDDEMO.TRANSACTION_TYPE_CATEGORY -> PostgreSQL 15.
-- CHAR(2)/CHAR(4) kept as fixed-length (bpchar). Keys are always normalized to 2 digits by
-- the API (zero-padded, COTRTUPC 1210-EDIT-TRANTYPE) so blank padding never occurs in practice;
-- bpchar comparison ignores trailing blanks, matching DB2 CHAR semantics. See docs/schema-mapping.md.

CREATE TABLE transaction_type (
    tr_type        CHAR(2)     NOT NULL,
    tr_description VARCHAR(50) NOT NULL,
    CONSTRAINT pk_transaction_type PRIMARY KEY (tr_type)
);

CREATE TABLE transaction_type_category (
    trc_type_code     CHAR(2)     NOT NULL,
    trc_type_category CHAR(4)     NOT NULL,
    trc_cat_data      VARCHAR(50) NOT NULL,
    CONSTRAINT pk_transaction_type_category PRIMARY KEY (trc_type_code, trc_type_category),
    CONSTRAINT fk_trc_type_code FOREIGN KEY (trc_type_code)
        REFERENCES transaction_type (tr_type) ON DELETE RESTRICT
);
