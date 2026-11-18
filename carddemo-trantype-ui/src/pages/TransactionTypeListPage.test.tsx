import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import * as api from '../api/transactionTypes';
import { TransactionTypePage } from '../api/transactionTypes';
import { TransactionTypeListPage } from './TransactionTypeListPage';
import { renderAt } from '../test/utils';
import { MSG } from '../logic/messages';

vi.mock('../api/transactionTypes', async (orig) => {
  const actual = await orig<typeof import('../api/transactionTypes')>();
  return {
    ...actual,
    listTransactionTypes: vi.fn(),
    updateTransactionType: vi.fn(),
    deleteTransactionType: vi.fn(),
  };
});

const list = vi.mocked(api.listTransactionTypes);
const update = vi.mocked(api.updateTransactionType);
const remove = vi.mocked(api.deleteTransactionType);

const types = (from: number, to: number) =>
  Array.from({ length: to - from + 1 }, (_, i) => ({ trType: String(from + i).padStart(2, '0'), trDescription: `TYPE ${from + i}` }));

const pageOf = (over: Partial<TransactionTypePage>): TransactionTypePage => ({
  rows: types(1, 7), firstKey: '01', lastKey: '07', nextCursor: '08', hasNext: true, hasPrev: false,
  pageNumber: 1, size: 7, infoMessage: MSG.INFO_REC_ACTIONS, errorMessage: null, ...over,
});

const page1 = pageOf({});
const page2 = pageOf({ rows: types(8, 9), firstKey: '08', lastKey: '09', nextCursor: null, hasNext: false, hasPrev: true, pageNumber: 2 });

const info = () => screen.getByTestId('info-message');
const error = () => screen.getByTestId('error-message');

async function renderList() {
  renderAt('/transaction-types', <TransactionTypeListPage />);
  await waitFor(() => expect(screen.getByTestId('type-0')).toHaveTextContent('01'));
}

beforeEach(() => {
  vi.clearAllMocks();
  list.mockResolvedValue(page1);
});

describe('TransactionTypeListPage (CTLI)', () => {
  it('shows the first 7 rows, page number and info line', async () => { // TP-01
    await renderList();
    expect(screen.getByTestId('type-6')).toHaveTextContent('07');
    expect(screen.getByTestId('page-number')).toHaveTextContent('Page 1');
    expect(info()).toHaveTextContent(MSG.INFO_REC_ACTIONS);
    expect(screen.getByTestId('tran-id')).toHaveTextContent('CTLI');
    expect(list).toHaveBeenCalledWith(expect.objectContaining({ size: 7 }));
  });

  it('pages forward with nextCursor and back with firstKey (F8/F7)', async () => { // TP-03, TP-04
    await renderList();
    list.mockResolvedValueOnce(page2);
    await userEvent.click(screen.getByRole('button', { name: 'F8=Page Dn' }));
    await waitFor(() => expect(screen.getByTestId('page-number')).toHaveTextContent('Page 2'));
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: '08', direction: 'next' }));

    list.mockResolvedValueOnce(page1);
    await userEvent.keyboard('{F7}');
    await waitFor(() => expect(screen.getByTestId('page-number')).toHaveTextContent('Page 1'));
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: '08', direction: 'prev' }));
  });

  it('reports page boundaries (TP-05, TP-06)', async () => {
    await renderList();
    await userEvent.click(screen.getByRole('button', { name: 'F7=Page Up' }));
    expect(error()).toHaveTextContent(MSG.NO_PREVIOUS_PAGES);

    list.mockResolvedValueOnce(page2);
    await userEvent.click(screen.getByRole('button', { name: 'F8=Page Dn' }));
    await waitFor(() => expect(screen.getByTestId('page-number')).toHaveTextContent('Page 2'));
    await userEvent.click(screen.getByRole('button', { name: 'F8=Page Dn' }));
    expect(error()).toHaveTextContent(MSG.NO_MORE_PAGES);
  });

  it('shows the empty-table message (TP-07)', async () => {
    list.mockResolvedValue(pageOf({ rows: [], firstKey: null, lastKey: null, nextCursor: null, hasNext: false,
      infoMessage: null, errorMessage: 'No records found for this search condition.' }));
    renderAt('/transaction-types', <TransactionTypeListPage />);
    await waitFor(() => expect(error()).toHaveTextContent('No records found for this search condition.'));
    expect(screen.getByTestId('select-0')).toBeDisabled();
  });

  it('validates the type filter and applies filters on Enter (TP-09, TP-12)', async () => {
    await renderList();
    await userEvent.type(screen.getByTestId('type-filter'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Process' }));
    expect(error()).toHaveTextContent(MSG.TYPE_FILTER_INVALID);

    await userEvent.clear(screen.getByTestId('type-filter'));
    await userEvent.type(screen.getByTestId('type-filter'), '03');
    await userEvent.type(screen.getByTestId('select-0'), 'D');
    list.mockResolvedValueOnce(pageOf({ rows: [{ trType: '03', trDescription: 'CREDIT' }], hasNext: false }));
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Process' }));
    await waitFor(() => expect(screen.getByTestId('type-0')).toHaveTextContent('03'));
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ typeFilter: '03' }));
    expect(list.mock.lastCall?.[0].cursor).toBeUndefined();
    expect(screen.getByTestId('select-0')).toHaveValue('');
  });

  it('enforces valid action codes and the single-action rule (TP-13, TP-14)', async () => {
    await renderList();
    await userEvent.type(screen.getByTestId('select-0'), 'X');
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Process' }));
    expect(error()).toHaveTextContent(MSG.INVALID_ACTION_CODE);

    await userEvent.clear(screen.getByTestId('select-0'));
    await userEvent.type(screen.getByTestId('select-0'), 'U');
    await userEvent.type(screen.getByTestId('select-1'), 'D');
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Process' }));
    expect(error()).toHaveTextContent(MSG.MORE_THAN_ONE_ACTION);
  });

  it('deletes after D + Enter + F10 (TP-15, TP-16)', async () => {
    await renderList();
    remove.mockResolvedValue(undefined);
    await userEvent.type(screen.getByTestId('select-2'), 'd');
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Process' }));
    expect(info()).toHaveTextContent(MSG.INFO_DELETE_CONFIRM);
    expect(remove).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'F10=Save' }));
    await waitFor(() => expect(info()).toHaveTextContent(MSG.INFO_DELETE_SUCCESS));
    expect(remove).toHaveBeenCalledWith('03');
    expect(list.mock.lastCall?.[0].cursor).toBeUndefined();
  });

  it('shows the FK restrict message when the type has children (TP-17)', async () => {
    await renderList();
    remove.mockRejectedValue({ code: 'FK_RESTRICT', message: 'Please delete associated child records first: SQLCODE: -532' });
    await userEvent.type(screen.getByTestId('select-0'), 'D');
    await userEvent.keyboard('{Enter}');
    await userEvent.keyboard('{F10}');
    await waitFor(() => expect(error()).toHaveTextContent('Please delete associated child records first:'));
  });

  it('updates inline after U + Enter + edit + F10, validating first (TP-19..TP-22)', async () => {
    await renderList();
    update.mockResolvedValue({ trType: '02', trDescription: 'PAYMENTS' });
    const desc = screen.getByTestId('desc-1');
    expect(desc).toHaveAttribute('readonly');

    await userEvent.type(screen.getByTestId('select-1'), 'U');
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Process' }));
    expect(info()).toHaveTextContent(MSG.INFO_UPDATE_CONFIRM);
    expect(error()).toHaveTextContent(MSG.NO_CHANGE_DATABASE);
    expect(desc).not.toHaveAttribute('readonly');

    await userEvent.clear(desc);
    await userEvent.type(desc, 'BAD-VALUE');
    await userEvent.click(screen.getByRole('button', { name: 'F10=Save' }));
    expect(error()).toHaveTextContent(MSG.DESC_ALPHANUM);
    expect(update).not.toHaveBeenCalled();

    await userEvent.clear(desc);
    await userEvent.type(desc, 'PAYMENTS');
    await userEvent.click(screen.getByRole('button', { name: 'F10=Save' }));
    await waitFor(() => expect(info()).toHaveTextContent(MSG.INFO_UPDATE_SUCCESS));
    expect(update).toHaveBeenCalledWith('02', 'PAYMENTS');
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: '01', direction: 'next' }));
  });

  it('treats F10 without a pending action as Enter (TP-26)', async () => {
    await renderList();
    await userEvent.click(screen.getByRole('button', { name: 'F10=Save' }));
    expect(remove).not.toHaveBeenCalled();
    expect(update).not.toHaveBeenCalled();
  });

  it('navigates to add (F2) and exits to the admin menu (F3) (TP-27, TP-28)', async () => {
    await renderList();
    await userEvent.keyboard('{F2}');
    expect(screen.getByText('CTTU PAGE')).toBeInTheDocument();
  });

  it('exits to the admin menu on F3 (TP-28)', async () => {
    await renderList();
    await userEvent.click(screen.getByRole('button', { name: 'F3=Exit' }));
    expect(screen.getByText('ADMIN MENU')).toBeInTheDocument();
  });
});
