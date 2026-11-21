package com.carddemo.batch.readmasters;

import com.carddemo.batch.common.VsamDataset;

enum ReadMaster {
    ACCOUNT("READACCT", "CBACT01C", "ACCTFILE", VsamDataset.ACCOUNT,
            "account_id active_status current_balance credit_limit cash_credit_limit open_date expiration_date reissue_date current_cycle_credit current_cycle_debit zip_code account_group_id filler"),
    CARD("READCARD", "CBACT02C", "CARDFILE", VsamDataset.CARD,
            "card_number account_id card_verification_value embossed_name expiration_date active_status filler"),
    XREF("READXREF", "CBACT03C", "XREFFILE", VsamDataset.CARD_XREF,
            "card_number customer_id account_id filler"),
    CUSTOMER("READCUST", "CBCUS01C", "CUSTFILE", VsamDataset.CUSTOMER,
            "customer_id first_name middle_name last_name address_line_1 address_line_2 address_line_3 state_code country_code zip_code phone_number_1 phone_number_2 social_security_number government_issued_id date_of_birth electronic_funds_transfer_account_id primary_cardholder_indicator fico_credit_score filler");

    final String job;
    final String program;
    final String ddname;
    final VsamDataset dataset;
    final String[] columns;

    ReadMaster(String job, String program, String ddname, VsamDataset dataset, String columns) {
        this.job = job;
        this.program = program;
        this.ddname = ddname;
        this.dataset = dataset;
        this.columns = columns.split(" ");
    }
}
