import { MSG } from './messages';

/** CTTU 1245-EDIT-NUM-REQD for 'Tran Type code'. Returns the source message or null. */
export function validateTypeCode(raw: string): string | null {
  const value = raw.trim();
  if (value === '' || value === '*') return MSG.typeRequired;
  if (!/^\d{1,2}$/.test(value)) return MSG.typeNumeric;
  if (Number(value) === 0) return MSG.typeNotZero;
  return null;
}

/** 1210-EDIT-TRANTYPE: zero-pad to 2 digits. */
export function normaliseTypeCode(raw: string): string {
  return String(Number(raw.trim())).padStart(2, '0');
}

/** 1230 / 1240 EDIT-ALPHANUM-REQD for 'Transaction Desc' (BMS LENGTH=50). */
export function validateDescription(raw: string): string | null {
  if (raw.trim() === '' || raw.trim() === '*') return MSG.descRequired;
  if (!/^[A-Za-z0-9 ]*$/.test(raw) || raw.length > 50) return MSG.descAlphanumeric;
  return null;
}

/** CTLI 1220-EDIT-TYPECD: blank or 00 = no filter; else exactly two digits. */
export function validateTypeFilter(raw: string): string | null {
  if (raw.trim() === '' || raw === '00') return null;
  return /^\d{2}$/.test(raw) ? null : MSG.typeFilterInvalid;
}

/** 1205 / 1211: comparison of UPPER-CASE(TRIM(new)) with UPPER-CASE(TRIM(old)). */
export function sameIgnoringCase(a: string, b: string): boolean {
  return a.trim().toUpperCase() === b.trim().toUpperCase();
}
