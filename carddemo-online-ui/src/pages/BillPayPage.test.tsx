import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { getSession, setSession } from '../api/client';
import { mockFetch } from '../test/fetchMock';
import { legacyBalance } from './BillPayPage';

const account = () => screen.getByRole('textbox', { name: 'Enter Acct ID' });
const confirm = () => screen.getByRole('textbox', { name: 'Confirm (Y/N)' });
const balance = () => screen.getByTestId('curbal');
const enter = () => fireEvent.keyDown(window, { key: 'Enter' });
const screenBody = (overrides: object) => ({
  accountId: '00000000042', currentBalance: 250, confirm: '', message: null,
  severity: null, focus: 'accountId', transactionId: null, ...overrides,
});

function open(signedIn = true) {
  if (signedIn) setSession({ token: 't', userId: 'USER0001', userType: 'U', roles: ['USER'] });
  render(<MemoryRouter initialEntries={['/bill-pay']}><App /></MemoryRouter>);
}

describe('CB00 COBIL00C / COBIL0A', () => {
  it('MAIN-PARA: EIBCALEN = 0 goes to sign-on; CM00 option 10 sends the blank map', async () => {
    const fetch = mockFetch((method, url) => {
      if (method === 'GET' && url === '/api/menus/main') return { status: 200, body: {
        options: Array.from({ length: 10 }, (_, i) => i < 9 ? `Option ${i + 1}` : '10. Bill Payment'),
      } };
      if (url === '/api/menus/main/select') return { status: 200, body: { route: '/bill-pay', tranId: 'CB00', program: 'COBIL00C' } };
      throw new Error(`Unexpected request: ${url}`);
    });
    open(false);
    expect(screen.getByText('CardDemo sign-on')).toBeInTheDocument();
    expect(getSession()).toBeNull();
    setSession({ token: 't', userId: 'USER0001', userType: 'U', roles: ['USER'] });
    render(<MemoryRouter initialEntries={['/menu']}><App /></MemoryRouter>);
    await userEvent.click(await screen.findByRole('button', { name: '10. Bill Payment' }));
    expect(await screen.findByRole('heading', { name: 'Bill Payment' })).toBeInTheDocument();
    expect(screen.getByText('Tran: CB00')).toBeInTheDocument();
    expect(screen.getByText('Prog: COBIL00C')).toBeInTheDocument();
    expect(account()).toHaveFocus();
    expect(account()).toHaveValue('');
    expect(account()).toHaveAttribute('maxlength', '11');
    expect(confirm()).toHaveAttribute('maxlength', '1');
    expect(balance()).toHaveTextContent(/^$/);
    expect(screen.getByText('ENTER=Continue')).toBeInTheDocument();
    expect(fetch.mock.calls[1][1]?.body).toBe('{"option":"10"}');
  });

  it('PROCESS-ENTER-KEY / READ-ACCTDAT-FILE: shows the balance in WS-CURR-BAL format and asks to confirm', async () => {
    const fetch = mockFetch(() => ({ status: 200, body: screenBody({
      message: 'Confirm to make a bill payment...', severity: 'error', focus: 'confirm' }) }));
    open();
    await userEvent.type(account(), '00000000042');
    enter();
    expect(await screen.findByRole('alert')).toHaveTextContent('Confirm to make a bill payment...');
    expect(balance()).toHaveTextContent('+0000000250.00');
    expect(confirm()).toHaveFocus();
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ accountId: '00000000042', confirm: '', currentBalance: null });
  });

  it('PROCESS-ENTER-KEY: errors keep the entered fields and echo the shown balance (FSET)', async () => {
    const fetch = mockFetch((_m, _u, body) => {
      const { confirm: value } = body as { confirm: string };
      return { status: 200, body: value === 'X'
        ? screenBody({ confirm: 'X', message: 'Invalid value. Valid values are (Y/N)...', severity: 'error', focus: 'confirm' })
        : screenBody({ message: 'Confirm to make a bill payment...', severity: 'error', focus: 'confirm' }) };
    });
    open();
    await userEvent.type(account(), '00000000042');
    enter();
    await screen.findByText('+0000000250.00');
    await userEvent.type(confirm(), 'X');
    enter();
    expect(await screen.findByText('Invalid value. Valid values are (Y/N)...')).toBeInTheDocument();
    expect(account()).toHaveValue('00000000042');
    expect(confirm()).toHaveValue('X');
    expect(confirm()).toHaveFocus();
    expect(JSON.parse(String(fetch.mock.calls[1][1]?.body))).toEqual({ accountId: '00000000042', confirm: 'X', currentBalance: 250 });
  });

  it('WRITE-TRANSACT-FILE: success clears the map and shows the transaction id in green', async () => {
    mockFetch(() => ({ status: 200, body: screenBody({ accountId: '', currentBalance: null, confirm: '',
      message: 'Payment successful.  Your Transaction ID is 0000000000000042.', severity: 'success', transactionId: '0000000000000042' }) }));
    open();
    await userEvent.type(account(), '00000000042');
    await userEvent.type(confirm(), 'Y');
    await userEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));
    expect(await screen.findByTestId('infomsg')).toHaveTextContent('Payment successful. Your Transaction ID is 0000000000000042.');
    expect(screen.getByTestId('errmsg')).toHaveTextContent(/^$/);
    expect(account()).toHaveValue('');
    expect(confirm()).toHaveValue('');
    expect(balance()).toHaveTextContent(/^$/);
    expect(account()).toHaveFocus();
  });

  it('WRITE-TRANSACT-FILE / UPDATE-ACCTDAT-FILE failures and a lost service keep the input', async () => {
    let call = 0;
    mockFetch(() => (++call === 1
      ? { status: 200, body: screenBody({ confirm: 'Y', message: 'Tran ID already exist...', severity: 'error' }) }
      : { status: 503, body: { code: 'BILL_PAY_UNAVAILABLE', message: 'Unable to lookup Account...' } }));
    open();
    await userEvent.type(account(), '00000000042');
    await userEvent.type(confirm(), 'Y');
    enter();
    expect(await screen.findByRole('alert')).toHaveTextContent('Tran ID already exist...');
    expect(account()).toHaveFocus();
    enter();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Unable to lookup Account...'));
    expect(confirm()).toHaveValue('Y');
  });

  it('CLEAR-CURRENT-SCREEN: PF4 clears locally; other keys are invalid; PF3 returns to CM00', async () => {
    const fetch = mockFetch((method, url) => {
      if (method === 'GET' && url === '/api/menus/main') return { status: 200, body: { options: [] } };
      return { status: 200, body: screenBody({ message: 'You have nothing to pay...', severity: 'error', currentBalance: 0 }) };
    });
    open();
    await userEvent.type(account(), '00000000043');
    enter();
    expect(await screen.findByRole('alert')).toHaveTextContent('You have nothing to pay...');
    expect(balance()).toHaveTextContent('+0000000000.00');
    fireEvent.keyDown(window, { key: 'F7' });
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid key pressed. Please see below...');
    fireEvent.keyDown(window, { key: 'F4' });
    expect(account()).toHaveValue('');
    expect(balance()).toHaveTextContent(/^$/);
    expect(screen.queryByRole('alert')).toBeNull();
    expect(account()).toHaveFocus();
    expect(fetch).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(window, { key: 'F3' });
    expect(await screen.findByRole('heading', { name: 'Main Menu' })).toBeInTheDocument();
  });

  it('a 401 returns to sign-on', async () => {
    mockFetch(() => ({ status: 401, body: {} }));
    open();
    enter();
    expect(await screen.findByText('CardDemo sign-on')).toBeInTheDocument();
  });

  it('WS-CURR-BAL edit picture', () => {
    expect(legacyBalance(1234.5)).toBe('+0000001234.50');
    expect(legacyBalance(-0.07)).toBe('-0000000000.07');
    expect(legacyBalance(null)).toBe('');
  });
});
