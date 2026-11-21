import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { TransactionAddPage } from './TransactionAddPage';
import { loginAsAdmin, mockFetch } from '../test/fetchMock';

function open() {
  loginAsAdmin();
  render(<MemoryRouter><Routes><Route path="/" element={<TransactionAddPage />} /><Route path="/menu" element={<div>MAIN MENU</div>} /></Routes></MemoryRouter>);
}
it('MAIN-PARA POPULATE-HEADER-INFO RECEIVE/SEND-TRNADD-SCREEN validation and confirmation', async () => {
  mockFetch((_method, _url, body) => ({ status: 200, body: { fields: body, message: 'Confirm to add this transaction...', field: 'confirmation', added: false } }));
  open();
  expect(screen.getByText(/COTRN02C/)).toBeInTheDocument();
  await userEvent.type(screen.getByLabelText('Account ID'), '00000000001');
  fireEvent.keyDown(window, { key: 'Enter' });
  expect(await screen.findByRole('alert')).toHaveTextContent('Confirm to add this transaction...');
  expect(screen.getByLabelText('Account ID')).toHaveValue('00000000001');
});
it('COPY-LAST-TRAN-DATA CLEAR-CURRENT-SCREEN INITIALIZE-ALL-FIELDS RETURN-TO-PREV-SCREEN', async () => {
  const fetch = mockFetch(() => ({ status: 200, body: { fields: { description: 'COPIED' }, message: 'Confirm to add this transaction...', field: 'confirmation', added: false } }));
  open();
  fireEvent.keyDown(window, { key: 'F5' });
  expect(await screen.findByDisplayValue('COPIED')).toBeInTheDocument();
  expect(fetch.mock.calls[0][0]).toContain('copyLast=true');
  fireEvent.keyDown(window, { key: 'F4' });
  expect(screen.getByLabelText('Description')).toHaveValue('');
  fireEvent.keyDown(window, { key: 'F3' });
  expect(screen.getByText('MAIN MENU')).toBeInTheDocument();
});
it('WRITE-TRANSACT-FILE clears fields and displays success; transport failure is visible', async () => {
  mockFetch(() => ({ status: 200, body: { fields: {}, message: 'Transaction added successfully.  Your Tran ID is 0000000000000001.', field: 'account_id', added: true } }));
  open();
  fireEvent.keyDown(window, { key: 'Enter' });
  expect(await screen.findByText(/Transaction added successfully/)).toBeInTheDocument();
  mockFetch(() => ({ status: 500, body: { message: 'Unable to Add Transaction...' } }));
  fireEvent.keyDown(window, { key: 'Enter' });
  expect(await screen.findByRole('alert')).toHaveTextContent('Unable to Add Transaction...');
});
