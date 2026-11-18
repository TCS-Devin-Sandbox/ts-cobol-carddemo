/** Message literals copied verbatim from COTRTLIC.cbl / COTRTUPC.cbl / COSGN00C.cbl / COADM01C.cbl. */
export const MSG = {
  // CTLI 2500-SETUP-MESSAGE
  ctliDefaultInfo: 'Type U to update, D to delete any record',
  ctliDeletePrompt: 'Delete HIGHLIGHTED row ? Press F10 to confirm',
  ctliUpdatePrompt: 'Update HIGHLIGHTED row. Press F10 to save',
  ctliDeleted: 'HIGHLIGHTED row deleted.Hit Enter to continue',
  ctliUpdated: 'HIGHLIGHTED row was updated',
  noPrevPages: 'No previous pages to display',
  noMorePages: 'No more pages to display',
  noMorePagesForSearch: 'No more pages for these search conditions',
  onlyOneAction: 'Please select only 1 action',
  invalidAction: 'Action code selected is invalid',
  ctliNoChange: 'No change detected with respect to database values.',
  typeFilterInvalid: 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER',
  ctliUpdateNotFound: 'Record not found. Deleted by others ? ',
  ctliDeadlock: 'Deadlock. Someone else updating ?',
  ctliUpdateFailed: 'Update failed with',
  deleteFailed: 'Delete failed with message:',
  childRecords: 'Please delete associated child records first:',
  // CTTU 3250-SETUP-INFOMSG
  cttuPromptKey: 'Enter transaction type to be maintained',
  cttuPromptCreate: 'Press F05 to add. F12 to cancel',
  cttuPromptDelete: 'Delete this record ? Press F4 to confirm',
  cttuDeleteDone: 'Delete successful.',
  cttuPromptChanges: 'Update transaction type details shown.',
  cttuPromptNewData: 'Enter new transaction type details.',
  cttuPromptConfirm: 'Changes validated.Press F5 to save',
  cttuUpdateDone: 'Changes committed to database',
  cttuFailure: 'Changes unsuccessful',
  // CTTU WS-RETURN-MSG
  cttuNotFound: 'No record found for this key in database',
  cttuNoChange: 'No change detected with respect to values fetched.',
  cttuLockError: 'Could not lock record for update',
  cttuUpdateCancelled: 'Update was cancelled',
  cttuDeleteCancelled: 'Delete was cancelled',
  invalidKey: 'Invalid key pressed',
  cttuInsertFailed: 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:',
  cttuUpdateTableFailed: 'Error updating: TRANSACTION_TYPE Table. SQLCODE:',
  cttuAccessFailed: 'Error accessing: TRANSACTION_TYPE table. SQLCODE:',
  // edits
  typeRequired: 'Tran Type code must be supplied.',
  typeNumeric: 'Tran Type code must be numeric.',
  typeNotZero: 'Tran Type code must not be zero.',
  descRequired: 'Transaction Desc must be supplied.',
  descAlphanumeric: 'Transaction Desc can have numbers or alphabets only.',
  // COSGN00C / COADM01C
  enterUserId: 'Please enter User ID ...',
  enterPassword: 'Please enter Password ...',
  invalidOption: 'Please enter a valid option number...',
} as const;

/** COBOL `PIC ----9` edit of SQLCODE (WS-DISP-SQLCODE): right-aligned in 5 positions, floating minus. */
export function dispSqlcode(sqlcode: number): string {
  const text = sqlcode < 0 ? `-${Math.abs(sqlcode)}` : `${sqlcode}`;
  return text.padStart(5, ' ');
}

/** CSDB2RPY 9999-FORMAT-DB2-MESSAGE as used by CTLI: TRIM(action) ' SQLCODE:' WS-DISP-SQLCODE. */
export function ctliDb2Message(action: string, sqlcode: number): string {
  return `${action.trim()} SQLCODE:${dispSqlcode(sqlcode)}`;
}

/** CTTU STRING ... 'SQLCODE :' WS-DISP-SQLCODE ':' (9800 delete branches). */
export function cttuDeleteMessage(prefix: string, sqlcode: number): string {
  return `${prefix}SQLCODE :${dispSqlcode(sqlcode)}:`;
}

/** CTTU STRING '<literal ending SQLCODE:>' WS-DISP-SQLCODE ':' (9100 / 9600 / 9700). */
export function cttuSqlMessage(prefix: string, sqlcode: number): string {
  return `${prefix}${dispSqlcode(sqlcode)}:`;
}
