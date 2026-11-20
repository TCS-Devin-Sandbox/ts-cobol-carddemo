-- Fixed-width VSAM masters; see docs/schema-mapping-vsam.md.

CREATE TABLE "account" (
    acct_id NUMERIC(11,0) NOT NULL CHECK (acct_id >= 0),
    acct_active_status CHAR(1) NOT NULL,
    acct_curr_bal NUMERIC(12,2) NOT NULL,
    acct_credit_limit NUMERIC(12,2) NOT NULL,
    acct_cash_credit_limit NUMERIC(12,2) NOT NULL,
    acct_open_date CHAR(10) NOT NULL,
    acct_expiraion_date CHAR(10) NOT NULL,
    acct_reissue_date CHAR(10) NOT NULL,
    acct_curr_cyc_credit NUMERIC(12,2) NOT NULL,
    acct_curr_cyc_debit NUMERIC(12,2) NOT NULL,
    acct_addr_zip CHAR(10) NOT NULL,
    acct_group_id CHAR(10) NOT NULL,
    filler CHAR(178) NOT NULL,
    PRIMARY KEY (acct_id)
);

CREATE TABLE "card" (
    card_num CHAR(16) NOT NULL,
    card_acct_id NUMERIC(11,0) NOT NULL CHECK (card_acct_id >= 0),
    card_cvv_cd NUMERIC(3,0) NOT NULL CHECK (card_cvv_cd >= 0),
    card_embossed_name CHAR(50) NOT NULL,
    card_expiraion_date CHAR(10) NOT NULL,
    card_active_status CHAR(1) NOT NULL,
    filler CHAR(59) NOT NULL,
    PRIMARY KEY (card_num)
);

CREATE TABLE "customer" (
    cust_id NUMERIC(9,0) NOT NULL CHECK (cust_id >= 0),
    cust_first_name CHAR(25) NOT NULL,
    cust_middle_name CHAR(25) NOT NULL,
    cust_last_name CHAR(25) NOT NULL,
    cust_addr_line_1 CHAR(50) NOT NULL,
    cust_addr_line_2 CHAR(50) NOT NULL,
    cust_addr_line_3 CHAR(50) NOT NULL,
    cust_addr_state_cd CHAR(2) NOT NULL,
    cust_addr_country_cd CHAR(3) NOT NULL,
    cust_addr_zip CHAR(10) NOT NULL,
    cust_phone_num_1 CHAR(15) NOT NULL,
    cust_phone_num_2 CHAR(15) NOT NULL,
    cust_ssn NUMERIC(9,0) NOT NULL CHECK (cust_ssn >= 0),
    cust_govt_issued_id CHAR(20) NOT NULL,
    cust_dob_yyyy_mm_dd CHAR(10) NOT NULL,
    cust_eft_account_id CHAR(10) NOT NULL,
    cust_pri_card_holder_ind CHAR(1) NOT NULL,
    cust_fico_credit_score NUMERIC(3,0) NOT NULL CHECK (cust_fico_credit_score >= 0),
    filler CHAR(168) NOT NULL,
    PRIMARY KEY (cust_id)
);

CREATE TABLE "card_xref" (
    xref_card_num CHAR(16) NOT NULL,
    xref_cust_id NUMERIC(9,0) NOT NULL CHECK (xref_cust_id >= 0),
    xref_acct_id NUMERIC(11,0) NOT NULL CHECK (xref_acct_id >= 0),
    filler CHAR(14) NOT NULL,
    PRIMARY KEY (xref_card_num)
);

CREATE TABLE "tran_cat_bal" (
    trancat_acct_id NUMERIC(11,0) NOT NULL CHECK (trancat_acct_id >= 0),
    trancat_type_cd CHAR(2) NOT NULL,
    trancat_cd NUMERIC(4,0) NOT NULL CHECK (trancat_cd >= 0),
    tran_cat_bal NUMERIC(11,2) NOT NULL,
    filler CHAR(22) NOT NULL,
    PRIMARY KEY (trancat_acct_id, trancat_type_cd, trancat_cd)
);

CREATE TABLE "disclosure_group" (
    dis_acct_group_id CHAR(10) NOT NULL,
    dis_tran_type_cd CHAR(2) NOT NULL,
    dis_tran_cat_cd NUMERIC(4,0) NOT NULL CHECK (dis_tran_cat_cd >= 0),
    dis_int_rate NUMERIC(6,2) NOT NULL,
    filler CHAR(28) NOT NULL,
    PRIMARY KEY (dis_acct_group_id, dis_tran_type_cd, dis_tran_cat_cd)
);

CREATE TABLE "transaction" (
    tran_id CHAR(16) NOT NULL,
    tran_type_cd CHAR(2) NOT NULL,
    tran_cat_cd NUMERIC(4,0) NOT NULL CHECK (tran_cat_cd >= 0),
    tran_source CHAR(10) NOT NULL,
    tran_desc CHAR(100) NOT NULL,
    tran_amt NUMERIC(11,2) NOT NULL,
    tran_merchant_id NUMERIC(9,0) NOT NULL CHECK (tran_merchant_id >= 0),
    tran_merchant_name CHAR(50) NOT NULL,
    tran_merchant_city CHAR(50) NOT NULL,
    tran_merchant_zip CHAR(10) NOT NULL,
    tran_card_num CHAR(16) NOT NULL,
    tran_orig_ts CHAR(26) NOT NULL,
    tran_proc_ts CHAR(26) NOT NULL,
    filler CHAR(20) NOT NULL,
    PRIMARY KEY (tran_id)
);

CREATE TABLE "tran_type" (
    tran_type CHAR(2) NOT NULL,
    tran_type_desc CHAR(50) NOT NULL,
    filler CHAR(8) NOT NULL,
    PRIMARY KEY (tran_type)
);

CREATE TABLE "tran_category" (
    tran_type_cd CHAR(2) NOT NULL,
    tran_cat_cd NUMERIC(4,0) NOT NULL CHECK (tran_cat_cd >= 0),
    tran_cat_type_desc CHAR(50) NOT NULL,
    filler CHAR(4) NOT NULL,
    PRIMARY KEY (tran_type_cd, tran_cat_cd)
);
