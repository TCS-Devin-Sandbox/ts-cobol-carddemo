import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import * as api from '../api/transactionTypes';
import { TransactionTypeEditPage } from './TransactionTypeEditPage';
import { renderAt } from '../test/utils';
import { MSG } from '../logic/messages';

vi.mock('../api/transactionTypes', async (orig) => {
  const actual = await orig<typeof import('../api/transactionTypes')>();
  return {
    ...actual,
    getTransactionType: vi.fn(),
    createTransactionType: vi.fn(),
    updateTransactionType: vi.fn(),
    deleteTransactionType: vi.fn(),
  };
});

const get = vi.mocked(api.getTransactionType);
const create = vi.mocked(api.createTransactionType);
const update = vi.mocked(api.updateTransactionType);
const remove = vi.mocked(api.deleteTransactionType);

const info = () => screen.getByTestId('info-message');
const error = () => screen.getByTestId('error-message');
const keyField = () => screen.getByTestId('tr-type');
const descField = () => screen.getByTestId('tr-description');
const btn = (name: string) => screen.getByRole('button', { name });

const notFound = { code: 'NOT_FOUND', message: MSG.RECORD_NOT_FOUND, status: 404 };

async function show(key = '1', description = 'PURCHASE') {
  get.mockResolvedValue({ trType: key.padStart(2, '0'), trDescription: description });
  await userEvent.type(keyField(), key);
  await userEvent.click(btn('Enter=Process'));
  await waitFor(() => expect(descField()).toHaveValue(description));
}

beforeEach(() => {
  vi.clearAllMocks();
  renderAt('/transaction-types/maintain', <TransactionTypeEditPage />);
});

describe('TransactionTypeEditPage (CTTU)', () => {
  it('starts in search state (TP-30)', () => {
    expect(info()).toHaveTextContent(MSG.PROMPT_SEARCH);
    expect(keyField()).toBeEnabled();
    expect(descField()).toBeDisabled();
    expect(btn('F4=Delete')).toBeDisabled();
    expect(btn('F5=Save')).toBeDisabled();
    expect(screen.getByTestId('program-name')).toHaveTextContent('COTRTUPC');
  });

  it('validates the key with the source messages (TP-31)', async () => {
    await userEvent.click(btn('Enter=Process'));
    expect(error()).toHaveTextContent(MSG.CODE_REQUIRED);
    await userEvent.type(keyField(), 'A1');
    await userEvent.click(btn('Enter=Process'));
    expect(error()).toHaveTextContent(MSG.CODE_NUMERIC);
    await userEvent.clear(keyField());
    await userEvent.type(keyField(), '00');
    await userEvent.click(btn('Enter=Process'));
    expect(error()).toHaveTextContent(MSG.CODE_NOT_ZERO);
    expect(get).not.toHaveBeenCalled();
  });

  it('shows an existing record with the key protected (TP-32, TP-33)', async () => {
    await show('1');
    expect(get).toHaveBeenCalledWith('01');
    expect(keyField()).toHaveValue('01');
    expect(keyField()).toBeDisabled();
    expect(descField()).toBeEnabled();
    expect(btn('F4=Delete')).toBeEnabled();
  });

  it('edits: no change, invalid, confirm, save with upsert (TP-37..TP-39)', async () => {
    await show('1');
    await userEvent.click(btn('Enter=Process'));
    expect(error()).toHaveTextContent(MSG.NO_CHANGE_FETCHED);

    await userEvent.clear(descField());
    await userEvent.type(descField(), 'BAD!');
    await userEvent.click(btn('Enter=Process'));
    expect(error()).toHaveTextContent(MSG.DESC_ALPHANUM);
    expect(info()).toHaveTextContent(MSG.PROMPT_CHANGES);

    await userEvent.clear(descField());
    await userEvent.type(descField(), 'PURCHASES');
    await userEvent.click(btn('Enter=Process'));
    expect(info()).toHaveTextContent(MSG.PROMPT_CONFIRM_SAVE);
    expect(descField()).toBeDisabled();

    update.mockResolvedValue({ trType: '01', trDescription: 'PURCHASES' });
    await userEvent.keyboard('{F5}');
    await waitFor(() => expect(info()).toHaveTextContent(MSG.UPDATE_SUCCESS));
    expect(update).toHaveBeenCalledWith('01', 'PURCHASES', true);

    await userEvent.click(btn('Enter=Process'));
    expect(info()).toHaveTextContent(MSG.PROMPT_SEARCH);
    expect(keyField()).toHaveValue('');
  });

  it('adds a new type from the not-found state (TP-34, TP-35, TP-40)', async () => {
    get.mockRejectedValue(notFound);
    await userEvent.type(keyField(), '42');
    await userEvent.click(btn('Enter=Process'));
    await waitFor(() => expect(error()).toHaveTextContent(MSG.RECORD_NOT_FOUND));
    expect(info()).toHaveTextContent(MSG.PROMPT_CREATE);

    await userEvent.click(btn('F5=Save'));
    expect(info()).toHaveTextContent(MSG.PROMPT_NEWDATA);
    await userEvent.type(descField(), 'NEW TYPE');
    await userEvent.click(btn('Enter=Process'));
    expect(info()).toHaveTextContent(MSG.PROMPT_CONFIRM_SAVE);

    create.mockResolvedValue({ trType: '42', trDescription: 'NEW TYPE' });
    await userEvent.click(btn('F5=Save'));
    await waitFor(() => expect(info()).toHaveTextContent(MSG.UPDATE_SUCCESS));
    expect(create).toHaveBeenCalledWith({ trType: '42', trDescription: 'NEW TYPE' });
  });

  it('shows the duplicate-key message when insert fails (TP-41)', async () => {
    get.mockRejectedValue(notFound);
    await userEvent.type(keyField(), '42');
    await userEvent.click(btn('Enter=Process'));
    await waitFor(() => expect(info()).toHaveTextContent(MSG.PROMPT_CREATE));
    await userEvent.click(btn('F5=Save'));
    await userEvent.type(descField(), 'NEW TYPE');
    await userEvent.click(btn('Enter=Process'));
    create.mockRejectedValue({ code: 'DUPLICATE_KEY', message: 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803' });
    await userEvent.click(btn('F5=Save'));
    await waitFor(() => expect(error()).toHaveTextContent('Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803'));
    expect(info()).toHaveTextContent(MSG.FAILURE);
  });

  it('deletes after F4 confirmation; Enter is invalid while confirming (TP-42, TP-43, TP-48)', async () => {
    await show('9', 'TEMP');
    await userEvent.click(btn('F4=Delete'));
    expect(info()).toHaveTextContent(MSG.PROMPT_DELETE_CONFIRM);
    expect(btn('Enter=Process')).toBeDisabled();
    await userEvent.keyboard('{Enter}');
    expect(error()).toHaveTextContent(MSG.INVALID_KEY);

    remove.mockResolvedValue(undefined);
    await userEvent.keyboard('{F4}');
    await waitFor(() => expect(info()).toHaveTextContent(MSG.DELETE_SUCCESS));
    expect(remove).toHaveBeenCalledWith('09');
  });

  it('reports delete with children (TP-44)', async () => {
    await show('1');
    await userEvent.click(btn('F4=Delete'));
    remove.mockRejectedValue({ code: 'FK_RESTRICT', message: 'Please delete associated child records first: SQLCODE: -532' });
    await userEvent.click(btn('F4=Delete'));
    await waitFor(() => expect(error()).toHaveTextContent('Please delete associated child records first:'));
  });

  it('cancels delete and update with F12 (TP-45, TP-46)', async () => {
    await show('1');
    await userEvent.click(btn('F4=Delete'));
    await userEvent.click(btn('F12=Cancel'));
    expect(error()).toHaveTextContent(MSG.DELETE_CANCELLED);
    expect(info()).toHaveTextContent(MSG.PROMPT_SEARCH);

    await show('1');
    await userEvent.clear(descField());
    await userEvent.type(descField(), 'CHANGED');
    await userEvent.click(btn('Enter=Process'));
    await userEvent.keyboard('{F12}');
    expect(error()).toHaveTextContent(MSG.UPDATE_CANCELLED);
    expect(descField()).toHaveValue('PURCHASE');
    expect(info()).toHaveTextContent(MSG.PROMPT_CHANGES);
  });

  it('F12 from show returns to key entry (TP-47)', async () => {
    await show('1');
    await userEvent.click(btn('F12=Cancel'));
    expect(keyField()).toBeEnabled();
    expect(keyField()).toHaveValue('');
  });

  it('F3 returns to the admin menu (TP-49)', async () => {
    await userEvent.click(btn('F3=Exit'));
    expect(screen.getByText('ADMIN MENU')).toBeInTheDocument();
  });
});
