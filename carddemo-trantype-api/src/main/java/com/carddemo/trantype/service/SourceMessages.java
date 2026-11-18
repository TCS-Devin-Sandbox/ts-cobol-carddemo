package com.carddemo.trantype.service;

/** Message literals copied verbatim from COTRTLIC.cbl / COTRTUPC.cbl (trailing blanks trimmed). */
public final class SourceMessages {

    // CTLI (COTRTLIC)
    public static final String TYPE_FILTER_INVALID = "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER";
    public static final String NO_RECORDS_FOUND = "No records found for this search condition.";
    public static final String NO_RECORDS_FOR_FILTERS = "No Records found for these filter conditions";
    public static final String NO_MORE_RECORDS = "No more pages for these search conditions";
    public static final String NO_CHANGES_DETECTED_DB = "No change detected with respect to database values.";
    public static final String RECORD_DELETED_BY_OTHERS = "Record not found. Deleted by others ? ".trim();
    public static final String DEADLOCK = "Deadlock. Someone else updating ?";
    public static final String UPDATE_FAILED = "Update failed with";
    public static final String DELETE_CHILDREN_FIRST = "Please delete associated child records first:";
    public static final String DELETE_FAILED = "Delete failed with message:";

    // CTTU (COTRTUPC)
    public static final String TYPE_CODE_NAME = "Tran Type code";
    public static final String DESC_NAME = "Transaction Desc";
    public static final String MUST_BE_SUPPLIED = " must be supplied.";
    public static final String MUST_BE_NUMERIC = " must be numeric.";
    public static final String MUST_NOT_BE_ZERO = " must not be zero.";
    public static final String ALPHANUM_ONLY = " can have numbers or alphabets only.";
    public static final String RECORD_NOT_FOUND = "No record found for this key in database";
    public static final String NO_CHANGES_DETECTED_FETCHED = "No change detected with respect to values fetched.";
    public static final String COULD_NOT_LOCK = "Could not lock record for update";
    public static final String INSERT_FAILED = "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:";
    public static final String UPDATE_TABLE_FAILED = "Error updating: TRANSACTION_TYPE Table. SQLCODE:";
    public static final String ACCESS_FAILED = "Error accessing: TRANSACTION_TYPE table. SQLCODE:";

    public static final String DESC_TOO_LONG = "Transaction Desc must not exceed 50 characters.";
    public static final String FORBIDDEN = "Access restricted to administrators";

    private SourceMessages() {
    }
}
