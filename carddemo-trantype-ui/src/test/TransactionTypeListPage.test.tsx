import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { TransactionTypeListPage } from '../pages/TransactionTypeListPage';
import { apiError, mockApi, page, renderAt, SEED } from './helpers';

const PAGE2 = [
  { trType: '08', description: 'TYPE 8' },
  { trType: '09', description: 'TYPE 9' },
];

function setup(list = vi.fn().mockResolvedValue(page(SEED)), extra = {}) {
  const api = mockApi({ list, ...extra });
  renderAt(api, '/transaction-types', <TransactionTypeListPage />);
  return api;
}

const info = () => screen.getByTestId('infomsg');
const err = () => screen.getByTestId('errmsg');
const click = (name: string) => userEvent.click(screen.getByRole('button', { name }));

describe('TransactionTypeListPage (CTLI)', () => {
  it('loads first page of 7 rows with page number and default info', async () => {
    const api = setup();
    expect(await screen.findByText('PURCHASE')).toBeInTheDocument();
    expect(screen.getByText('ADJUSTMENT')).toBeInTheDocument();
    expect(screen.getByTestId('pageno')).toHaveTextContent('Page 1');
    expect(info()).toHaveTextContent('Type U to update, D to delete any record');
    expect(screen.getByTestId('tranid')).toHaveTextContent('CTLI');
    expect(screen.getByTestId('pgmname')).toHaveTextContent('COTRTLIC');
    expect(api.list).toHaveBeenCalledWith(expect.objectContaining({ cursor: null, direction: 'forward', page: 1, size: 7 }));
  });

  it('pages forward with the look-ahead cursor and backward with the first key', async () => {
    const list = vi
      .fn()
      .mockResolvedValueOnce(page(SEED, { hasNext: true, nextCursor: '08' }))
      .mockResolvedValueOnce(page(PAGE2, { hasPrev: true, pageNumber: 2 }))
      .mockResolvedValueOnce(page(SEED, { hasNext: true, nextCursor: '08' }));
    setup(list);
    await screen.findByText('PURCHASE');
    await click('F8=Page Dn');
    expect(await screen.findByText('TYPE 8')).toBeInTheDocument();
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: '08', direction: 'forward', page: 2 }));
    expect(screen.getByTestId('pageno')).toHaveTextContent('Page 2');
    expect(err()).toHaveTextContent('No more pages for these search conditions');
    await click('F8=Page Dn');
    expect(err()).toHaveTextContent('No more pages to display');
    await click('F7=Page Up');
    expect(await screen.findByText('PURCHASE')).toBeInTheDocument();
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: '08', direction: 'backward', page: 1 }));
  });

  it('F7 on first page shows no previous pages', async () => {
    setup();
    await screen.findByText('PURCHASE');
    await click('F7=Page Up');
    expect(err()).toHaveTextContent('No previous pages to display');
  });

  it('physical F8 key is bound', async () => {
    setup();
    await screen.findByText('PURCHASE');
    fireEvent.keyDown(window, { key: 'F8' });
    expect(err()).toHaveTextContent('No more pages for these search conditions');
    fireEvent.keyDown(window, { key: 'F8' });
    expect(err()).toHaveTextContent('No more pages to display');
  });

  it('empty table shows source message', async () => {
    setup(vi.fn().mockResolvedValue(page([], { message: 'No records found for this search condition.' })));
    await waitFor(() => expect(err()).toHaveTextContent('No records found for this search condition.'));
    expect(screen.queryAllByLabelText(/Select row/)).toHaveLength(0);
  });

  it('invalid type filter rejected with source literal', async () => {
    const list = vi.fn().mockResolvedValue(page(SEED));
    setup(list);
    await screen.findByText('PURCHASE');
    await userEvent.type(screen.getByLabelText('Type Filter'), '5x');
    await click('ENTER');
    expect(err()).toHaveTextContent('TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER');
    expect(list).toHaveBeenCalledTimes(1);
  });

  it('filter change reloads page 1 with filters', async () => {
    const list = vi.fn().mockResolvedValueOnce(page(SEED)).mockResolvedValueOnce(page([SEED[2]]));
    setup(list);
    await screen.findByText('PURCHASE');
    await userEvent.type(screen.getByLabelText('Type Filter'), '03');
    await userEvent.type(screen.getByLabelText('Description Filter'), 'CRED');
    await click('ENTER');
    await waitFor(() => expect(screen.queryByText('PURCHASE')).not.toBeInTheDocument());
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: null, page: 1, typeFilter: '03', descFilter: 'CRED' }));
  });

  it('rejects invalid action code and more than one action', async () => {
    setup();
    await screen.findByText('PURCHASE');
    await userEvent.type(screen.getByLabelText('Select row 1'), 'X');
    await click('ENTER');
    expect(err()).toHaveTextContent('Action code selected is invalid');
    await userEvent.clear(screen.getByLabelText('Select row 1'));
    await userEvent.type(screen.getByLabelText('Select row 1'), 'U');
    await userEvent.type(screen.getByLabelText('Select row 2'), 'D');
    await click('ENTER');
    expect(err()).toHaveTextContent('Please select only 1 action');
  });

  it('delete requires F10 confirmation and resets to page 1', async () => {
    const remove = vi.fn().mockResolvedValue(undefined);
    const list = vi.fn().mockResolvedValue(page(SEED));
    setup(list, { remove });
    await screen.findByText('PURCHASE');
    await userEvent.type(screen.getByLabelText('Select row 7'), 'd');
    await click('ENTER');
    expect(info()).toHaveTextContent('Delete HIGHLIGHTED row ? Press F10 to confirm');
    expect(screen.getByLabelText('Type Filter')).toBeDisabled();
    expect(remove).not.toHaveBeenCalled();
    await click('F10=Save');
    expect(remove).toHaveBeenCalledWith('07');
    await waitFor(() => expect(info()).toHaveTextContent('HIGHLIGHTED row deleted.Hit Enter to continue'));
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: null, page: 1 }));
  });

  it('delete with children shows -532 message', async () => {
    const remove = vi.fn().mockRejectedValue(apiError(409, 'CHILD_RECORDS_EXIST', 'Please delete associated child records first:', -532));
    setup(undefined, { remove });
    await screen.findByText('PURCHASE');
    await userEvent.type(screen.getByLabelText('Select row 1'), 'D');
    await click('ENTER');
    await click('F10=Save');
    await waitFor(() => expect(err()).toHaveTextContent('Please delete associated child records first: SQLCODE: -532'));
  });

  it('inline update validates then saves with upsert=false', async () => {
    const save = vi.fn().mockResolvedValue({ trType: '02', description: 'PAYMENT NEW' });
    setup(undefined, { save });
    await screen.findByText('PURCHASE');
    await userEvent.type(screen.getByLabelText('Select row 2'), 'U');
    await click('ENTER');
    expect(info()).toHaveTextContent('Update HIGHLIGHTED row. Press F10 to save');
    const desc = screen.getByLabelText('Description row 2');
    await click('F10=Save');
    expect(err()).toHaveTextContent('No change detected with respect to database values.');
    await userEvent.clear(desc);
    await click('F10=Save');
    expect(err()).toHaveTextContent('Transaction Desc must be supplied.');
    await userEvent.type(desc, 'BAD-DESC');
    await click('F10=Save');
    expect(err()).toHaveTextContent('Transaction Desc can have numbers or alphabets only.');
    expect(save).not.toHaveBeenCalled();
    await userEvent.clear(desc);
    await userEvent.type(desc, 'PAYMENT NEW');
    await click('F10=Save');
    expect(save).toHaveBeenCalledWith('02', 'PAYMENT NEW', false);
    await waitFor(() => expect(info()).toHaveTextContent('HIGHLIGHTED row was updated'));
  });

  it('update of a row deleted by others shows +100 message', async () => {
    const save = vi.fn().mockRejectedValue(apiError(404, 'NOT_FOUND', 'Record not found. Deleted by others ? ', 100));
    setup(undefined, { save });
    await screen.findByText('PURCHASE');
    await userEvent.type(screen.getByLabelText('Select row 1'), 'U');
    await click('ENTER');
    await userEvent.type(screen.getByLabelText('Description row 1'), ' X');
    await click('F10=Save');
    await waitFor(() => expect(err()).toHaveTextContent('Record not found. Deleted by others ? SQLCODE: 100'));
  });

  it('F2 goes to CTTU and F3 to the admin menu', async () => {
    setup();
    await screen.findByText('PURCHASE');
    await click('F2=Add');
    expect(screen.getByTestId('location')).toHaveTextContent('/transaction-types/maintain');
  });

  it('F3 exits to admin menu', async () => {
    setup();
    await screen.findByText('PURCHASE');
    await click('F3=Exit');
    expect(screen.getByTestId('location')).toHaveTextContent('/admin');
  });

  it('keys other than ENTER/F2/F3/F7/F8/F10 act as ENTER (refresh)', async () => {
    const list = vi.fn().mockResolvedValue(page(SEED));
    setup(list);
    await screen.findByText('PURCHASE');
    fireEvent.keyDown(window, { key: 'F5' });
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: '01', direction: 'forward', page: 1 }));
  });

  it('database failure is shown on the error line', async () => {
    setup(vi.fn().mockRejectedValue(apiError(500, 'INTERNAL_ERROR', 'Error accessing: TRANSACTION_TYPE table. SQLCODE:', -1)));
    await waitFor(() => expect(err()).toHaveTextContent('Error accessing: TRANSACTION_TYPE table. SQLCODE:'));
  });
});
