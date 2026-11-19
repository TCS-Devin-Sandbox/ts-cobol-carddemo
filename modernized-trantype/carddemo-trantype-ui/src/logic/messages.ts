// Literals carried over verbatim from COTRTLIC / COTRTUPC (docs/discovery-ctli-cttu.md §6).
export const MSG = {
  // CTLI info line
  INFO_REC_ACTIONS: 'Type U to update, D to delete any record',
  INFO_DELETE_CONFIRM: 'Delete HIGHLIGHTED row ? Press F10 to confirm',
  INFO_UPDATE_CONFIRM: 'Update HIGHLIGHTED row. Press F10 to save',
  INFO_DELETE_SUCCESS: 'HIGHLIGHTED row deleted.Hit Enter to continue',
  INFO_UPDATE_SUCCESS: 'HIGHLIGHTED row was updated',
  // CTLI error line
  NO_PREVIOUS_PAGES: 'No previous pages to display',
  NO_MORE_PAGES: 'No more pages to display',
  MORE_THAN_ONE_ACTION: 'Please select only 1 action',
  INVALID_ACTION_CODE: 'Action code selected is invalid',
  NO_CHANGE_DATABASE: 'No change detected with respect to database values.',
  TYPE_FILTER_INVALID: 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER',
  // shared edits
  DESC_REQUIRED: 'Transaction Desc must be supplied.',
  DESC_ALPHANUM: 'Transaction Desc can have numbers or alphabets only.',
  DESC_TOO_LONG: 'Transaction Desc must not exceed 50 characters.',
  CODE_REQUIRED: 'Tran Type code must be supplied.',
  CODE_NUMERIC: 'Tran Type code must be numeric.',
  CODE_NOT_ZERO: 'Tran Type code must not be zero.',
  // CTTU info line
  PROMPT_SEARCH: 'Enter transaction type to be maintained',
  PROMPT_CREATE: 'Press F05 to add. F12 to cancel',
  PROMPT_DELETE_CONFIRM: 'Delete this record ? Press F4 to confirm',
  DELETE_SUCCESS: 'Delete successful.',
  PROMPT_CHANGES: 'Update transaction type details shown.',
  PROMPT_NEWDATA: 'Enter new transaction type details.',
  PROMPT_CONFIRM_SAVE: 'Changes validated.Press F5 to save',
  UPDATE_SUCCESS: 'Changes committed to database',
  FAILURE: 'Changes unsuccessful',
  // CTTU error line
  RECORD_NOT_FOUND: 'No record found for this key in database',
  NO_CHANGE_FETCHED: 'No change detected with respect to values fetched.',
  UPDATE_CANCELLED: 'Update was cancelled',
  DELETE_CANCELLED: 'Delete was cancelled',
  INVALID_KEY: 'Invalid key pressed',
  // security
  ADMIN_ONLY: 'No access - Admin Only option... ',
} as const;
