-- Fixed-width VSAM masters; see docs/schema-mapping-vsam.md.

CREATE TABLE "account" (
    account_id NUMERIC(11,0) NOT NULL CHECK (account_id >= 0),
    active_status CHAR(1) NOT NULL,
    current_balance NUMERIC(12,2) NOT NULL,
    credit_limit NUMERIC(12,2) NOT NULL,
    cash_credit_limit NUMERIC(12,2) NOT NULL,
    open_date CHAR(10) NOT NULL,
    expiration_date CHAR(10) NOT NULL,
    reissue_date CHAR(10) NOT NULL,
    current_cycle_credit NUMERIC(12,2) NOT NULL,
    current_cycle_debit NUMERIC(12,2) NOT NULL,
    zip_code CHAR(10) NOT NULL,
    account_group_id CHAR(10) NOT NULL,
    filler CHAR(178) NOT NULL,
    PRIMARY KEY (account_id)
);

CREATE TABLE "card" (
    card_number CHAR(16) NOT NULL,
    account_id NUMERIC(11,0) NOT NULL CHECK (account_id >= 0),
    card_verification_value NUMERIC(3,0) NOT NULL CHECK (card_verification_value >= 0),
    embossed_name CHAR(50) NOT NULL,
    expiration_date CHAR(10) NOT NULL,
    active_status CHAR(1) NOT NULL,
    filler CHAR(59) NOT NULL,
    PRIMARY KEY (card_number)
);

CREATE TABLE "customer" (
    customer_id NUMERIC(9,0) NOT NULL CHECK (customer_id >= 0),
    first_name CHAR(25) NOT NULL,
    middle_name CHAR(25) NOT NULL,
    last_name CHAR(25) NOT NULL,
    address_line_1 CHAR(50) NOT NULL,
    address_line_2 CHAR(50) NOT NULL,
    address_line_3 CHAR(50) NOT NULL,
    state_code CHAR(2) NOT NULL,
    country_code CHAR(3) NOT NULL,
    zip_code CHAR(10) NOT NULL,
    phone_number_1 CHAR(15) NOT NULL,
    phone_number_2 CHAR(15) NOT NULL,
    social_security_number NUMERIC(9,0) NOT NULL CHECK (social_security_number >= 0),
    government_issued_id CHAR(20) NOT NULL,
    date_of_birth CHAR(10) NOT NULL,
    electronic_funds_transfer_account_id CHAR(10) NOT NULL,
    primary_cardholder_indicator CHAR(1) NOT NULL,
    fico_credit_score NUMERIC(3,0) NOT NULL CHECK (fico_credit_score >= 0),
    filler CHAR(168) NOT NULL,
    PRIMARY KEY (customer_id)
);

CREATE TABLE "card_xref" (
    card_number CHAR(16) NOT NULL,
    customer_id NUMERIC(9,0) NOT NULL CHECK (customer_id >= 0),
    account_id NUMERIC(11,0) NOT NULL CHECK (account_id >= 0),
    filler CHAR(14) NOT NULL,
    PRIMARY KEY (card_number)
);

CREATE TABLE "transaction_category_balance" (
    account_id NUMERIC(11,0) NOT NULL CHECK (account_id >= 0),
    transaction_type_code CHAR(2) NOT NULL,
    transaction_category_code NUMERIC(4,0) NOT NULL CHECK (transaction_category_code >= 0),
    balance NUMERIC(11,2) NOT NULL,
    filler CHAR(22) NOT NULL,
    PRIMARY KEY (account_id, transaction_type_code, transaction_category_code)
);

CREATE TABLE "disclosure_group" (
    account_group_id CHAR(10) NOT NULL,
    transaction_type_code CHAR(2) NOT NULL,
    transaction_category_code NUMERIC(4,0) NOT NULL CHECK (transaction_category_code >= 0),
    interest_rate NUMERIC(6,2) NOT NULL,
    filler CHAR(28) NOT NULL,
    PRIMARY KEY (account_group_id, transaction_type_code, transaction_category_code)
);

CREATE TABLE "transaction" (
    transaction_id CHAR(16) NOT NULL,
    transaction_type_code CHAR(2) NOT NULL,
    transaction_category_code NUMERIC(4,0) NOT NULL CHECK (transaction_category_code >= 0),
    source CHAR(10) NOT NULL,
    description CHAR(100) NOT NULL,
    amount NUMERIC(11,2) NOT NULL,
    merchant_id NUMERIC(9,0) NOT NULL CHECK (merchant_id >= 0),
    merchant_name CHAR(50) NOT NULL,
    merchant_city CHAR(50) NOT NULL,
    merchant_zip_code CHAR(10) NOT NULL,
    card_number CHAR(16) NOT NULL,
    origination_timestamp CHAR(26) NOT NULL,
    processed_timestamp CHAR(26) NOT NULL,
    filler CHAR(20) NOT NULL,
    PRIMARY KEY (transaction_id)
);

CREATE TABLE "tran_type" (
    transaction_type_code CHAR(2) NOT NULL,
    description CHAR(50) NOT NULL,
    filler CHAR(8) NOT NULL,
    PRIMARY KEY (transaction_type_code)
);

CREATE TABLE "tran_category" (
    transaction_type_code CHAR(2) NOT NULL,
    transaction_category_code NUMERIC(4,0) NOT NULL CHECK (transaction_category_code >= 0),
    description CHAR(50) NOT NULL,
    filler CHAR(4) NOT NULL,
    PRIMARY KEY (transaction_type_code, transaction_category_code)
);
