import { Box, TextField } from '@mui/material';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useApi } from '../api/ApiContext';
import { ApiError, TransactionType } from '../api/client';
import { FunctionKey, FunctionKeys } from '../components/FunctionKeys';
import { MessageBanner } from '../components/MessageBanner';
import { ScreenHeader } from '../components/ScreenHeader';
import { cttuDeleteMessage, cttuSqlMessage, MSG } from '../messages';
import { normaliseTypeCode, sameIgnoringCase, validateDescription, validateTypeCode } from '../validation';

/** TTUP-CHANGE-ACTION 88-levels of COTRTUPC. */
export type CttuState =
  | 'DETAILS_NOT_FETCHED'
  | 'INVALID_SEARCH_KEYS'
  | 'DETAILS_NOT_FOUND'
  | 'SHOW_DETAILS'
  | 'CREATE_NEW_RECORD'
  | 'CONFIRM_DELETE'
  | 'START_DELETE'
  | 'DELETE_DONE'
  | 'DELETE_FAILED'
  | 'CHANGES_NOT_OK'
  | 'CHANGES_OK_NOT_CONFIRMED'
  | 'CHANGES_OKAYED_AND_DONE'
  | 'CHANGES_OKAYED_LOCK_ERROR'
  | 'CHANGES_OKAYED_BUT_FAILED'
  | 'CHANGES_BACKED_OUT';

/** 3250-SETUP-INFOMSG. */
export function infoFor(state: CttuState): string {
  switch (state) {
    case 'DETAILS_NOT_FOUND':
      return MSG.cttuPromptCreate;
    case 'CHANGES_BACKED_OUT':
    case 'CHANGES_NOT_OK':
      return MSG.cttuPromptChanges;
    case 'CONFIRM_DELETE':
      return MSG.cttuPromptDelete;
    case 'DELETE_FAILED':
    case 'CHANGES_OKAYED_LOCK_ERROR':
    case 'CHANGES_OKAYED_BUT_FAILED':
      return MSG.cttuFailure;
    case 'DELETE_DONE':
      return MSG.cttuDeleteDone;
    case 'CREATE_NEW_RECORD':
      return MSG.cttuPromptNewData;
    case 'CHANGES_OK_NOT_CONFIRMED':
      return MSG.cttuPromptConfirm;
    case 'CHANGES_OKAYED_AND_DONE':
      return MSG.cttuUpdateDone;
    default:
      // NOT-FETCHED, INVALID-SEARCH-KEYS, SHOW-DETAILS (shares the WHEN), START-DELETE (no info)
      return MSG.cttuPromptKey;
  }
}

/** 0001-CHECK-PFKEYS. */
export function isKeyValid(key: string, state: CttuState): boolean {
  switch (key) {
    case 'F3':
      return true;
    case 'Enter':
      return state !== 'CONFIRM_DELETE';
    case 'F4':
      return state === 'SHOW_DETAILS' || state === 'CONFIRM_DELETE';
    case 'F5':
      return (
        state === 'CHANGES_OK_NOT_CONFIRMED' ||
        state === 'DETAILS_NOT_FOUND' ||
        ['CONFIRM_DELETE', 'START_DELETE', 'DELETE_DONE', 'DELETE_FAILED'].includes(state)
      );
    case 'F12':
      return ['CHANGES_OK_NOT_CONFIRMED', 'SHOW_DETAILS', 'DETAILS_NOT_FOUND', 'CONFIRM_DELETE', 'CREATE_NEW_RECORD'].includes(
        state,
      );
    default:
      return false;
  }
}

/** States reset to a fresh screen at the start of the next interaction (0000-MAIN first EVALUATE). */
const TERMINAL: CttuState[] = [
  'CHANGES_OKAYED_AND_DONE',
  'CHANGES_OKAYED_LOCK_ERROR',
  'CHANGES_OKAYED_BUT_FAILED',
  'DELETE_DONE',
  'DELETE_FAILED',
];

/** CTTU / COTRTUPC / map CTRTUPA. */
export function TransactionTypeEditPage() {
  const api = useApi();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const [state, setState] = useState<CttuState>('DETAILS_NOT_FETCHED');
  const [typeInput, setTypeInput] = useState('');
  const [descInput, setDescInput] = useState('');
  const [old, setOld] = useState<TransactionType | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [typeError, setTypeError] = useState(false);
  const [descError, setDescError] = useState(false);

  const go = (next: CttuState, message: string | null = null) => {
    setState(next);
    setError(message);
  };

  const resetScreen = (message: string | null = null) => {
    setTypeInput('');
    setDescInput('');
    setOld(null);
    setTypeError(false);
    setDescError(false);
    go('DETAILS_NOT_FETCHED', message);
  };

  /** 9000-READ-TRANTYPE. */
  const read = async (key: string, message: string | null = null) => {
    try {
      const found = await api.get(key);
      setOld(found);
      setTypeInput(found.trType);
      setDescInput(found.description);
      go('SHOW_DETAILS', message);
    } catch (e) {
      const err = e as ApiError;
      setOld(null);
      if (err.code === 'NOT_FOUND') {
        setDescInput('');
        setTypeError(true);
        go('DETAILS_NOT_FOUND', MSG.cttuNotFound);
      } else if (err.code === 'VALIDATION_ERROR') {
        setTypeError(true);
        go('INVALID_SEARCH_KEYS', err.message);
      } else {
        go('DETAILS_NOT_FETCHED', cttuSqlMessage(MSG.cttuAccessFailed, err.sqlcode ?? -1));
      }
    }
  };

  /** ENTER (1000-PROCESS-INPUTS + 2000-DECIDE-ACTION). */
  const enter = async (descOverride?: string) => {
    setTypeError(false);
    setDescError(false);
    if (['DETAILS_NOT_FETCHED', 'INVALID_SEARCH_KEYS', 'DETAILS_NOT_FOUND'].includes(state)) {
      const err = validateTypeCode(typeInput);
      if (err) {
        setTypeError(true);
        go(err === MSG.typeRequired ? 'DETAILS_NOT_FETCHED' : 'INVALID_SEARCH_KEYS', err);
        return;
      }
      const key = normaliseTypeCode(typeInput);
      setTypeInput(key);
      await read(key);
      return;
    }
    if (state === 'CHANGES_OK_NOT_CONFIRMED') {
      setError(null);
      return;
    }
    const desc = descOverride ?? descInput;
    if (descOverride !== undefined) setDescInput(descOverride);
    // 1205-COMPARE-OLD-NEW
    const oldType = old?.trType ?? '';
    if (typeInput === oldType && sameIgnoringCase(desc, old?.description ?? '')) {
      setError(MSG.cttuNoChange);
      if (state === 'CHANGES_BACKED_OUT') setState('CHANGES_NOT_OK');
      return;
    }
    const err = validateDescription(desc);
    if (err) {
      setDescError(true);
      go('CHANGES_NOT_OK', err);
      return;
    }
    go('CHANGES_OK_NOT_CONFIRMED');
  };

  /** F5 save: 9600-WRITE-PROCESSING (UPDATE, INSERT on +100). */
  const save = async () => {
    try {
      const saved = await api.save(typeInput, descInput.trim(), true);
      setOld(saved);
      go('CHANGES_OKAYED_AND_DONE');
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'LOCK_ERROR') go('CHANGES_OKAYED_LOCK_ERROR', MSG.cttuLockError);
      else if (err.code === 'DUPLICATE_KEY') go('CHANGES_OKAYED_BUT_FAILED', cttuSqlMessage(MSG.cttuInsertFailed, err.sqlcode ?? -803));
      else if (err.code === 'VALIDATION_ERROR') go('CHANGES_NOT_OK', err.message);
      else go('CHANGES_OKAYED_BUT_FAILED', cttuSqlMessage(MSG.cttuUpdateTableFailed, err.sqlcode ?? -1));
    }
  };

  /** F4 confirm: 9800-DELETE-PROCESSING. */
  const remove = async () => {
    try {
      await api.remove(typeInput);
      go('DELETE_DONE');
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'CHILD_RECORDS_EXIST') {
        setTypeError(true);
        go('START_DELETE', cttuDeleteMessage(MSG.childRecords, err.sqlcode ?? -532));
      } else {
        setTypeError(true);
        go('DELETE_FAILED', cttuDeleteMessage(MSG.deleteFailed, err.sqlcode ?? -1));
      }
    }
  };

  const exit = () => navigate(from === 'list' ? '/transaction-types' : '/admin');

  const press = (key: string) => {
    if (key === 'F3') {
      exit();
      return;
    }
    if (!isKeyValid(key, state)) {
      setError(MSG.invalidKey);
      return;
    }
    if (TERMINAL.includes(state) || (state === 'CHANGES_BACKED_OUT' && !old)) {
      resetScreen();
      return;
    }
    switch (key) {
      case 'F4':
        if (state === 'SHOW_DETAILS') go('CONFIRM_DELETE');
        else void remove();
        return;
      case 'F5':
        if (state === 'DETAILS_NOT_FOUND') {
          setTypeError(false);
          setDescInput('');
          go('CREATE_NEW_RECORD');
        } else if (state === 'CHANGES_OK_NOT_CONFIRMED') void save();
        else void enter(state === 'START_DELETE' ? '' : undefined);
        return;
      case 'F12':
        if (state === 'CHANGES_OK_NOT_CONFIRMED') {
          // 2000-DECIDE-ACTION: WS-UPDATE-WAS-CANCELLED, TTUP-CHANGES-BACKED-OUT; original values redisplayed
          setDescInput(old?.description ?? '');
          go('CHANGES_BACKED_OUT', MSG.cttuUpdateCancelled);
        } else if (state === 'CONFIRM_DELETE') {
          resetScreen(MSG.cttuDeleteCancelled);
        } else {
          resetScreen();
        }
        return;
      default:
        // START-DELETE: the protected description is not transmitted by the 3270 (no MDT), so it is blank
        void enter(state === 'START_DELETE' ? '' : undefined);
    }
  };

  const typeEditable = ['DETAILS_NOT_FETCHED', 'INVALID_SEARCH_KEYS', 'DETAILS_NOT_FOUND'].includes(state) ||
    (state === 'CHANGES_BACKED_OUT' && !old);
  const descEditable = ['SHOW_DETAILS', 'CHANGES_NOT_OK', 'CREATE_NEW_RECORD', 'CHANGES_BACKED_OUT'].includes(state);

  const keys: FunctionKey[] = [
    { key: 'Enter', label: 'ENTER=Process', onPress: () => press('Enter'), visible: state !== 'CONFIRM_DELETE' },
    { key: 'F3', label: 'F3=Exit', onPress: () => press('F3') },
    { key: 'F4', label: 'F4=Delete', onPress: () => press('F4'), visible: state === 'SHOW_DETAILS' || state === 'CONFIRM_DELETE' },
    {
      key: 'F5',
      label: 'F5=Save',
      onPress: () => press('F5'),
      visible: state === 'CHANGES_OK_NOT_CONFIRMED' || state === 'DETAILS_NOT_FOUND',
    },
    {
      key: 'F12',
      label: 'F12=Cancel',
      onPress: () => press('F12'),
      visible: ['CHANGES_OK_NOT_CONFIRMED', 'SHOW_DETAILS', 'DETAILS_NOT_FOUND', 'CONFIRM_DELETE', 'CREATE_NEW_RECORD'].includes(state),
    },
  ];

  return (
    <Box component="main" p={3} maxWidth={900}>
      <ScreenHeader tranId="CTTU" programName="COTRTUPC" title="Maintain Transaction Type" />
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, maxWidth: 600 }} data-state={state} data-testid="cttu-form">
        <TextField
          label="Transaction Type  :"
          size="small"
          value={typeInput}
          onChange={(e) => setTypeInput(e.target.value)}
          InputProps={{ readOnly: !typeEditable }}
          error={typeError}
          inputProps={{ maxLength: 2, 'aria-label': 'Transaction Type' }}
          autoFocus
        />
        <TextField
          label="Description       :"
          size="small"
          value={descInput}
          onChange={(e) => setDescInput(e.target.value)}
          InputProps={{ readOnly: !descEditable }}
          error={descError}
          inputProps={{ maxLength: 50, 'aria-label': 'Description' }}
        />
      </Box>
      <MessageBanner info={infoFor(state)} error={error} />
      <FunctionKeys keys={keys} onOtherKey={(k) => press(k)} />
    </Box>
  );
}
