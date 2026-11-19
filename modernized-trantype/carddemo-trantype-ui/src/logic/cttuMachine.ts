import { MSG } from './messages';

/** COTRTUPC TTUP-CHANGE-ACTION, see docs/target-spec.md §4.2. */
export type EditState =
  | 'SEARCH' // LOW-VALUES / 'K'
  | 'NOT_FOUND' // 'X'
  | 'SHOW' // 'S'
  | 'CREATE' // 'R'
  | 'CHANGES_NOT_OK' // 'E'
  | 'BACKED_OUT' // 'B'
  | 'CONFIRM_SAVE' // 'N'
  | 'CONFIRM_DELETE' // '9'
  | 'SAVED' // 'C'
  | 'DELETED' // '7'
  | 'FAILED'; // '6' / 'L' / 'F'

export type FunctionKey = 'ENTER' | 'F3' | 'F4' | 'F5' | 'F12';

/** 0001-CHECK-PFKEYS / 3391-SETUP-PFKEY-ATTRS. */
export function isKeyValid(state: EditState, key: FunctionKey): boolean {
  switch (key) {
    case 'F3':
      return true;
    case 'ENTER':
      return state !== 'CONFIRM_DELETE';
    case 'F4':
      return state === 'SHOW' || state === 'CONFIRM_DELETE';
    case 'F5':
      return state === 'CONFIRM_SAVE' || state === 'NOT_FOUND';
    case 'F12':
      return ['CONFIRM_SAVE', 'SHOW', 'NOT_FOUND', 'CONFIRM_DELETE', 'CREATE'].includes(state);
  }
}

/** 3250-SETUP-INFOMSG. */
export const INFO_BY_STATE: Record<EditState, string> = {
  SEARCH: MSG.PROMPT_SEARCH,
  NOT_FOUND: MSG.PROMPT_CREATE,
  SHOW: MSG.PROMPT_SEARCH,
  CREATE: MSG.PROMPT_NEWDATA,
  CHANGES_NOT_OK: MSG.PROMPT_CHANGES,
  BACKED_OUT: MSG.PROMPT_CHANGES,
  CONFIRM_SAVE: MSG.PROMPT_CONFIRM_SAVE,
  CONFIRM_DELETE: MSG.PROMPT_DELETE_CONFIRM,
  SAVED: MSG.UPDATE_SUCCESS,
  DELETED: MSG.DELETE_SUCCESS,
  FAILED: MSG.FAILURE,
};

/** 3300-SETUP-SCREEN-ATTRS. */
export const keyEditable = (s: EditState) => s === 'SEARCH' || s === 'NOT_FOUND';
export const descEditable = (s: EditState) =>
  s === 'SHOW' || s === 'CREATE' || s === 'CHANGES_NOT_OK' || s === 'BACKED_OUT';
/** States after which the next key press restarts at key entry (0000 "simulate initial entry"). */
export const isTerminal = (s: EditState) => s === 'SAVED' || s === 'DELETED' || s === 'FAILED';
