import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import {
  deleteTransactionType, listTransactionTypes, ListParams, PAGE_SIZE, TransactionTypePage, updateTransactionType,
} from '../api/transactionTypes';
import { ApiError } from '../api/client';
import { ScreenHeader } from '../components/ScreenHeader';
import { MessageBanner } from '../components/MessageBanner';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { useFunctionKeys } from '../hooks/useFunctionKeys';
import { checkDescription, checkTypeFilter, evaluateSelections, sameDescription } from '../logic/edits';
import { MSG } from '../logic/messages';

interface Pending {
  kind: 'delete' | 'update';
  row: number;
  trType: string;
}

const emptyArray = () => Array.from({ length: PAGE_SIZE }, () => '');

/** CTLI / COTRTLIC: 7-row keyset list with inline U/D actions confirmed by F10. */
export function TransactionTypeListPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState<TransactionTypePage | null>(null);
  const [typeFilter, setTypeFilter] = useState('');
  const [descFilter, setDescFilter] = useState('');
  const [applied, setApplied] = useState({ typeFilter: '', descFilter: '' });
  const [selects, setSelects] = useState<string[]>(emptyArray);
  const [descs, setDescs] = useState<string[]>(emptyArray);
  const [pending, setPending] = useState<Pending | null>(null);
  const [badRows, setBadRows] = useState<number[]>([]);
  const [descError, setDescError] = useState(false);
  const [filterError, setFilterError] = useState(false);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (params: ListParams, filters = applied, messages: { info?: string; error?: string } = {}) => {
      try {
        const p = await listTransactionTypes({ ...params, ...filters, size: PAGE_SIZE });
        setPage(p);
        setDescs(Array.from({ length: PAGE_SIZE }, (_, i) => p.rows[i]?.trDescription ?? ''));
        setSelects(emptyArray());
        setPending(null);
        setBadRows([]);
        setDescError(false);
        setFilterError(false);
        setInfo(messages.info ?? (p.rows.length ? p.infoMessage : null));
        setError(messages.error ?? p.errorMessage);
        setApplied(filters);
      } catch (e) {
        const err = e as ApiError;
        setError(err.message);
        if (err.message === MSG.TYPE_FILTER_INVALID) setFilterError(true);
      }
    },
    [applied],
  );

  useEffect(() => {
    void load({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const rows = page?.rows ?? [];
  const reloadCurrent = (messages?: { info?: string; error?: string }) =>
    load(page?.firstKey ? { cursor: page.firstKey, direction: 'next' } : {}, applied, messages);
  const filtersChanged = () =>
    typeFilter.trim() !== applied.typeFilter.trim() || descFilter.trim() !== applied.descFilter.trim();

  /** Enter: 1200-EDIT-INPUTS then stage the selected action. */
  const process = async () => {
    setError(null);
    const filterMsg = checkTypeFilter(typeFilter);
    if (filterMsg) {
      setFilterError(true);
      setPending(null);
      setError(filterMsg);
      return;
    }
    setFilterError(false);
    if (filtersChanged()) {
      await load({}, { typeFilter, descFilter });
      return;
    }
    const sel = evaluateSelections(selects.slice(0, rows.length));
    setBadRows(sel.kind === 'error' ? sel.badRows : []);
    setDescError(false);
    if (sel.kind === 'error') {
      setPending(null);
      setError(sel.message);
      return;
    }
    if (sel.kind === 'none') {
      await reloadCurrent();
      return;
    }
    const row = rows[sel.row];
    setPending({ kind: sel.kind, row: sel.row, trType: row.trType });
    if (sel.kind === 'delete') {
      setInfo(MSG.INFO_DELETE_CONFIRM);
      return;
    }
    setInfo(MSG.INFO_UPDATE_CONFIRM);
    if (sameDescription(descs[sel.row], row.trDescription)) {
      setError(MSG.NO_CHANGE_DATABASE);
    } else {
      const msg = checkDescription(descs[sel.row]);
      setDescError(!!msg);
      setError(msg);
    }
  };

  /** F10: confirm a pending delete (9300) or update (9200); otherwise behaves as Enter. */
  const confirm = async () => {
    const sel = evaluateSelections(selects.slice(0, rows.length));
    const unchanged = pending && !filtersChanged() && sel.kind === pending.kind && sel.row === pending.row;
    if (!pending || !unchanged) {
      await process();
      return;
    }
    setError(null);
    if (pending.kind === 'delete') {
      try {
        await deleteTransactionType(pending.trType);
        await load({}, applied, { info: MSG.INFO_DELETE_SUCCESS });
      } catch (e) {
        setError((e as ApiError).message);
      }
      return;
    }
    const desc = descs[pending.row];
    if (sameDescription(desc, rows[pending.row].trDescription)) {
      setError(MSG.NO_CHANGE_DATABASE);
      return;
    }
    const msg = checkDescription(desc);
    if (msg) {
      setDescError(true);
      setError(msg);
      return;
    }
    try {
      await updateTransactionType(pending.trType, desc.trim());
      await reloadCurrent({ info: MSG.INFO_UPDATE_SUCCESS });
    } catch (e) {
      setError((e as ApiError).message);
    }
  };

  const pageUp = async () => {
    if (!page?.hasPrev || !page.firstKey) {
      setError(MSG.NO_PREVIOUS_PAGES);
      return;
    }
    await load({ cursor: page.firstKey, direction: 'prev' });
  };

  const pageDown = async () => {
    if (!page?.hasNext || !page.nextCursor) {
      setError(MSG.NO_MORE_PAGES);
      return;
    }
    await load({ cursor: page.nextCursor, direction: 'next' });
  };

  const add = () => navigate('/transaction-types/maintain');
  const exit = () => navigate('/admin');

  useFunctionKeys({ Enter: process, F2: add, F3: exit, F7: pageUp, F8: pageDown, F10: confirm });

  const actionPending = pending !== null;

  return (
    <Box p={3} maxWidth={1000} mx="auto">
      <ScreenHeader tranId="CTLI" programName="COTRTLIC" title="Maintain Transaction Type" />
      <Typography align="right" data-testid="page-number">Page {page?.pageNumber ?? 1}</Typography>
      <Box display="flex" gap={2} my={2}>
        <TextField label="Type Filter" name="typeFilter" size="small" value={typeFilter} error={filterError}
          disabled={actionPending} inputProps={{ maxLength: 2, 'data-testid': 'type-filter' }}
          onChange={(e) => setTypeFilter(e.target.value)} sx={{ width: 140 }} />
        <TextField label="Description Filter" name="descFilter" size="small" value={descFilter} fullWidth
          disabled={actionPending} inputProps={{ maxLength: 50, 'data-testid': 'desc-filter' }}
          onChange={(e) => setDescFilter(e.target.value)} />
      </Box>
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell width={80}>Select</TableCell>
              <TableCell width={80}>Type</TableCell>
              <TableCell>Description</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {Array.from({ length: PAGE_SIZE }, (_, i) => {
              const row = rows[i];
              const isPending = pending?.row === i;
              return (
                <TableRow key={i} selected={isPending} data-testid={`row-${i}`}>
                  <TableCell>
                    <TextField size="small" value={selects[i]} error={badRows.includes(i)}
                      disabled={!row || filterError}
                      inputProps={{ maxLength: 1, 'aria-label': `select row ${i + 1}`, 'data-testid': `select-${i}` }}
                      onChange={(e) => setSelects((s) => s.map((v, j) => (j === i ? e.target.value.toUpperCase() : v)))} />
                  </TableCell>
                  <TableCell data-testid={`type-${i}`}>{row?.trType ?? ''}</TableCell>
                  <TableCell>
                    {row && (
                      <TextField size="small" fullWidth value={descs[i]}
                        error={isPending && pending?.kind === 'update' && descError}
                        InputProps={{ readOnly: !(isPending && pending?.kind === 'update') }}
                        inputProps={{ maxLength: 50, 'aria-label': `description row ${i + 1}`, 'data-testid': `desc-${i}` }}
                        onChange={(e) => setDescs((d) => d.map((v, j) => (j === i ? e.target.value : v)))} />
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <MessageBanner info={info} error={error} />
      <FunctionKeyBar keys={[
        { label: 'Enter=Process', onClick: process },
        { label: 'F2=Add', onClick: add },
        { label: 'F3=Exit', onClick: exit },
        { label: 'F7=Page Up', onClick: pageUp },
        { label: 'F8=Page Dn', onClick: pageDown },
        { label: 'F10=Save', onClick: confirm },
      ]} />
    </Box>
  );
}
