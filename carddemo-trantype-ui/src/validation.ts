/** Client-side copies of the COBOL field edits (COTRTUPC 1210/1230/1245, COTRTLIC 1220/1240). */
export const DESC_MAX = 50;

export function typeCodeError(raw: string): string | null {
  const v = raw.trim();
  if (!v) return 'Tran Type code must be supplied.';
  if (!/^\+?\d{1,2}$/.test(v)) return 'Tran Type code must be numeric.';
  if (Number(v) === 0) return 'Tran Type code must not be zero.';
  return null;
}

export function padTypeCode(raw: string): string {
  return String(Number(raw.trim())).padStart(2, '0');
}

export function descriptionError(raw: string): string | null {
  const v = raw.trim();
  if (!v) return 'Transaction Desc must be supplied.';
  if (!/^[A-Za-z0-9 ]*$/.test(v)) return 'Transaction Desc can have numbers or alphabets only.';
  if (v.length > DESC_MAX) return 'Transaction Desc must not exceed 50 characters.';
  return null;
}

export function typeFilterError(raw: string): string | null {
  const v = raw.trim();
  return v && !/^\+?\d{1,2}$/.test(v) ? 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' : null;
}

export function sameDescription(a: string, b: string): boolean {
  return a.trim().toUpperCase() === b.trim().toUpperCase();
}
