import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Button, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField,
} from '@mui/material';
import { api, ApiError, type PageQuery, type TransactionTypePage } from '../api/client';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { descriptionError, sameDescription, typeFilterError } from '../validation';

/** COTRTLIC (CTLI) messages, verbatim. */
export const MSG = {
  recActions: 'Type U to update, D to delete any record',
  deletePrompt: 'Delete HIGHLIGHTED row ? Press F10 to confirm',
  updatePrompt: 'Update HIGHLIGHTED row. Press F10 to save',
  deleteSuccess: 'HIGHLIGHTED row deleted.Hit Enter to continue',
  updateSuccess: 'HIGHLIGHTED row was updated',
  exit: 'PF03 pressed. Exiting',
  moreThanOne: 'Please select only 1 action',
  invalidAction: 'Action code selected is invalid',
  noChanges: 'No change detected with respect to database values.',
  noPrevious: 'No previous pages to display',
  noMore: 'No more pages to display',
};

export const PAGE_SIZE = 7;

type Pending = { kind: 'U' | 'D'; index: number } | null;
type Filters = { typeFilter: string; descFilter: string };

export function TransactionTypeListPage() {
  const navigate = useNavigate();
  const [filters, setFilters] = useState<Filters>({ typeFilter: '', descFilter: '' });
  const [applied, setApplied] = useState<Filters>({ typeFilter: '', descFilter: '' });
  const [query, setQuery] = useState<PageQuery>({ page: 1 });
  const [data, setData] = useState<TransactionTypePage | null>(null);
  const [actions, setActions] = useState<string[]>([]);
  const [descs, setDescs] = useState<string[]>([]);
  const [pending, setPending] = useState<Pending>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (q: PageQuery, f: Filters, infoAfter: string = MSG.recActions) => {
    try {
      const page = await api.list({ ...q, ...f, size: PAGE_SIZE });
      setData(page);
      setQuery(q);
      setApplied(f);
      setActions(page.rows.map(() => ''));
      setDescs(page.rows.map((r) => r.trDescription));
      setPending(null);
      setError(page.message);
      setInfo(page.rows.length ? infoAfter : null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    void load({ page: 1 }, { typeFilter: '', descFilter: '' });
  }, [load]);

  const rows = data?.rows ?? [];

  const enter = () => {
    const filterErr = typeFilterError(filters.typeFilter);
    if (filterErr) {
      setError(filterErr);
      return;
    }
    if (filters.typeFilter.trim() !== applied.typeFilter.trim()
      || filters.descFilter.trim() !== applied.descFilter.trim()) {
      void load({ page: 1 }, filters);
      return;
    }
    const selected = actions.map((a, i) => ({ a: a.trim().toUpperCase(), i })).filter((x) => x.a);
    if (selected.some((x) => x.a !== 'U' && x.a !== 'D')) {
      setError(MSG.invalidAction);
      return;
    }
    if (selected.length > 1) {
      setError(MSG.moreThanOne);
      return;
    }
    if (selected.length === 0) {
      void load(query, applied);
      return;
    }
    const { a, i } = selected[0];
    setError(null);
    setPending({ kind: a as 'U' | 'D', index: i });
    setInfo(a === 'D' ? MSG.deletePrompt : MSG.updatePrompt);
  };

  const save = async () => {
    if (!pending) {
      enter();
      return;
    }
    const row = rows[pending.index];
    try {
      if (pending.kind === 'D') {
        await api.remove(row.trType);
        await load({ page: 1 }, applied, MSG.deleteSuccess);
      } else {
        const desc = descs[pending.index];
        const err = descriptionError(desc)
          ?? (sameDescription(desc, row.trDescription) ? MSG.noChanges : null);
        if (err) {
          setError(err);
          return;
        }
        await api.updateDescription(row.trType, desc.trim());
        await load(query, applied, MSG.updateSuccess);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    }
  };

  const pageUp = () => {
    if (!data?.hasPrev || !data.firstKey) {
      setError(MSG.noPrevious);
      return;
    }
    void load({ cursor: data.firstKey, direction: 'BACKWARD', page: data.pageNumber - 1 }, applied);
  };

  const pageDown = () => {
    if (!data?.hasNext || !data.nextCursor) {
      setError(MSG.noMore);
      return;
    }
    void load({ cursor: data.nextCursor, direction: 'FORWARD', page: data.pageNumber + 1 }, applied);
  };

  const setAction = (i: number, v: string) => {
    setActions((prev) => prev.map((a, j) => (j === i ? v : a)));
    setPending(null);
  };

  return (
    <Box sx={{ maxWidth: 960, mx: 'auto', p: 2 }}>
      <ScreenHeader tranId="CTLI" program="COTRTLIC" title="Maintain Transaction Type" page={data?.pageNumber ?? 1} />
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Type Filter"
          value={filters.typeFilter}
          onChange={(e) => setFilters({ ...filters, typeFilter: e.target.value })}
          inputProps={{ maxLength: 2, inputMode: 'numeric' }}
          autoFocus
          size="small"
        />
        <TextField
          label="Description Filter"
          value={filters.descFilter}
          onChange={(e) => setFilters({ ...filters, descFilter: e.target.value })}
          inputProps={{ maxLength: 50 }}
          size="small"
          sx={{ flex: 1 }}
        />
      </Stack>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Select</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Description</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((r, i) => (
            <TableRow key={r.trType} selected={pending?.index === i} data-testid={`row-${r.trType}`}>
              <TableCell sx={{ width: 90 }}>
                <TextField
                  value={actions[i] ?? ''}
                  onChange={(e) => setAction(i, e.target.value)}
                  inputProps={{ maxLength: 1, 'aria-label': `action ${r.trType}` }}
                  size="small"
                  error={!!actions[i]?.trim() && !['U', 'D'].includes(actions[i].trim().toUpperCase())}
                />
              </TableCell>
              <TableCell sx={{ width: 80 }}>{r.trType}</TableCell>
              <TableCell>
                <TextField
                  value={descs[i] ?? ''}
                  onChange={(e) => setDescs((prev) => prev.map((d, j) => (j === i ? e.target.value : d)))}
                  disabled={actions[i]?.trim().toUpperCase() !== 'U'}
                  inputProps={{ maxLength: 50, 'aria-label': `description ${r.trType}` }}
                  size="small"
                  fullWidth
                />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <MessageLine info={info} error={error} />
      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
        <Button variant="contained" onClick={enter}>ENTER</Button>
        <Button onClick={() => navigate('/transaction-types/maintain?from=list')}>F2=Add</Button>
        <Button onClick={() => navigate('/admin', { state: { info: MSG.exit } })}>F3=Exit</Button>
        <Button onClick={pageUp}>F7=Page Up</Button>
        <Button onClick={pageDown}>F8=Page Dn</Button>
        <Button onClick={() => void save()} disabled={!pending}>F10=Save</Button>
      </Stack>
    </Box>
  );
}
