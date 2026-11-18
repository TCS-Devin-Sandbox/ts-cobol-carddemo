import { MSG } from './messages';

export const DESCRIPTION_MAX = 50;

const isBlank = (v: string | null | undefined) => v == null || v.trim() === '' || v.trim() === '*';

/** COTRTUPC 1245-EDIT-NUM-REQD. Returns an error message or null. */
export function checkTypeCode(value: string): string | null {
  if (isBlank(value)) return MSG.CODE_REQUIRED;
  const t = value.trim();
  if (!/^\d{1,2}$/.test(t)) return MSG.CODE_NUMERIC;
  if (Number(t) === 0) return MSG.CODE_NOT_ZERO;
  return null;
}

/** COTRTUPC 1210-EDIT-TRANTYPE: zero-pad to 2 digits. Assumes checkTypeCode passed. */
export const normalizeTypeCode = (value: string) => value.trim().padStart(2, '0');

/** 1230 / 1240-EDIT-ALPHANUM-REQD. */
export function checkDescription(value: string): string | null {
  if (isBlank(value)) return MSG.DESC_REQUIRED;
  const t = value.trim();
  if (!/^[A-Za-z0-9 ]*$/.test(t)) return MSG.DESC_ALPHANUM;
  if (t.length > DESCRIPTION_MAX) return MSG.DESC_TOO_LONG;
  return null;
}

/** COTRTLIC 1220-EDIT-TYPECD: blank / 00 = no filter, otherwise exactly 2 digits. */
export function checkTypeFilter(value: string): string | null {
  const t = value.trim();
  if (t === '' || t === '00') return null;
  return /^\d{2}$/.test(t) ? null : MSG.TYPE_FILTER_INVALID;
}

/** 1211 / 1205 comparison: case-insensitive, trimmed. */
export const sameDescription = (a: string, b: string) =>
  a.trim().toUpperCase() === b.trim().toUpperCase();

export type Selection =
  | { kind: 'none' }
  | { kind: 'error'; message: string; badRows: number[] }
  | { kind: 'delete' | 'update'; row: number };

/** COTRTLIC 1210-EDIT-ARRAY: U/D codes, single-action rule. */
export function evaluateSelections(codes: string[]): Selection {
  const badRows: number[] = [];
  const valid: number[] = [];
  let invalid = false;
  codes.forEach((raw, i) => {
    const c = raw.trim();
    if (c === '') return;
    if (c === 'U' || c === 'D') valid.push(i);
    else {
      invalid = true;
      badRows.push(i);
    }
  });
  const actions = valid.length + badRows.length;
  if (actions > 1) {
    return { kind: 'error', message: MSG.MORE_THAN_ONE_ACTION, badRows: [...valid, ...badRows].sort() };
  }
  if (invalid) return { kind: 'error', message: MSG.INVALID_ACTION_CODE, badRows };
  if (valid.length === 0) return { kind: 'none' };
  const row = valid[0];
  return { kind: codes[row].trim() === 'D' ? 'delete' : 'update', row };
}
