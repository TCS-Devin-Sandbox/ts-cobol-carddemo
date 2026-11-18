import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Paper, TextField } from '@mui/material';
import {
  createTransactionType, deleteTransactionType, getTransactionType, TransactionType, updateTransactionType,
} from '../api/transactionTypes';
import { ApiError } from '../api/client';
import { ScreenHeader } from '../components/ScreenHeader';
import { MessageBanner } from '../components/MessageBanner';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { useFunctionKeys } from '../hooks/useFunctionKeys';
import { checkDescription, checkTypeCode, normalizeTypeCode, sameDescription } from '../logic/edits';
import {
  descEditable, EditState, FunctionKey, INFO_BY_STATE, isKeyValid, isTerminal, keyEditable,
} from '../logic/cttuMachine';
import { MSG } from '../logic/messages';

/** CTTU / COTRTUPC: search → show → edit → confirm save (F5) / delete (F4) / cancel (F12). */
export function TransactionTypeEditPage() {
  const navigate = useNavigate();
  const [state, setState] = useState<EditState>('SEARCH');
  const [trType, setTrType] = useState('');
  const [desc, setDesc] = useState('');
  const [original, setOriginal] = useState<TransactionType | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [keyError, setKeyError] = useState(false);
  const [descError, setDescError] = useState(false);

  const reset = (message: string | null = null) => {
    setState('SEARCH');
    setTrType('');
    setDesc('');
    setOriginal(null);
    setKeyError(false);
    setDescError(false);
    setError(message);
  };

  const search = async () => {
    const msg = checkTypeCode(trType);
    if (msg) {
      setKeyError(true);
      setError(msg);
      setState('SEARCH');
      return;
    }
    const key = normalizeTypeCode(trType);
    setTrType(key);
    try {
      const found = await getTransactionType(key);
      setOriginal(found);
      setDesc(found.trDescription);
      setState('SHOW');
    } catch (e) {
      const err = e as ApiError;
      setError(err.message);
      if (err.status === 404) {
        setOriginal(null);
        setDesc('');
        setState('NOT_FOUND');
      } else {
        setKeyError(true);
        setState('SEARCH');
      }
    }
  };

  const validateChanges = () => {
    if (original && sameDescription(desc, original.trDescription)) {
      setError(MSG.NO_CHANGE_FETCHED);
      return;
    }
    const msg = checkDescription(desc);
    setDescError(!!msg);
    if (msg) {
      setError(msg);
      setState('CHANGES_NOT_OK');
      return;
    }
    setState('CONFIRM_SAVE');
  };

  const save = async () => {
    try {
      if (original) await updateTransactionType(trType, desc.trim(), true);
      else await createTransactionType({ trType, trDescription: desc.trim() });
      setState('SAVED');
    } catch (e) {
      setError((e as ApiError).message);
      setState('FAILED');
    }
  };

  const remove = async () => {
    try {
      await deleteTransactionType(trType);
      setState('DELETED');
    } catch (e) {
      setError((e as ApiError).message);
      setKeyError(true);
      setState('FAILED');
    }
  };

  const press = async (key: FunctionKey) => {
    if (key === 'F3') {
      navigate('/admin');
      return;
    }
    if (!isKeyValid(state, key)) {
      setError(MSG.INVALID_KEY);
      return;
    }
    setError(null);
    setKeyError(false);
    if (isTerminal(state)) {
      reset();
      return;
    }
    switch (key) {
      case 'ENTER':
        if (state === 'SEARCH' || state === 'NOT_FOUND') await search();
        else if (state !== 'CONFIRM_SAVE') validateChanges();
        return;
      case 'F4':
        if (state === 'SHOW') setState('CONFIRM_DELETE');
        else await remove();
        return;
      case 'F5':
        if (state === 'NOT_FOUND') {
          const keyMsg = checkTypeCode(trType);
          if (keyMsg) {
            setError(keyMsg);
            setKeyError(true);
            return;
          }
          setTrType(normalizeTypeCode(trType || '0'));
          setDesc('');
          setState('CREATE');
        } else await save();
        return;
      case 'F12':
        if (state === 'CONFIRM_DELETE') reset(MSG.DELETE_CANCELLED);
        else if (state === 'CONFIRM_SAVE' && original) {
          setDesc(original.trDescription);
          setState('BACKED_OUT');
          setError(MSG.UPDATE_CANCELLED);
        } else if (state === 'CONFIRM_SAVE') reset(MSG.UPDATE_CANCELLED);
        else reset();
        return;
    }
  };

  useFunctionKeys({
    Enter: () => void press('ENTER'),
    F3: () => void press('F3'),
    F4: () => void press('F4'),
    F5: () => void press('F5'),
    F12: () => void press('F12'),
  });

  const blankKey = keyError && trType.trim() === '';

  return (
    <Box p={3} maxWidth={900} mx="auto">
      <ScreenHeader tranId="CTTU" programName="COTRTUPC" title="Maintain Transaction Type" />
      <Paper sx={{ p: 3 }}>
        <TextField label="Transaction Type" name="trType" size="small" value={trType} autoFocus
          disabled={!keyEditable(state)} error={keyError} placeholder={blankKey ? '*' : undefined}
          inputProps={{ maxLength: 2, 'data-testid': 'tr-type' }}
          onChange={(e) => setTrType(e.target.value)} sx={{ width: 200, mb: 2 }} />
        <TextField label="Description" name="trDescription" size="small" fullWidth value={desc}
          disabled={!descEditable(state)} error={descError}
          inputProps={{ maxLength: 50, 'data-testid': 'tr-description' }}
          onChange={(e) => setDesc(e.target.value)} />
      </Paper>
      <MessageBanner info={INFO_BY_STATE[state]} error={error} />
      <FunctionKeyBar keys={[
        { label: 'Enter=Process', onClick: () => void press('ENTER'), disabled: !isKeyValid(state, 'ENTER') },
        { label: 'F3=Exit', onClick: () => void press('F3') },
        { label: 'F4=Delete', onClick: () => void press('F4'), disabled: !isKeyValid(state, 'F4') },
        { label: 'F5=Save', onClick: () => void press('F5'), disabled: !isKeyValid(state, 'F5') },
        { label: 'F12=Cancel', onClick: () => void press('F12'), disabled: !isKeyValid(state, 'F12') },
      ]} />
    </Box>
  );
}
