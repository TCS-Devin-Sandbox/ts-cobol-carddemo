import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Box, Stack, TextField } from '@mui/material';
import { api, ApiError } from '../api/client';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { useFunctionKeys } from '../hooks/useFunctionKeys';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { descriptionError, padTypeCode, sameDescription, typeCodeError } from '../validation';

/** TTUP-CHANGE-ACTION states of COTRTUPC. */
export type EditState =
  | 'NOT_FETCHED'
  | 'SHOW_DETAILS'
  | 'DETAILS_NOT_FOUND'
  | 'CREATE_NEW_RECORD'
  | 'CHANGES_NOT_OK'
  | 'CHANGES_OK_NOT_CONFIRMED'
  | 'CHANGES_DONE'
  | 'CHANGES_FAILED'
  | 'CONFIRM_DELETE'
  | 'DELETE_DONE'
  | 'DELETE_FAILED';

/** COTRTUPC messages, verbatim. */
export const MSG = {
  noInput: 'No input received',
  noChange: 'No change detected with respect to values fetched.',
  updateCancelled: 'Update was cancelled',
  deleteCancelled: 'Delete was cancelled',
};

/** 3250-SETUP-INFOMSG. */
export const INFO: Record<EditState, string> = {
  NOT_FETCHED: 'Enter transaction type to be maintained',
  SHOW_DETAILS: 'Update transaction type details shown.',
  DETAILS_NOT_FOUND: 'Press F05 to add. F12 to cancel',
  CREATE_NEW_RECORD: 'Enter new transaction type details.',
  CHANGES_NOT_OK: 'Update transaction type details shown.',
  CHANGES_OK_NOT_CONFIRMED: 'Changes validated.Press F5 to save',
  CHANGES_DONE: 'Changes committed to database',
  CHANGES_FAILED: 'Changes unsuccessful',
  CONFIRM_DELETE: 'Delete this record ? Press F4 to confirm',
  DELETE_DONE: 'Delete successful.',
  DELETE_FAILED: 'Changes unsuccessful',
};

const KEY_ENTRY: EditState[] = ['NOT_FETCHED', 'DETAILS_NOT_FOUND', 'CHANGES_DONE', 'CHANGES_FAILED',
  'DELETE_DONE', 'DELETE_FAILED'];
const DESC_EDIT: EditState[] = ['SHOW_DETAILS', 'CHANGES_NOT_OK', 'CREATE_NEW_RECORD'];
const F12_STATES: EditState[] = ['CHANGES_OK_NOT_CONFIRMED', 'SHOW_DETAILS', 'DETAILS_NOT_FOUND', 'CONFIRM_DELETE',
  'CREATE_NEW_RECORD'];

export function TransactionTypeEditPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const fromList = params.get('from') === 'list';
  const [state, setState] = useState<EditState>('NOT_FETCHED');
  const [trType, setTrType] = useState('');
  const [desc, setDesc] = useState('');
  const [original, setOriginal] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const go = (next: EditState, err: string | null = null) => {
    setState(next);
    setError(err);
  };

  const reset = (err: string | null = null) => {
    setTrType('');
    setDesc('');
    setOriginal(null);
    go('NOT_FETCHED', err);
  };

  const fail = (e: unknown, next: EditState) => go(next, e instanceof ApiError ? e.message : String(e));

  const enter = async () => {
    if (KEY_ENTRY.includes(state)) {
      if (!trType.trim()) return go('NOT_FETCHED', MSG.noInput);
      const err = typeCodeError(trType);
      if (err) return go('NOT_FETCHED', err);
      const key = padTypeCode(trType);
      setTrType(key);
      try {
        const found = await api.get(key);
        setDesc(found.trDescription);
        setOriginal(found.trDescription);
        go('SHOW_DETAILS');
      } catch (e) {
        setDesc('');
        setOriginal(null);
        if (e instanceof ApiError && e.status === 404) go('DETAILS_NOT_FOUND', e.message);
        else fail(e, 'NOT_FETCHED');
      }
      return;
    }
    if (DESC_EDIT.includes(state)) {
      const err = descriptionError(desc);
      if (err) return go(state === 'CREATE_NEW_RECORD' ? state : 'CHANGES_NOT_OK', err);
      if (original !== null && sameDescription(desc, original)) return go(state, MSG.noChange);
      go('CHANGES_OK_NOT_CONFIRMED');
    }
  };

  const f5 = async () => {
    if (state === 'DETAILS_NOT_FOUND') {
      setDesc('');
      return go('CREATE_NEW_RECORD');
    }
    if (state === 'CHANGES_OK_NOT_CONFIRMED') {
      try {
        const saved = await api.save(trType, desc.trim());
        setDesc(saved.trDescription);
        setOriginal(saved.trDescription);
        go('CHANGES_DONE');
      } catch (e) {
        fail(e, 'CHANGES_FAILED');
      }
    }
  };

  const f4 = async () => {
    if (state === 'SHOW_DETAILS') return go('CONFIRM_DELETE');
    if (state === 'CONFIRM_DELETE') {
      try {
        await api.remove(trType);
        go('DELETE_DONE');
      } catch (e) {
        fail(e, 'DELETE_FAILED');
      }
    }
  };

  const f12 = () => {
    if (state === 'CONFIRM_DELETE') return reset(MSG.deleteCancelled);
    if (state === 'CHANGES_OK_NOT_CONFIRMED' && original !== null) {
      setDesc(original);
      return go('SHOW_DETAILS', MSG.updateCancelled);
    }
    if (state === 'CHANGES_OK_NOT_CONFIRMED') return go('CREATE_NEW_RECORD', MSG.updateCancelled);
    reset();
  };

  const exit = () => (fromList ? navigate('/transaction-types') : navigate('/admin'));

  useFunctionKeys({
    Enter: state !== 'CONFIRM_DELETE' ? () => void enter() : undefined,
    F3: exit,
    F4: state === 'SHOW_DETAILS' || state === 'CONFIRM_DELETE' ? () => void f4() : undefined,
    F5: state === 'CHANGES_OK_NOT_CONFIRMED' || state === 'DETAILS_NOT_FOUND' ? () => void f5() : undefined,
    F12: F12_STATES.includes(state) ? f12 : undefined,
  });

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', p: 2 }}>
      <ScreenHeader tranId="CTTU" program="COTRTUPC" title="Maintain Transaction Type" />
      <Stack spacing={2} sx={{ maxWidth: 560, mx: 'auto' }}>
        <TextField
          label="Transaction Type"
          value={trType}
          onChange={(e) => {
            setTrType(e.target.value);
            if (state !== 'NOT_FETCHED') go('NOT_FETCHED');
          }}
          disabled={!KEY_ENTRY.includes(state)}
          inputProps={{ maxLength: 2, inputMode: 'numeric' }}
          autoFocus
        />
        <TextField
          label="Description"
          value={desc}
          onChange={(e) => setDesc(e.target.value)}
          disabled={!DESC_EDIT.includes(state)}
          inputProps={{ maxLength: 50, pattern: '[A-Za-z0-9 ]*' }}
        />
      </Stack>
      <MessageLine info={INFO[state]} error={error} />
      <FunctionKeyBar keys={[
        { label: 'ENTER=Process', onClick: () => void enter(), hidden: state === 'CONFIRM_DELETE' },
        { label: 'F3=Exit', onClick: exit },
        { label: 'F4=Delete', onClick: () => void f4(),
          hidden: state !== 'SHOW_DETAILS' && state !== 'CONFIRM_DELETE' },
        { label: 'F5=Save', onClick: () => void f5(),
          hidden: state !== 'CHANGES_OK_NOT_CONFIRMED' && state !== 'DETAILS_NOT_FOUND' },
        { label: 'F12=Cancel', onClick: f12, hidden: !F12_STATES.includes(state) },
      ]} />
    </Box>
  );
}
