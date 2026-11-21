import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CardUpdatePage } from './CardUpdatePage';
import { CardListPage } from './CardPages';
import { loginAsAdmin, mockFetch } from '../test/fetchMock';

const fields = { embossedName: 'OLD NAME', activeStatus: 'Y', expirationMonth: '12', expirationYear: '2030' };
const result = { token: 't', accountId: '10000000001', cardNumber: '4000000000000001', fields, message: 'Update card details presented above.', confirm: false };
it('1200-EDIT-MAP-INPUTS / 3250-SETUP-INFOMSG: a new validation error clears previous no-change information', async () => {
  const noChange = 'No change detected with respect to values fetched.';
  mockFetch((method, _url, body) => {
    if (method === 'GET') return { status: 200, body: result };
    const action = body as { fields: typeof fields };
    return action.fields.embossedName ? { status: 200, body: { ...result, message: noChange } }
      : { status: 400, body: { message: 'Card name not provided' } };
  });
  page();
  await userEvent.click(screen.getByText('Enter=Process'));
  await screen.findByDisplayValue('OLD NAME');
  fireEvent.keyDown(window, { key: 'Enter' });
  await screen.findByText(noChange);
  await userEvent.clear(screen.getByLabelText('Name on Card'));
  fireEvent.keyDown(window, { key: 'Enter' });
  expect(await screen.findByRole('alert')).toHaveTextContent('Card name not provided');
  expect(screen.queryByText(noChange)).not.toBeInTheDocument();
  expect(screen.queryByTestId('infomsg')).not.toBeInTheDocument();
});
function page() {
  loginAsAdmin();
  render(<MemoryRouter initialEntries={['/cards/update']}><Routes>
    <Route path="/cards/update" element={<CardUpdatePage />} /><Route path="/menu" element={<div>MENU</div>} />
  </Routes></MemoryRouter>);
}
it('0000-MAIN / 1000-PROCESS-INPUTS / 1100-RECEIVE-MAP / 3000-SEND-MAP / 3100-SCREEN-INIT / 3200-SETUP-SCREEN-VARS / 3300-SETUP-SCREEN-ATTRS / 3400-SEND-SCREEN', async () => {
  mockFetch(() => ({ status: 200, body: result }));
  page();
  expect(screen.getByText('Please enter Account and Card Number')).toBeInTheDocument();
  expect(screen.getByLabelText('Name on Card')).toHaveAttribute('readonly');
  await userEvent.type(screen.getByLabelText('Account ID'), result.accountId);
  await userEvent.type(screen.getByLabelText('Card Number'), result.cardNumber);
  fireEvent.keyDown(window, { key: 'F5' });
  expect(await screen.findByDisplayValue('OLD NAME')).not.toHaveAttribute('readonly');
  expect(screen.getByLabelText('Account ID')).toHaveAttribute('readonly');
  fireEvent.keyDown(window, { key: 'F3' });
  expect(screen.getByText('MENU')).toBeInTheDocument();
});
it.each(['Enter', 'F3'])('CCLI 0000-MAIN / 9000-READ-FORWARD: %s after save re-browses the current filtered page', async key => {
  loginAsAdmin();
  const row = { accountId: result.accountId, cardNumber: '4000000000000008', activeStatus: 'Y' };
  const listPage = { rows: [row], firstKey: row.cardNumber, nextCursor: null, pageNumber: 2,
    lastPageShown: false, message: null, info: 'TYPE S FOR DETAIL, U TO UPDATE ANY RECORD' };
  const fetch = mockFetch((method, url, body) => {
    if (url.startsWith('/api/cards?')) return { status: 200, body: { ...listPage, rows: [{ ...row, activeStatus: 'N' }] } };
    if (method === 'GET') return { status: 200, body: { ...result, cardNumber: row.cardNumber } };
    const action = body as { key: string; fields: typeof fields };
    return { status: 200, body: { ...result, fields: action.fields, confirm: action.key !== 'PF5',
      message: action.key === 'PF5' ? 'Changes committed to database' : 'Changes validated.Press F5 to save' } };
  });
  render(<MemoryRouter initialEntries={[{ pathname: '/cards/update', state: { ...row,
    list: { accountId: row.accountId, cardNumber: '', page: listPage } } }]}><Routes>
    <Route path="/cards/update" element={<CardUpdatePage />} /><Route path="/cards" element={<CardListPage />} />
  </Routes></MemoryRouter>);
  await screen.findByDisplayValue('OLD NAME');
  await userEvent.clear(screen.getByLabelText('Active Status'));
  await userEvent.type(screen.getByLabelText('Active Status'), 'N');
  fireEvent.keyDown(window, { key: 'Enter' });
  await screen.findByText('Changes validated.Press F5 to save');
  fireEvent.keyDown(window, { key: 'F5' });
  await screen.findByText('Changes committed to database');
  fireEvent.keyDown(window, { key });
  expect(await screen.findByRole('cell', { name: 'N', exact: true })).toBeInTheDocument();
  expect(screen.queryByRole('cell', { name: 'Y', exact: true })).not.toBeInTheDocument();
  expect(screen.getByTestId('pageno')).toHaveTextContent('Page 2');
  expect(screen.getByLabelText('Account ID')).toHaveValue(row.accountId);
  const url = String(fetch.mock.calls.at(-1)?.[0]);
  const params = new URL(url, 'http://test').searchParams;
  expect(params.get('key')).toBe('RETURN');
  expect(params.get('accountId')).toBe(row.accountId);
  expect(params.get('cardNumber')).toBe('');
  expect(params.get('cursor')).toBe(row.cardNumber);
  expect(params.get('pageNumber')).toBe('2');
});
it('1200-EDIT-MAP-INPUTS / 2000-DECIDE-ACTION / 3250-SETUP-INFOMSG: validation, confirmation F5 and F12', async () => {
  mockFetch((method, _url, body) => {
    if (method === 'GET') return { status: 200, body: result };
    const action = body as { key: string; fields: typeof fields };
    if (action.key === 'PF12') return { status: 200, body: result };
    if (action.key === 'PF5') return { status: 200, body: { ...result, message: 'Changes committed to database' } };
    if (!action.fields.embossedName) return { status: 400, body: { message: 'Card name not provided' } };
    return { status: 200, body: { ...result, fields: action.fields, confirm: true, message: 'Changes validated.Press F5 to save' } };
  });
  page();
  await userEvent.click(screen.getByText('Enter=Process'));
  await screen.findByDisplayValue('OLD NAME');
  await userEvent.clear(screen.getByLabelText('Name on Card'));
  await userEvent.click(screen.getByText('Enter=Process'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Card name not provided');
  await userEvent.type(screen.getByLabelText('Name on Card'), 'NEW NAME');
  await userEvent.click(screen.getByText('Enter=Process'));
  expect(await screen.findByText('Changes validated.Press F5 to save')).toBeInTheDocument();
  expect(screen.getByLabelText('Name on Card')).toHaveAttribute('readonly');
  fireEvent.keyDown(window, { key: 'F12' });
  await screen.findByDisplayValue('OLD NAME');
  await userEvent.clear(screen.getByLabelText('Name on Card'));
  await userEvent.type(screen.getByLabelText('Name on Card'), 'NEW NAME');
  await userEvent.click(screen.getByText('Enter=Process'));
  await screen.findByText('Changes validated.Press F5 to save');
  fireEvent.keyDown(window, { key: 'F5' });
  expect(await screen.findByText('Changes committed to database')).toBeInTheDocument();
  for (const label of ['Name on Card', 'Active Status', 'Expiration Month', 'Expiration Year']) {
    expect(screen.getByLabelText(label)).toHaveAttribute('readonly');
    const value = (screen.getByLabelText(label) as HTMLInputElement).value;
    await userEvent.type(screen.getByLabelText(label), 'X');
    expect(screen.getByLabelText(label)).toHaveValue(value);
  }
  fireEvent.keyDown(window, { key: 'Enter' });
  expect(screen.getByText('Please enter Account and Card Number')).toBeInTheDocument();
  expect(screen.getByLabelText('Name on Card')).toHaveValue('');
});
