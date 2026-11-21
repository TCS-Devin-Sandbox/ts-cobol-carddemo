package com.carddemo.batch.common;

import com.carddemo.batch.common.codec.CardDemoLayout;

/** Columns are in elementary copybook order, matching docs/schema-mapping-vsam.md. */
public enum VsamDataset {
    ACCOUNT("ACCTDATA", "account", CardDemoLayout.CVACT01Y, 1,
            "account_id active_status current_balance credit_limit cash_credit_limit open_date expiration_date reissue_date current_cycle_credit current_cycle_debit zip_code account_group_id filler"),
    CARD("CARDDATA", "card", CardDemoLayout.CVACT02Y, 1,
            "card_number account_id card_verification_value embossed_name expiration_date active_status filler"),
    CUSTOMER("CUSTDATA", "customer", CardDemoLayout.CVCUS01Y, 1,
            "customer_id first_name middle_name last_name address_line_1 address_line_2 address_line_3 state_code country_code zip_code phone_number_1 phone_number_2 social_security_number government_issued_id date_of_birth electronic_funds_transfer_account_id primary_cardholder_indicator fico_credit_score filler"),
    CARD_XREF("CARDXREF", "card_xref", CardDemoLayout.CVACT03Y, 1,
            "card_number customer_id account_id filler"),
    CATEGORY_BALANCE("TCATBALF", "transaction_category_balance", CardDemoLayout.CVTRA01Y, 3,
            "account_id transaction_type_code transaction_category_code balance filler"),
    DISCLOSURE("DISCGRP", "disclosure_group", CardDemoLayout.CVTRA02Y, 3,
            "account_group_id transaction_type_code transaction_category_code interest_rate filler"),
    TRANSACTION("DALYTRAN", "transaction", CardDemoLayout.CVTRA05Y, 1,
            "transaction_id transaction_type_code transaction_category_code source description amount merchant_id merchant_name merchant_city merchant_zip_code card_number origination_timestamp processed_timestamp filler"),
    TYPE("TRANTYPE", "tran_type", CardDemoLayout.CVTRA03Y, 1,
            "transaction_type_code description filler"),
    CATEGORY("TRANCATG", "tran_category", CardDemoLayout.CVTRA04Y, 2,
            "transaction_type_code transaction_category_code description filler");

    public final String inputName;
    public final String table;
    public final CardDemoLayout layout;
    final int keyFields;
    final String[] columns;

    VsamDataset(String inputName, String table, CardDemoLayout layout, int keyFields, String columns) {
        this.inputName = inputName;
        this.table = table;
        this.layout = layout;
        this.keyFields = keyFields;
        this.columns = columns.split(" ");
    }

    public static VsamDataset forInput(String filename) {
        for (var dataset : values()) {
            if (filename.startsWith("AWS.M2.CARDDEMO." + dataset.inputName + ".")) return dataset;
        }
        return null;
    }
}
