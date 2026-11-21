import { descriptionError, padTypeCode, sameDescription, typeCodeError, typeFilterError } from './validation';

describe('COBOL field edits', () => {
  it.each([
    ['', 'Tran Type code must be supplied.'],
    ['AB', 'Tran Type code must be numeric.'],
    ['00', 'Tran Type code must not be zero.'],
    ['7', null],
  ])('type code %j', (input, expected) => expect(typeCodeError(input)).toBe(expected));

  it('pads type code to 2 digits', () => expect(padTypeCode('7')).toBe('07'));

  it.each([
    [' ', 'Transaction Desc must be supplied.'],
    ['A-B', 'Transaction Desc can have numbers or alphabets only.'],
    ['Cash 1', null],
  ])('description %j', (input, expected) => expect(descriptionError(input)).toBe(expected));

  it('type filter must be 2-digit numeric', () => {
    expect(typeFilterError('X')).toBe('TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER');
    expect(typeFilterError('')).toBeNull();
  });

  it('compares descriptions case-insensitively', () => expect(sameDescription('abc ', 'ABC')).toBe(true));
});
