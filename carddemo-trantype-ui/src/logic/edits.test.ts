import { checkDescription, checkTypeCode, checkTypeFilter, evaluateSelections, normalizeTypeCode } from './edits';
import { MSG } from './messages';

describe('CTTU key edits (1245-EDIT-NUM-REQD)', () => {
  it.each([['', MSG.CODE_REQUIRED], ['  ', MSG.CODE_REQUIRED], ['*', MSG.CODE_REQUIRED],
    ['1A', MSG.CODE_NUMERIC], ['123', MSG.CODE_NUMERIC], ['00', MSG.CODE_NOT_ZERO], ['0', MSG.CODE_NOT_ZERO]])(
    '"%s" -> %s', (input, msg) => expect(checkTypeCode(input)).toBe(msg));

  it('zero-pads valid codes', () => {
    expect(checkTypeCode('1')).toBeNull();
    expect(normalizeTypeCode(' 1')).toBe('01');
  });
});

describe('description edits (1240)', () => {
  it('requires, restricts characters and length', () => {
    expect(checkDescription(' ')).toBe(MSG.DESC_REQUIRED);
    expect(checkDescription('A-B')).toBe(MSG.DESC_ALPHANUM);
    expect(checkDescription('A'.repeat(51))).toBe(MSG.DESC_TOO_LONG);
    expect(checkDescription('Cash Back 2')).toBeNull();
  });
});

describe('type filter (1220-EDIT-TYPECD)', () => {
  it('accepts blank, 00 and two digits only', () => {
    expect(checkTypeFilter('')).toBeNull();
    expect(checkTypeFilter('00')).toBeNull();
    expect(checkTypeFilter('03')).toBeNull();
    expect(checkTypeFilter('3')).toBe(MSG.TYPE_FILTER_INVALID);
  });
});

describe('row selections (1210-EDIT-ARRAY)', () => {
  it('stages one action', () => {
    expect(evaluateSelections(['', 'D', ''])).toEqual({ kind: 'delete', row: 1 });
    expect(evaluateSelections(['U'])).toEqual({ kind: 'update', row: 0 });
    expect(evaluateSelections(['', ''])).toEqual({ kind: 'none' });
  });
  it('rejects invalid codes and more than one action', () => {
    expect(evaluateSelections(['X'])).toMatchObject({ kind: 'error', message: MSG.INVALID_ACTION_CODE });
    expect(evaluateSelections(['U', 'D'])).toMatchObject({ kind: 'error', message: MSG.MORE_THAN_ONE_ACTION, badRows: [0, 1] });
  });
});
