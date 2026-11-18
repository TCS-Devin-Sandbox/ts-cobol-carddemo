package com.carddemo.trantype.service;

/** Message literals copied verbatim from COTRTLIC.cbl / COTRTUPC.cbl. */
public final class SourceMessages {

    // CTTU 1245-EDIT-NUM-REQD with WS-EDIT-VARIABLE-NAME = 'Tran Type code'
    public static final String TYPE_CODE_REQUIRED = "Tran Type code must be supplied.";
    public static final String TYPE_CODE_NUMERIC = "Tran Type code must be numeric.";
    public static final String TYPE_CODE_NOT_ZERO = "Tran Type code must not be zero.";
    // 1230 / 1240 with WS-EDIT-VARIABLE-NAME = 'Transaction Desc'
    public static final String DESC_REQUIRED = "Transaction Desc must be supplied.";
    public static final String DESC_ALPHANUMERIC = "Transaction Desc can have numbers or alphabets only.";
    // CTLI 1220-EDIT-TYPECD
    public static final String TYPE_FILTER_INVALID = "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER";
    // CTLI 8000-READ-FORWARD / 1290-CROSS-EDITS
    public static final String NO_RECORDS_SEARCH = "No records found for this search condition.";
    public static final String NO_RECORDS_FILTER = "No Records found for these filter conditions";
    // CTTU 9100-GET-TRANSACTION-TYPE +100
    public static final String NOT_FOUND_KEY = "No record found for this key in database";
    // CTLI 9200-UPDATE-RECORD +100
    public static final String UPDATE_NOT_FOUND = "Record not found. Deleted by others ? ";
    // CTLI 9300 / CTTU 9800 OTHER
    public static final String DELETE_FAILED = "Delete failed with message:";
    // CTLI 9300 / CTTU 9800 -532
    public static final String CHILD_RECORDS = "Please delete associated child records first:";
    // CTTU 9700-INSERT-RECORD
    public static final String INSERT_FAILED = "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:";
    // CTTU 9600-WRITE-PROCESSING
    public static final String UPDATE_FAILED = "Error updating: TRANSACTION_TYPE Table. SQLCODE:";
    public static final String LOCK_ERROR = "Could not lock record for update";
    // CTTU 9100 <0
    public static final String ACCESS_FAILED = "Error accessing: TRANSACTION_TYPE table. SQLCODE:";
    // CTTU ABEND-ROUTINE
    public static final String UNEXPECTED_ABEND = "UNEXPECTED ABEND OCCURRED.";
    // COSGN00C
    public static final String WRONG_PASSWORD = "Wrong Password. Try again ...";

    public static final String SIGN_ON_REQUIRED = "Please sign on to use this function";
    public static final String ADMIN_ONLY = "Access restricted to administrators";

    private SourceMessages() {
    }
}
