-- INT-01 pilot: private, disposable tables (not the production schema; INT-02 owns that).
-- Column names and types follow INT-MAP v1.0; every run is isolated by run_id.
CREATE SCHEMA IF NOT EXISTS interest_pilot;

CREATE TABLE IF NOT EXISTS interest_pilot.account_input (
  run_id            UUID          NOT NULL,
  acct_id           VARCHAR(11)   COLLATE "C" NOT NULL,
  active_status     VARCHAR(1)    COLLATE "C" NOT NULL,
  current_balance   NUMERIC(12,2) NOT NULL,
  credit_limit      NUMERIC(12,2) NOT NULL,
  cash_credit_limit NUMERIC(12,2) NOT NULL,
  open_date         VARCHAR(10)   COLLATE "C" NOT NULL,
  expiration_date   VARCHAR(10)   COLLATE "C" NOT NULL,
  reissue_date      VARCHAR(10)   COLLATE "C" NOT NULL,
  cycle_credit      NUMERIC(12,2) NOT NULL,
  cycle_debit       NUMERIC(12,2) NOT NULL,
  addr_zip          VARCHAR(10)   COLLATE "C" NOT NULL,
  group_id          VARCHAR(10)   COLLATE "C" NOT NULL,
  filler_raw        BYTEA         NOT NULL CHECK (octet_length(filler_raw) = 178),
  raw_record        BYTEA         NOT NULL CHECK (octet_length(raw_record) = 300),
  PRIMARY KEY (run_id, acct_id)
);

CREATE TABLE IF NOT EXISTS interest_pilot.card_xref_input (
  run_id      UUID        NOT NULL,
  card_number VARCHAR(16) COLLATE "C" NOT NULL,
  customer_id VARCHAR(9)  COLLATE "C" NOT NULL,
  acct_id     VARCHAR(11) COLLATE "C" NOT NULL,
  filler_raw  BYTEA       NOT NULL CHECK (octet_length(filler_raw) = 14),
  raw_record  BYTEA       NOT NULL CHECK (octet_length(raw_record) = 50),
  PRIMARY KEY (run_id, card_number)
);
CREATE INDEX IF NOT EXISTS card_xref_input_acct ON interest_pilot.card_xref_input (run_id, acct_id);

CREATE TABLE IF NOT EXISTS interest_pilot.category_balance_input (
  run_id        UUID          NOT NULL,
  legacy_key    BYTEA         NOT NULL CHECK (octet_length(legacy_key) = 17),
  acct_id       VARCHAR(11)   COLLATE "C" NOT NULL,
  txn_type      VARCHAR(2)    COLLATE "C" NOT NULL,
  category_code VARCHAR(4)    COLLATE "C" NOT NULL,
  balance       NUMERIC(11,2) NOT NULL,
  filler_raw    BYTEA         NOT NULL CHECK (octet_length(filler_raw) = 22),
  raw_record    BYTEA         NOT NULL CHECK (octet_length(raw_record) = 50),
  PRIMARY KEY (run_id, acct_id, txn_type, category_code),
  UNIQUE (run_id, legacy_key)
);

CREATE TABLE IF NOT EXISTS interest_pilot.disclosure_rate_input (
  run_id              UUID         NOT NULL,
  legacy_key          BYTEA        NOT NULL CHECK (octet_length(legacy_key) = 16),
  group_id            VARCHAR(10)  COLLATE "C" NOT NULL,
  txn_type            VARCHAR(2)   COLLATE "C" NOT NULL,
  category_code       VARCHAR(4)   COLLATE "C" NOT NULL,
  annual_percent_rate NUMERIC(6,2) NOT NULL,
  filler_raw          BYTEA        NOT NULL CHECK (octet_length(filler_raw) = 28),
  raw_record          BYTEA        NOT NULL CHECK (octet_length(raw_record) = 50),
  PRIMARY KEY (run_id, group_id, txn_type, category_code),
  UNIQUE (run_id, legacy_key)
);

CREATE TABLE IF NOT EXISTS interest_pilot.interest_tx (
  run_id        UUID          NOT NULL,
  emit_ordinal  BIGINT        NOT NULL CHECK (emit_ordinal > 0),
  txn_id        VARCHAR(16)   COLLATE "C" NOT NULL,
  txn_type      VARCHAR(2)    COLLATE "C" NOT NULL,
  category_code VARCHAR(4)    COLLATE "C" NOT NULL,
  source        VARCHAR(10)   COLLATE "C" NOT NULL,
  description   VARCHAR(100)  COLLATE "C" NOT NULL,
  amount        NUMERIC(11,2) NOT NULL,
  merchant_id   VARCHAR(9)    COLLATE "C" NOT NULL,
  merchant_name VARCHAR(50)   COLLATE "C" NOT NULL,
  merchant_city VARCHAR(50)   COLLATE "C" NOT NULL,
  merchant_zip  VARCHAR(10)   COLLATE "C" NOT NULL,
  card_number   VARCHAR(16)   COLLATE "C" NOT NULL,
  orig_ts       VARCHAR(26)   COLLATE "C" NOT NULL,
  proc_ts       VARCHAR(26)   COLLATE "C" NOT NULL,
  filler_raw    BYTEA         NOT NULL CHECK (octet_length(filler_raw) = 20),
  raw_record    BYTEA         NOT NULL CHECK (octet_length(raw_record) = 350),
  PRIMARY KEY (run_id, emit_ordinal)
);
CREATE INDEX IF NOT EXISTS interest_tx_txn_id ON interest_pilot.interest_tx (run_id, txn_id);

CREATE TABLE IF NOT EXISTS interest_pilot.account_delta (
  run_id              UUID          NOT NULL,
  acct_id             VARCHAR(11)   COLLATE "C" NOT NULL,
  old_current_balance NUMERIC(12,2) NOT NULL,
  new_current_balance NUMERIC(12,2) NOT NULL,
  old_cycle_credit    NUMERIC(12,2) NOT NULL,
  new_cycle_credit    NUMERIC(12,2) NOT NULL,
  old_cycle_debit     NUMERIC(12,2) NOT NULL,
  new_cycle_debit     NUMERIC(12,2) NOT NULL,
  source_fingerprint  BYTEA         NOT NULL CHECK (octet_length(source_fingerprint) = 32),
  PRIMARY KEY (run_id, acct_id)
);

CREATE TABLE IF NOT EXISTS interest_pilot.run_checkpoint (
  run_id          UUID        PRIMARY KEY,
  next_legacy_key BYTEA,
  last_acct_id    VARCHAR(11) COLLATE "C" NOT NULL,
  last_ordinal    BIGINT      NOT NULL
);

-- Restricted parity SYSOUT for STEP15 (ordered DISPLAY lines), kept with the run's outputs.
CREATE TABLE IF NOT EXISTS interest_pilot.sysout_line (
  run_id      UUID   NOT NULL,
  line_number BIGINT NOT NULL,
  text        TEXT   COLLATE "C" NOT NULL,
  PRIMARY KEY (run_id, line_number)
);
