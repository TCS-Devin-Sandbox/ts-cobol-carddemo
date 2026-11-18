-- CardDemo transaction type tables (PostgreSQL 15).
-- Source: app/app-transaction-type-db2/ddl/TRNTYPE.ddl, TRNTYCAT.ddl, XTRNTYPE.ddl, XTRNTYCAT.ddl
-- Code columns are kept as CHAR (blank-padded) to match DB2 CHAR semantics; see docs/schema-mapping.md.

CREATE TABLE transaction_type (
    tr_type        CHAR(2) COLLATE "C" NOT NULL,
    tr_description VARCHAR(50) NOT NULL,
    CONSTRAINT pk_transaction_type PRIMARY KEY (tr_type)
);

CREATE TABLE transaction_type_category (
    trc_type_code     CHAR(2) COLLATE "C" NOT NULL,
    trc_type_category CHAR(4) COLLATE "C" NOT NULL,
    trc_cat_data      VARCHAR(50) NOT NULL,
    CONSTRAINT pk_transaction_type_category PRIMARY KEY (trc_type_code, trc_type_category),
    CONSTRAINT fk_trc_type_code FOREIGN KEY (trc_type_code)
        REFERENCES transaction_type (tr_type) ON DELETE RESTRICT
);
