import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { getSession, setSession } from '../api/client';
import { mockFetch } from '../test/fetchMock';

const found = {
  accountId: '00000000042', customerId: '000000007', version: '42:7:100:200', state: 'EDIT',
  fields: { firstName: 'Example', creditLimit: '9000.00', middleName: '', activeStatus: 'Y' },
  message: 'Update account details presented above.', invalidField: null,
};
const home = { status: 200, body: { options: ['01. Account View', '02. Account Update'] } };
const account = () => screen.getByRole('textbox', { name: 'Account Number' });
const firstName = () => screen.getByRole('textbox', { name: 'First Name' });

function open(path = '/accounts/update', signedIn = true) {
  if (signedIn) setSession({ token: 't', userId: 'USER', userType: 'U', roles: ['USER'] });
  render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>);
}

async function fetchAccount() {
  await userEvent.type(account(), found.accountId);
  fireEvent.keyDown(account(), { key: 'Enter' });
  await screen.findByRole('textbox', { name: 'First Name' });
}

describe('CAUP account update', () => {
  it('0000 / 3100: requires a session and starts with only a focused account key', () => {
    mockFetch(() => ({ status: 200 }));
    open();
    expect(account()).toHaveFocus();
    expect(screen.queryByRole('textbox', { name: 'First Name' })).not.toBeInTheDocument();
    expect(screen.getByText('Enter or update id of account to update')).toBeInTheDocument();
  });

  it('0000: routes unsigned operators to sign-on', async () => {
    mockFetch(() => ({ status: 200 }));
    open('/accounts/update', false);
    expect(await screen.findByRole('button', { name: /sign on/i })).toBeInTheDocument();
  });

  it('menu dispatch: opens CAUP through option 2', async () => {
    mockFetch((_method, url) => url.endsWith('/select')
      ? { status: 200, body: { route: '/accounts/update' } } : home);
    open('/menu');
    await userEvent.click(await screen.findByRole('button', { name: '02. Account Update' }));
    expect(await screen.findByRole('textbox', { name: 'Account Number' })).toHaveFocus();
  });

  it('0000: sign-on then menu entry evaluates the current session rather than initial browser state', async () => {
    mockFetch((_method, url) => url === '/api/auth/login'
      ? { status: 200, body: { token: 't', userId: 'USER', userType: 'U', roles: ['USER'] } }
      : url.endsWith('/select') ? { status: 200, body: { route: '/accounts/update' } } : home);
    open('/login', false);
    await userEvent.type(screen.getByLabelText('User ID'), 'user');
    await userEvent.type(screen.getByLabelText('Password'), 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign on' }));
    await userEvent.click(await screen.findByRole('button', { name: '02. Account Update' }));
    expect(await screen.findByRole('textbox', { name: 'Account Number' })).toHaveFocus();
  });

  it('1100 / 1200 / 1210: displays the exact first error and leaves search editable', async () => {
    mockFetch(() => ({ status: 200, body: { ...found, version: null, customerId: null, fields: {}, state: 'SEARCH',
      invalidField: 'accountId', message: 'No input received' } }));
    open(); fireEvent.keyDown(account(), { key: 'Enter' });
    expect(await screen.findByRole('alert')).toHaveTextContent('No input received');
    expect(account()).not.toBeDisabled();
  });

  it('2000 / 3201-3203 / 3300 / 3310 / 3320: fetch, edit, review protects fields, F5 saves the reviewed version', async () => {
    const fetch = mockFetch((_method, url, body) => {
      if (url.endsWith('/read')) return { status: 200, body: found };
      const change = body as { fields: Record<string, string>; confirmed: boolean };
      return { status: 200, body: { ...found, fields: change.fields,
        state: change.confirmed ? 'SAVED' : 'CONFIRM',
        message: change.confirmed ? 'Changes committed to database' : 'Changes validated.Press F5 to save' } };
    });
    open(); await fetchAccount();
    expect(account()).toBeDisabled();
    expect(screen.getByText('Customer ID: 000000007')).toBeInTheDocument();
    expect(firstName()).toHaveValue('Example');
    expect(screen.getByRole('textbox', { name: 'FICO Score' })).toBeInTheDocument();
    fireEvent.keyDown(firstName(), { key: 'F5' });
    expect(fetch).toHaveBeenCalledTimes(1);
    await userEvent.clear(firstName()); await userEvent.type(firstName(), 'Updated');
    fireEvent.keyDown(firstName(), { key: 'Enter' });
    expect(await screen.findByText('Changes validated.Press F5 to save')).toBeInTheDocument();
    expect(firstName()).toBeDisabled();
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(fetch).toHaveBeenCalledTimes(2);
    fireEvent.keyDown(window, { key: 'F5' });
    expect(await screen.findByText('Changes committed to database')).toBeInTheDocument();
    expect(firstName()).not.toBeDisabled();
    expect(JSON.parse(String(fetch.mock.calls[2][1]?.body))).toMatchObject({
      accountId: found.accountId, version: found.version, confirmed: true, fields: { firstName: 'Updated' },
    });
  });

  it('1205 / 3250 / 3390: displays no-change and edit-error messages without losing the operator edits', async () => {
    let count = 0;
    mockFetch((_method, url) => url.endsWith('/read') ? { status: 200, body: found }
      : { status: 200, body: { ...found, invalidField: count++ === 0 ? null : 'firstName',
        message: count === 1 ? 'No change detected with respect to values fetched.' : 'First Name must be supplied.' } });
    open(); await fetchAccount();
    fireEvent.keyDown(firstName(), { key: 'Enter' });
    expect(await screen.findByText('No change detected with respect to values fetched.')).toBeInTheDocument();
    fireEvent.keyDown(firstName(), { key: 'Enter' });
    expect(await screen.findByRole('alert')).toHaveTextContent('First Name must be supplied.');
    expect(firstName()).toHaveAttribute('aria-invalid', 'true');
  });

  it.each(['keyboard', 'button'])('2000: F12 %s discards edits and reloads the database without saving', async mode => {
    const fetch = mockFetch(() => ({ status: 200, body: found }));
    open(); await fetchAccount();
    await userEvent.clear(firstName()); await userEvent.type(firstName(), 'Discard');
    if (mode === 'keyboard') fireEvent.keyDown(firstName(), { key: 'F12' });
    else await userEvent.click(screen.getByRole('button', { name: 'F12=Cancel / Reload' }));
    expect(await screen.findByDisplayValue('Example')).toBeInTheDocument();
    expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/accounts/update/read', '/api/accounts/update/read']);
  });

  it('9700: conflict preserves edits but prevents retry until F12 reloads a fresh version', async () => {
    let reads = 0;
    const fetch = mockFetch((_method, url) => url.endsWith('/read')
      ? { status: 200, body: { ...found, version: ++reads === 1 ? found.version : 'new-version' } }
      : { status: 409, body: { message: 'Record changed by some one else. Please review' } });
    open(); await fetchAccount();
    await userEvent.clear(firstName()); await userEvent.type(firstName(), 'Pending');
    fireEvent.keyDown(firstName(), { key: 'Enter' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Record changed by some one else. Please review');
    expect(firstName()).toHaveValue('Pending'); expect(firstName()).toBeDisabled();
    fireEvent.keyDown(window, { key: 'F5' }); fireEvent.keyDown(window, { key: 'Enter' });
    expect(fetch).toHaveBeenCalledTimes(2);
    fireEvent.keyDown(window, { key: 'F12' });
    expect(await screen.findByDisplayValue('Example')).toBeInTheDocument();
    expect(firstName()).not.toBeDisabled();
  });

  it('0000 / COMMON-RETURN: F3 returns to menu without signing out', async () => {
    mockFetch(() => home); open(); fireEvent.keyDown(account(), { key: 'F3' });
    expect(await screen.findByRole('button', { name: '02. Account Update' })).toBeInTheDocument();
    expect(getSession()?.userType).toBe('U');
  });

  it('ABEND-ROUTINE: shows safe network failure and handles expired sessions', async () => {
    let calls = 0;
    mockFetch(() => ++calls === 1 ? { status: 503, body: { message: 'Changes unsuccessful. Please try again' } }
      : { status: 401 });
    open(); fireEvent.keyDown(account(), { key: 'Enter' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Changes unsuccessful. Please try again');
    fireEvent.keyDown(account(), { key: 'Enter' });
    expect(await screen.findByRole('button', { name: /sign on/i })).toBeInTheDocument();
    expect(getSession()).toBeNull();
  });
});
