import { Box, Table, TableBody, TableCell, TableHead, TableRow, TextField } from '@mui/material';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApi } from '../api/ApiContext';
import { ApiError, TransactionType } from '../api/client';
import { FunctionKeys } from '../components/FunctionKeys';
import { MessageBanner } from '../components/MessageBanner';
import { ScreenHeader } from '../components/ScreenHeader';
import { ctliDb2Message, MSG } from '../messages';
import { sameIgnoringCase, validateDescription, validateTypeFilter } from '../validation';

/** WS-MAX-SCREEN-LINES. */
export const PAGE_SIZE = 7;

interface Filters {
  type: string;
  desc: string;
}

interface Paging {
  firstKey: string | null;
  nextCursor: string | null;
  hasNext: boolean;
  hasPrev: boolean;
  pageNumber: number;
  lastPageShown: boolean;
}

type Pending = { kind: 'D' | 'U'; index: number } | null;

interface FieldErrors {
  typeFilter?: boolean;
  descFilter?: boolean;
  selects?: number[];
  desc?: number;
}

const blankSelects = () => Array<string>(PAGE_SIZE).fill('');

/** CTLI / COTRTLIC / map CTRTLIA. */
export function TransactionTypeListPage() {
  const api = useApi();
  const navigate = useNavigate();
  const [rows, setRows] = useState<TransactionType[]>([]);
  const [paging, setPaging] = useState<Paging>({
    firstKey: null,
    nextCursor: null,
    hasNext: false,
    hasPrev: false,
    pageNumber: 1,
    lastPageShown: false,
  });
  const [filters, setFilters] = useState<Filters>({ type: '', desc: '' });
  const [applied, setApplied] = useState<Filters>({ type: '', desc: '' });
  const [selects, setSelects] = useState<string[]>(blankSelects);
  const [descEdits, setDescEdits] = useState<string[]>(blankSelects);
  const [pending, setPending] = useState<Pending>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  const load = useCallback(
    async (
      f: Filters,
      cursor: string | null,
      direction: 'forward' | 'backward',
      page: number,
      msgs: { info?: string | null; error?: string | null; lastPageShown?: boolean } = {},
    ) => {
      try {
        const res = await api.list({
          cursor,
          direction,
          page,
          size: PAGE_SIZE,
          typeFilter: f.type,
          descFilter: f.desc,
        });
        setRows(res.rows);
        setSelects(blankSelects());
        setDescEdits(res.rows.map((r) => r.description));
        setPending(null);
        setPaging({
          firstKey: res.firstKey,
          nextCursor: res.nextCursor,
          hasNext: res.hasNext,
          hasPrev: res.hasPrev,
          pageNumber: res.pageNumber,
          lastPageShown: msgs.lastPageShown ?? false,
        });
        if (res.message) {
          setError(res.message);
          setInfo(null);
          setFieldErrors(f.type || f.desc ? { typeFilter: !!f.type, descFilter: !!f.desc } : {});
        } else {
          setError(msgs.error ?? null);
          setInfo(msgs.info ?? MSG.ctliDefaultInfo);
        }
        return res;
      } catch (e) {
        setError(e instanceof ApiError ? e.message : String(e));
        setInfo(null);
        return null;
      }
    },
    [api],
  );

  useEffect(() => {
    void load({ type: '', desc: '' }, null, 'forward', 1);
  }, [load]);

  const refresh = (msgs: { info?: string | null; error?: string | null } = {}) =>
    load(applied, paging.firstKey ?? '', 'forward', paging.pageNumber, { ...msgs, lastPageShown: paging.lastPageShown });

  /** 1211-EDIT-ARRAY-DESC. */
  const validateUpdate = (index: number): string | null => {
    const value = descEdits[index] ?? '';
    if (sameIgnoringCase(value, rows[index].description)) return MSG.ctliNoChange;
    return validateDescription(value);
  };

  const filtersChanged = () => filters.type !== applied.type || filters.desc !== applied.desc;

  const enter = () => {
    setFieldErrors({});
    if (filtersChanged()) {
      const err = validateTypeFilter(filters.type);
      if (err) {
        setError(err);
        setInfo(null);
        setFieldErrors({ typeFilter: true });
        return;
      }
      setApplied(filters);
      void load(filters, null, 'forward', 1);
      return;
    }
    const actions = selects
      .map((s, i) => ({ code: s.trim().toUpperCase(), index: i }))
      .filter((a) => a.code !== '');
    if (actions.length > 1) {
      setError(MSG.onlyOneAction);
      setFieldErrors({ selects: actions.map((a) => a.index) });
      return;
    }
    if (actions.length === 1 && actions[0].code !== 'U' && actions[0].code !== 'D') {
      setError(MSG.invalidAction);
      setFieldErrors({ selects: [actions[0].index] });
      return;
    }
    if (actions.length === 0) {
      void refresh();
      return;
    }
    const { code, index } = actions[0];
    setError(null);
    if (code === 'D') {
      setPending({ kind: 'D', index });
      setInfo(MSG.ctliDeletePrompt);
      return;
    }
    if (pending?.kind === 'U' && pending.index === index) {
      const err = validateUpdate(index);
      if (err) {
        setError(err);
        setFieldErrors({ desc: index });
      }
      setInfo(MSG.ctliUpdatePrompt);
      return;
    }
    setPending({ kind: 'U', index });
    setDescEdits(rows.map((r) => r.description));
    setInfo(MSG.ctliUpdatePrompt);
  };

  const f10 = async () => {
    const selected = selects.map((v, i) => ({ code: v.trim().toUpperCase(), index: i })).filter((a) => a.code !== '');
    const selectionUnchanged =
      !!pending && selected.length === 1 && selected[0].index === pending.index && selected[0].code === pending.kind;
    // 0000-MAIN: F10 acts only on the confirmed action; any filter or row-selection change is processed as ENTER
    if (!pending || filtersChanged() || !selectionUnchanged) {
      enter();
      return;
    }
    const row = rows[pending.index];
    setFieldErrors({});
    if (pending.kind === 'D') {
      try {
        await api.remove(row.trType);
        setApplied(filters);
        await load(filters, null, 'forward', 1, { info: MSG.ctliDeleted });
      } catch (e) {
        const err = e as ApiError;
        setError(
          err.code === 'CHILD_RECORDS_EXIST'
            ? ctliDb2Message(MSG.childRecords, err.sqlcode ?? -532)
            : ctliDb2Message(MSG.deleteFailed, err.sqlcode ?? -1),
        );
        setFieldErrors({ selects: [pending.index] });
      }
      return;
    }
    const validation = validateUpdate(pending.index);
    if (validation) {
      setError(validation);
      setFieldErrors({ desc: pending.index });
      return;
    }
    try {
      await api.save(row.trType, descEdits[pending.index].trim(), false);
      await refresh({ info: MSG.ctliUpdated });
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'NOT_FOUND') setError(ctliDb2Message(MSG.ctliUpdateNotFound, 100));
      else if (err.code === 'LOCK_ERROR') setError(ctliDb2Message(MSG.ctliDeadlock, -911));
      else if (err.code === 'VALIDATION_ERROR') setError(err.message);
      else setError(ctliDb2Message(MSG.ctliUpdateFailed, err.sqlcode ?? -1));
    }
  };

  const f7 = () => {
    if (paging.pageNumber <= 1) {
      setError(MSG.noPrevPages);
      return;
    }
    void load(applied, paging.firstKey, 'backward', paging.pageNumber - 1);
  };

  const f8 = async () => {
    if (paging.hasNext) {
      const res = await load(applied, paging.nextCursor, 'forward', paging.pageNumber + 1);
      if (res && !res.hasNext && !res.message) {
        setError(MSG.noMorePagesForSearch);
        setPaging((p) => ({ ...p, lastPageShown: true }));
      }
      return;
    }
    if (paging.lastPageShown) {
      setError(MSG.noMorePages);
      return;
    }
    setError(MSG.noMorePagesForSearch);
    setInfo(rows.length ? MSG.ctliDefaultInfo : null);
    setPaging((p) => ({ ...p, lastPageShown: true }));
  };

  const setAt = (list: string[], index: number, value: string) => list.map((v, i) => (i === index ? value : v));

  return (
    <Box component="main" p={3} maxWidth={900}>
      <ScreenHeader tranId="CTLI" programName="COTRTLIC" title="Maintain Transaction Type" />
      <Box sx={{ textAlign: 'right', fontFamily: 'monospace' }} data-testid="pageno">
        Page {paging.pageNumber}
      </Box>
      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <TextField
          label="Type Filter:"
          size="small"
          value={filters.type}
          onChange={(e) => setFilters({ ...filters, type: e.target.value })}
          disabled={!!pending}
          error={!!fieldErrors.typeFilter}
          inputProps={{ maxLength: 2, 'aria-label': 'Type Filter' }}
          autoFocus
        />
        <TextField
          label="Description Filter:"
          size="small"
          sx={{ flex: 1 }}
          value={filters.desc}
          onChange={(e) => setFilters({ ...filters, desc: e.target.value })}
          disabled={!!pending}
          error={!!fieldErrors.descFilter}
          inputProps={{ maxLength: 50, 'aria-label': 'Description Filter' }}
        />
      </Box>
      <Table size="small" aria-label="transaction types">
        <TableHead>
          <TableRow>
            <TableCell>Select</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Description</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {Array.from({ length: PAGE_SIZE }, (_, i) => {
            const row = rows[i];
            const highlighted = pending?.index === i;
            const editing = pending?.kind === 'U' && pending.index === i;
            return (
              <TableRow key={i} selected={highlighted} data-testid={`row-${i + 1}`}>
                <TableCell sx={{ width: 90 }}>
                  {row && (
                    <TextField
                      size="small"
                      value={selects[i]}
                      onChange={(e) => setSelects(setAt(selects, i, e.target.value.toUpperCase()))}
                      error={fieldErrors.selects?.includes(i)}
                      disabled={!!fieldErrors.typeFilter}
                      inputProps={{ maxLength: 1, 'aria-label': `Select row ${i + 1}` }}
                    />
                  )}
                </TableCell>
                <TableCell sx={{ fontFamily: 'monospace' }}>{row?.trType ?? ''}</TableCell>
                <TableCell>
                  {row &&
                    (editing ? (
                      <TextField
                        size="small"
                        fullWidth
                        value={descEdits[i]}
                        onChange={(e) => setDescEdits(setAt(descEdits, i, e.target.value))}
                        error={fieldErrors.desc === i}
                        inputProps={{ maxLength: 50, 'aria-label': `Description row ${i + 1}` }}
                        autoFocus
                      />
                    ) : (
                      <span>{row.description}</span>
                    ))}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
      <MessageBanner info={info} error={error} />
      <FunctionKeys
        keys={[
          { key: 'Enter', label: 'ENTER', onPress: enter },
          { key: 'F2', label: 'F2=Add', onPress: () => navigate('/transaction-types/maintain', { state: { from: 'list' } }) },
          { key: 'F3', label: 'F3=Exit', onPress: () => navigate('/admin') },
          { key: 'F7', label: 'F7=Page Up', onPress: f7 },
          { key: 'F8', label: 'F8=Page Dn', onPress: () => void f8() },
          { key: 'F10', label: 'F10=Save', onPress: () => void f10() },
        ]}
        onOtherKey={() => enter()}
      />
    </Box>
  );
}
