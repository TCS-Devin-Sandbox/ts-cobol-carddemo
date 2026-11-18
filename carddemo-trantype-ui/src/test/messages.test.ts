import { describe, expect, it } from 'vitest';
import { ctliDb2Message, cttuDeleteMessage, cttuSqlMessage, dispSqlcode, MSG } from '../messages';
import { normaliseTypeCode, validateDescription, validateTypeCode, validateTypeFilter } from '../validation';

describe('COBOL PIC ----9 SQLCODE editing', () => {
  it('right-aligns with floating minus', () => {
    expect(dispSqlcode(-532)).toBe(' -532');
    expect(dispSqlcode(100)).toBe('  100');
    expect(dispSqlcode(-911)).toBe(' -911');
  });

  it('formats CTLI DB2 messages like 9999-FORMAT-DB2-MESSAGE', () => {
    expect(ctliDb2Message(MSG.childRecords, -532)).toBe('Please delete associated child records first: SQLCODE: -532');
    expect(ctliDb2Message(MSG.ctliUpdateNotFound, 100)).toBe('Record not found. Deleted by others ? SQLCODE:  100');
  });

  it('formats CTTU STRING messages', () => {
    expect(cttuDeleteMessage(MSG.childRecords, -532)).toBe('Please delete associated child records first:SQLCODE : -532:');
    expect(cttuSqlMessage(MSG.cttuInsertFailed, -803)).toBe(
      'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803:',
    );
  });
});

describe('field edits', () => {
  it('type code (1245-EDIT-NUM-REQD)', () => {
    expect(validateTypeCode('')).toBe('Tran Type code must be supplied.');
    expect(validateTypeCode('ab')).toBe('Tran Type code must be numeric.');
    expect(validateTypeCode('00')).toBe('Tran Type code must not be zero.');
    expect(validateTypeCode('5')).toBeNull();
    expect(normaliseTypeCode('5')).toBe('05');
  });

  it('description (1230-EDIT-ALPHANUM-REQD)', () => {
    expect(validateDescription('  ')).toBe('Transaction Desc must be supplied.');
    expect(validateDescription('A-B')).toBe('Transaction Desc can have numbers or alphabets only.');
    expect(validateDescription('Cash Back 2')).toBeNull();
  });

  it('type filter (1220-EDIT-TYPECD)', () => {
    expect(validateTypeFilter('')).toBeNull();
    expect(validateTypeFilter('00')).toBeNull();
    expect(validateTypeFilter('03')).toBeNull();
    expect(validateTypeFilter('3')).toBe('TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER');
  });
});
