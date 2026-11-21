import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CardUpdatePage } from './CardUpdatePage';
import { loginAsAdmin, mockFetch } from '../test/fetchMock';

const fields = { embossedName: 'OLD NAME', activeStatus: 'Y', expirationMonth: '12', expirationYear: '2030' };
const result = { token: 't', accountId: '10000000001', cardNumber: '4000000000000001', fields, message: 'Update card details presented above.', confirm: false };
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
});
