import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { TransactionTypeEditPage } from '../pages/TransactionTypeEditPage';
import { apiError, mockApi, renderAt } from './helpers';

const notFound = () => apiError(404, 'NOT_FOUND', 'No record found for this key in database', 100);

function setup(extra = {}, from?: string) {
  const api = mockApi({
    get: vi.fn().mockImplementation((key: string) =>
      key === '01' ? Promise.resolve({ trType: '01', description: 'PURCHASE' }) : Promise.reject(notFound()),
    ),
    ...extra,
  });
  renderAt(api, '/transaction-types/maintain', <TransactionTypeEditPage />, '/transaction-types/maintain', from ? { from } : undefined);
  return api;
}

const info = () => screen.getByTestId('infomsg');
const err = () => screen.getByTestId('errmsg');
const state = () => screen.getByTestId('cttu-form').getAttribute('data-state');
const click = (name: string) => userEvent.click(screen.getByRole('button', { name }));
const typeField = () => screen.getByLabelText('Transaction Type');
const descField = () => screen.getByLabelText('Description');
const buttons = () => screen.getAllByRole('button').map((b) => b.textContent);

describe('TransactionTypeEditPage (CTTU)', () => {
  it('initial state prompts for key with ENTER and F3 only', async () => {
    setup();
    await waitFor(() => expect(info()).toHaveTextContent('Enter transaction type to be maintained'));
    expect(screen.getByTestId('tranid')).toHaveTextContent('CTTU');
    expect(buttons()).toEqual(['ENTER=Process', 'F3=Exit']);
    expect(descField()).toHaveAttribute('readonly');
    expect(typeField()).not.toHaveAttribute('readonly');
    expect(typeField()).toHaveAttribute('maxlength', '2');
  });

  it('type code edits use source literals', async () => {
    setup();
    await click('ENTER=Process');
    expect(err()).toHaveTextContent('Tran Type code must be supplied.');
    await userEvent.type(typeField(), 'ab');
    await click('ENTER=Process');
    expect(err()).toHaveTextContent('Tran Type code must be numeric.');
    await userEvent.clear(typeField());
    await userEvent.type(typeField(), '0');
    await click('ENTER=Process');
    expect(err()).toHaveTextContent('Tran Type code must not be zero.');
  });

  it('existing key shows details, zero-padded, description editable', async () => {
    const api = setup();
    await userEvent.type(typeField(), '1');
    await click('ENTER=Process');
    await waitFor(() => expect(state()).toBe('SHOW_DETAILS'));
    expect(api.get).toHaveBeenCalledWith('01');
    expect(typeField()).toHaveValue('01');
    expect(descField()).toHaveValue('PURCHASE');
    expect(typeField()).toHaveAttribute('readonly');
    expect(descField()).not.toHaveAttribute('readonly');
    expect(buttons()).toEqual(['ENTER=Process', 'F3=Exit', 'F4=Delete', 'F12=Cancel']);
  });

  it('edit: no change, invalid, validated, saved (upsert)', async () => {
    const save = vi.fn().mockResolvedValue({ trType: '01', description: 'PURCHASES' });
    setup({ save });
    await userEvent.type(typeField(), '01');
    await click('ENTER=Process');
    await waitFor(() => expect(state()).toBe('SHOW_DETAILS'));
    await userEvent.clear(descField());
    await userEvent.type(descField(), 'purchase ');
    await click('ENTER=Process');
    expect(err()).toHaveTextContent('No change detected with respect to values fetched.');
    await userEvent.clear(descField());
    await userEvent.type(descField(), 'PURCHASE*');
    await click('ENTER=Process');
    expect(err()).toHaveTextContent('Transaction Desc can have numbers or alphabets only.');
    expect(info()).toHaveTextContent('Update transaction type details shown.');
    await userEvent.clear(descField());
    await userEvent.type(descField(), 'PURCHASES');
    await click('ENTER=Process');
    expect(info()).toHaveTextContent('Changes validated.Press F5 to save');
    expect(descField()).toHaveAttribute('readonly');
    expect(buttons()).toEqual(['ENTER=Process', 'F3=Exit', 'F5=Save', 'F12=Cancel']);
    await click('F5=Save');
    expect(save).toHaveBeenCalledWith('01', 'PURCHASES', true);
    await waitFor(() => expect(info()).toHaveTextContent('Changes committed to database'));
    await click('ENTER=Process');
    expect(state()).toBe('DETAILS_NOT_FETCHED');
    expect(typeField()).toHaveValue('');
  });

  it('F12 after validation backs out the update', async () => {
    setup();
    await userEvent.type(typeField(), '01');
    await click('ENTER=Process');
    await waitFor(() => expect(state()).toBe('SHOW_DETAILS'));
    await userEvent.type(descField(), ' X');
    await click('ENTER=Process');
    await click('F12=Cancel');
    expect(err()).toHaveTextContent('Update was cancelled');
    expect(descField()).toHaveValue('PURCHASE');
    expect(info()).toHaveTextContent('Update transaction type details shown.');
  });

  it('non-existent key then add via F5', async () => {
    const save = vi.fn().mockResolvedValue({ trType: '42', description: 'NEW TYPE' });
    setup({ save });
    await userEvent.type(typeField(), '42');
    await click('ENTER=Process');
    await waitFor(() => expect(err()).toHaveTextContent('No record found for this key in database'));
    expect(info()).toHaveTextContent('Press F05 to add. F12 to cancel');
    expect(buttons()).toEqual(['ENTER=Process', 'F3=Exit', 'F5=Save', 'F12=Cancel']);
    await click('F5=Save');
    expect(info()).toHaveTextContent('Enter new transaction type details.');
    expect(typeField()).toHaveAttribute('readonly');
    await click('ENTER=Process');
    expect(err()).toHaveTextContent('Transaction Desc must be supplied.');
    await userEvent.type(descField(), 'NEW TYPE');
    await click('ENTER=Process');
    await click('F5=Save');
    expect(save).toHaveBeenCalledWith('42', 'NEW TYPE', true);
    await waitFor(() => expect(info()).toHaveTextContent('Changes committed to database'));
  });

  it('duplicate key on insert shows -803 message', async () => {
    const save = vi
      .fn()
      .mockRejectedValue(apiError(409, 'DUPLICATE_KEY', 'Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:', -803));
    setup({ save });
    await userEvent.type(typeField(), '42');
    await click('ENTER=Process');
    await waitFor(() => expect(state()).toBe('DETAILS_NOT_FOUND'));
    await click('F5=Save');
    await userEvent.type(descField(), 'DUP');
    await click('ENTER=Process');
    await click('F5=Save');
    await waitFor(() =>
      expect(err()).toHaveTextContent('Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803:'),
    );
    expect(info()).toHaveTextContent('Changes unsuccessful');
  });

  it('delete confirm with F4, success', async () => {
    const remove = vi.fn().mockResolvedValue(undefined);
    setup({ remove });
    await userEvent.type(typeField(), '01');
    await click('ENTER=Process');
    await waitFor(() => expect(state()).toBe('SHOW_DETAILS'));
    await click('F4=Delete');
    expect(info()).toHaveTextContent('Delete this record ? Press F4 to confirm');
    expect(buttons()).toEqual(['F3=Exit', 'F4=Delete', 'F12=Cancel']);
    expect(remove).not.toHaveBeenCalled();
    await click('F4=Delete');
    expect(remove).toHaveBeenCalledWith('01');
    await waitFor(() => expect(info()).toHaveTextContent('Delete successful.'));
  });

  it('delete with children shows -532 message', async () => {
    const remove = vi
      .fn()
      .mockRejectedValue(apiError(409, 'CHILD_RECORDS_EXIST', 'Please delete associated child records first:', -532));
    setup({ remove });
    await userEvent.type(typeField(), '01');
    await click('ENTER=Process');
    await waitFor(() => expect(state()).toBe('SHOW_DETAILS'));
    await click('F4=Delete');
    await click('F4=Delete');
    await waitFor(() => expect(err()).toHaveTextContent('Please delete associated child records first:SQLCODE : -532:'));
  });

  it('F12 on delete confirm cancels', async () => {
    setup();
    await userEvent.type(typeField(), '01');
    await click('ENTER=Process');
    await waitFor(() => expect(state()).toBe('SHOW_DETAILS'));
    await click('F4=Delete');
    await click('F12=Cancel');
    expect(err()).toHaveTextContent('Delete was cancelled');
    expect(state()).toBe('DETAILS_NOT_FETCHED');
  });

  it('key invalid for the state is rejected', async () => {
    setup();
    await waitFor(() => expect(info()).toHaveTextContent('Enter transaction type to be maintained'));
    fireEvent.keyDown(window, { key: 'F4' });
    expect(err()).toHaveTextContent('Invalid key pressed');
  });

  it('F3 returns to the list when entered from CTLI', async () => {
    setup({}, 'list');
    await click('F3=Exit');
    expect(screen.getByTestId('location')).toHaveTextContent('/transaction-types');
  });

  it('F3 returns to the admin menu otherwise', async () => {
    setup({}, 'menu');
    await click('F3=Exit');
    expect(screen.getByTestId('location')).toHaveTextContent('/admin');
  });
});
