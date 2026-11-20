-- INT-02 / INT-ARCH A1,A3; INT-MAP v1.0. Apply once, in version order.
CREATE SCHEMA interest;
CREATE SCHEMA interest_batch;
CREATE ROLE interest_intake NOLOGIN;
CREATE ROLE interest_compute NOLOGIN;
CREATE ROLE interest_publish NOLOGIN;
CREATE ROLE interest_repository NOLOGIN;
REVOKE ALL ON SCHEMA interest, interest_batch FROM PUBLIC;
CREATE TABLE interest.snapshot (
 snapshot_id UUID PRIMARY KEY,
 manifest JSONB NOT NULL,
 codec_profile TEXT NOT NULL,
 state TEXT NOT NULL CHECK (state IN ('LOADING','SEALED','HELD')),
 created_at TIMESTAMP NOT NULL
);
CREATE TABLE interest.run (
 run_id UUID PRIMARY KEY,
 snapshot_id UUID NOT NULL REFERENCES interest.snapshot,
 parm VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(parm)=10),
 cycle TEXT NOT NULL,
 feed TEXT NOT NULL,
 mode TEXT NOT NULL CHECK (mode IN ('shadow','live')),
 build_revision TEXT NOT NULL,
 rule_revision TEXT NOT NULL,
 clock_profile TEXT NOT NULL,
 state TEXT NOT NULL CHECK (state IN ('READY','COMPUTING','COMPUTED','RELEASED','FAILED','HELD'))
);
CREATE TABLE interest.account_input (
 snapshot_id UUID NOT NULL REFERENCES interest.snapshot,
 acct_id VARCHAR(11) COLLATE "C" NOT NULL CHECK (acct_id ~ '^[0-9]{11}$'),
 active_status VARCHAR(1) COLLATE "C" NOT NULL CHECK (length(active_status) = 1),
 current_balance NUMERIC(12,2) NOT NULL,
 credit_limit NUMERIC(12,2) NOT NULL,
 cash_credit_limit NUMERIC(12,2) NOT NULL,
 open_date VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(open_date) = 10),
 expiration_date VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(expiration_date) = 10),
 reissue_date VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(reissue_date) = 10),
 cycle_credit NUMERIC(12,2) NOT NULL,
 cycle_debit NUMERIC(12,2) NOT NULL,
 addr_zip VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(addr_zip) = 10),
 group_id VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(group_id) = 10),
 filler_raw BYTEA NOT NULL CHECK (octet_length(filler_raw) = 178),
 raw_record BYTEA NOT NULL CHECK (octet_length(raw_record) = 300),
 PRIMARY KEY (snapshot_id, acct_id)
);
CREATE TABLE interest.card_xref_input (
 snapshot_id UUID NOT NULL REFERENCES interest.snapshot,
 card_number VARCHAR(16) COLLATE "C" NOT NULL CHECK (length(card_number) = 16),
 customer_id VARCHAR(9) COLLATE "C" NOT NULL CHECK (customer_id ~ '^[0-9]{9}$'),
 acct_id VARCHAR(11) COLLATE "C" NOT NULL CHECK (acct_id ~ '^[0-9]{11}$'),
 filler_raw BYTEA NOT NULL CHECK (octet_length(filler_raw) = 14),
 raw_record BYTEA NOT NULL CHECK (octet_length(raw_record) = 50),
 aix_first BOOLEAN NOT NULL,
 aix_evidence TEXT NOT NULL,
 PRIMARY KEY (snapshot_id, card_number)
);
CREATE TABLE interest.category_balance_input (
 snapshot_id UUID NOT NULL REFERENCES interest.snapshot,
 legacy_key BYTEA NOT NULL CHECK (octet_length(legacy_key) = 17),
 acct_id VARCHAR(11) COLLATE "C" NOT NULL CHECK (acct_id ~ '^[0-9]{11}$'),
 txn_type VARCHAR(2) COLLATE "C" NOT NULL CHECK (length(txn_type) = 2),
 category_code VARCHAR(4) COLLATE "C" NOT NULL CHECK (category_code ~ '^[0-9]{4}$'),
 balance NUMERIC(11,2) NOT NULL,
 filler_raw BYTEA NOT NULL CHECK (octet_length(filler_raw) = 22),
 raw_record BYTEA NOT NULL CHECK (octet_length(raw_record) = 50),
 PRIMARY KEY (snapshot_id, acct_id, txn_type, category_code),
 UNIQUE (snapshot_id, legacy_key)
);
CREATE TABLE interest.disclosure_rate_input (
 snapshot_id UUID NOT NULL REFERENCES interest.snapshot,
 legacy_key BYTEA NOT NULL CHECK (octet_length(legacy_key) = 16),
 group_id VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(group_id) = 10),
 txn_type VARCHAR(2) COLLATE "C" NOT NULL CHECK (length(txn_type) = 2),
 category_code VARCHAR(4) COLLATE "C" NOT NULL CHECK (category_code ~ '^[0-9]{4}$'),
 annual_percent_rate NUMERIC(6,2) NOT NULL,
 filler_raw BYTEA NOT NULL CHECK (octet_length(filler_raw) = 28),
 raw_record BYTEA NOT NULL CHECK (octet_length(raw_record) = 50),
 PRIMARY KEY (snapshot_id, group_id, txn_type, category_code),
 UNIQUE (snapshot_id, legacy_key)
);
CREATE TABLE interest.interest_tx (
 run_id UUID NOT NULL REFERENCES interest.run,
 emit_ordinal BIGINT NOT NULL CHECK (emit_ordinal > 0),
 txn_id VARCHAR(16) COLLATE "C" NOT NULL CHECK (length(txn_id) = 16),
 txn_type VARCHAR(2) COLLATE "C" NOT NULL CHECK (length(txn_type) = 2),
 category_code VARCHAR(4) COLLATE "C" NOT NULL CHECK (category_code ~ '^[0-9]{4}$'),
 source VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(source) = 10),
 description VARCHAR(100) COLLATE "C" NOT NULL CHECK (length(description) = 100),
 amount NUMERIC(11,2) NOT NULL,
 merchant_id VARCHAR(9) COLLATE "C" NOT NULL CHECK (merchant_id ~ '^[0-9]{9}$'),
 merchant_name VARCHAR(50) COLLATE "C" NOT NULL CHECK (length(merchant_name) = 50),
 merchant_city VARCHAR(50) COLLATE "C" NOT NULL CHECK (length(merchant_city) = 50),
 merchant_zip VARCHAR(10) COLLATE "C" NOT NULL CHECK (length(merchant_zip) = 10),
 card_number VARCHAR(16) COLLATE "C" NOT NULL CHECK (length(card_number) = 16),
 orig_ts VARCHAR(26) COLLATE "C" NOT NULL CHECK (length(orig_ts) = 26),
 proc_ts VARCHAR(26) COLLATE "C" NOT NULL CHECK (length(proc_ts) = 26),
 filler_raw BYTEA NOT NULL CHECK (octet_length(filler_raw) = 20),
 raw_record BYTEA NOT NULL CHECK (octet_length(raw_record) = 350),
 PRIMARY KEY (run_id, emit_ordinal)
);
CREATE TABLE interest.account_delta (
 run_id UUID NOT NULL REFERENCES interest.run,
 acct_id VARCHAR(11) COLLATE "C" NOT NULL CHECK (acct_id ~ '^[0-9]{11}$'),
 old_current_balance NUMERIC(12,2) NOT NULL,
 new_current_balance NUMERIC(12,2) NOT NULL,
 old_cycle_credit NUMERIC(12,2) NOT NULL,
 new_cycle_credit NUMERIC(12,2) NOT NULL,
 old_cycle_debit NUMERIC(12,2) NOT NULL,
 new_cycle_debit NUMERIC(12,2) NOT NULL,
 source_fingerprint BYTEA NOT NULL CHECK (octet_length(source_fingerprint) = 32),
 PRIMARY KEY (run_id, acct_id)
);
CREATE INDEX card_xref_account ON interest.card_xref_input(snapshot_id, acct_id);
CREATE UNIQUE INDEX card_xref_aix_first ON interest.card_xref_input(snapshot_id, acct_id) WHERE aix_first;
CREATE INDEX interest_tx_id ON interest.interest_tx(run_id, txn_id);
CREATE TABLE interest.run_checkpoint (
 run_id UUID PRIMARY KEY REFERENCES interest.run,
 next_legacy_key BYTEA CHECK (octet_length(next_legacy_key)=17),
 last_acct_id VARCHAR(11) COLLATE "C" NOT NULL CHECK (last_acct_id ~ '^[0-9]{11}$'),
 last_ordinal BIGINT NOT NULL CHECK (last_ordinal>=0)
);
CREATE TABLE interest.publication (
 run_id UUID PRIMARY KEY REFERENCES interest.run,
 state TEXT NOT NULL CHECK (state IN ('PRIVATE','PREPARED','HELD','RECEIVED')),
 payload_checksum BYTEA NOT NULL CHECK (octet_length(payload_checksum)=32),
 record_count BIGINT NOT NULL CHECK (record_count>=0),
 destination TEXT NOT NULL,
 prior_generation TEXT NOT NULL,
 current_generation TEXT NOT NULL,
 delta_preconditions JSONB NOT NULL,
 operation_id TEXT NOT NULL,
 receipt JSONB
);
GRANT USAGE ON SCHEMA interest TO interest_intake, interest_compute, interest_publish;
GRANT SELECT ON ALL TABLES IN SCHEMA interest TO interest_intake, interest_compute, interest_publish;
GRANT INSERT, UPDATE ON interest.snapshot, interest.account_input, interest.card_xref_input, interest.category_balance_input, interest.disclosure_rate_input TO interest_intake;
GRANT INSERT, UPDATE ON interest.run, interest.interest_tx, interest.account_delta, interest.run_checkpoint TO interest_compute;
GRANT INSERT, UPDATE ON interest.publication TO interest_publish;
GRANT UPDATE(state) ON interest.run TO interest_publish;
