package com.carddemo.trantype;

/** Message literals carried over verbatim from COTRTLIC / COTRTUPC (see docs/discovery-ctli-cttu.md §6). */
public final class Messages {

    private Messages() {
    }

    // CTLI information line (INFOMSG)
    public static final String INFO_REC_ACTIONS = "Type U to update, D to delete any record";
    public static final String INFO_DELETE_CONFIRM = "Delete HIGHLIGHTED row ? Press F10 to confirm";
    public static final String INFO_UPDATE_CONFIRM = "Update HIGHLIGHTED row. Press F10 to save";
    public static final String INFO_DELETE_SUCCESS = "HIGHLIGHTED row deleted.Hit Enter to continue";
    public static final String INFO_UPDATE_SUCCESS = "HIGHLIGHTED row was updated";

    // CTLI error line (ERRMSG)
    public static final String NO_RECORDS_FOUND = "No records found for this search condition.";
    public static final String NO_RECORDS_FOR_FILTERS = "No Records found for these filter conditions";
    public static final String NO_PREVIOUS_PAGES = "No previous pages to display";
    public static final String NO_MORE_PAGES = "No more pages to display";
    public static final String MORE_THAN_ONE_ACTION = "Please select only 1 action";
    public static final String INVALID_ACTION_CODE = "Action code selected is invalid";
    public static final String NO_CHANGE_DATABASE = "No change detected with respect to database values.";
    public static final String TYPE_FILTER_INVALID = "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER";

    // Shared field edits (1240 / 1230 / 1245)
    public static final String DESC_REQUIRED = "Transaction Desc must be supplied.";
    public static final String DESC_ALPHANUM = "Transaction Desc can have numbers or alphabets only.";
    public static final String DESC_TOO_LONG = "Transaction Desc must not exceed 50 characters.";
    public static final String CODE_REQUIRED = "Tran Type code must be supplied.";
    public static final String CODE_NUMERIC = "Tran Type code must be numeric.";
    public static final String CODE_NOT_ZERO = "Tran Type code must not be zero.";

    // CTTU
    public static final String RECORD_NOT_FOUND = "No record found for this key in database";
    public static final String NO_CHANGE_FETCHED = "No change detected with respect to values fetched.";
    public static final String COULD_NOT_LOCK = "Could not lock record for update";

    // DB2-derived (action literal + " SQLCODE: <db2 code>", as in 9999-FORMAT-DB2-MESSAGE)
    public static final String UPDATE_NOT_FOUND = "Record not found. Deleted by others ? SQLCODE: 100";
    public static final String UPDATE_DEADLOCK = "Deadlock. Someone else updating ? SQLCODE: -911";
    public static final String UPDATE_FAILED = "Update failed with SQLCODE: -999";
    public static final String DELETE_CHILDREN = "Please delete associated child records first: SQLCODE: -532";
    public static final String DELETE_NOT_FOUND = "Delete failed with message: SQLCODE: 100";
    public static final String DELETE_FAILED = "Delete failed with message: SQLCODE: -999";
    public static final String INSERT_DUPLICATE = "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803";
    public static final String INSERT_FAILED = "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -999";
    public static final String CTTU_UPDATE_FAILED = "Error updating: TRANSACTION_TYPE Table. SQLCODE: -999";
    public static final String READ_FAILED = "Error accessing: TRANSACTION_TYPE table. SQLCODE: -999";
    public static final String LIST_FAILED = "Error reading TRANSACTION_TYPE table SQLCODE: -999";

    // Security (COMEN01C / COSGN00C)
    public static final String ADMIN_ONLY = "No access - Admin Only option... ";
    public static final String AUTH_REQUIRED = "Authentication required";
    public static final String ENTER_USER_ID = "Please enter User ID ...";
    public static final String ENTER_PASSWORD = "Please enter Password ...";
    public static final String USER_NOT_FOUND = "User not found. Try again ...";
    public static final String WRONG_PASSWORD = "Wrong Password. Try again ...";

    // New (no source equivalent)
    public static final String DESC_FILTER_TOO_LONG = "Description filter must not exceed 50 characters.";
    public static final String PAGE_SIZE_INVALID = "Page size must be between 1 and 50.";
    public static final String DIRECTION_INVALID = "Direction must be next or prev.";
    public static final String MALFORMED_REQUEST = "Malformed request";
    public static final String CURSOR_INVALID = "Cursor must be a 2 digit transaction type code.";
    public static final String DB_ACCESS_FAILURE = "Db2 access failure. SQLCODE: -999";
    public static final String DATA_INTEGRITY = "Data integrity violation. SQLCODE: -999";
    public static final String UNEXPECTED = "UNEXPECTED ABEND OCCURRED.";
}
