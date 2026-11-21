import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { setSession } from '../api/client';
import { loginAsAdmin, mockFetch } from '../test/fetchMock';

const base = '/api/admin/users';
const user = { userId: 'TEST01', firstName: 'First', lastName: 'Last', userType: 'U' };
const other = { ...user, userId: 'TEST02' };
const last = <T,>(items: T[]) => items[items.length - 1];
const page = (rows = [user], hasNext = false) => ({
  rows, hasNext, firstKey: rows[0]?.userId, lastKey: last(rows)?.userId, message: null,
});
const key = (value: string) => fireEvent.keyDown(window, { key: value });
const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
const open = (path: string) => render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>);
const menu = { status: 200, body: { options: ['01. User List (Security)'] } };

describe('CU00–CU03 COBOL screen paragraphs', () => {
  it('MAIN-PARA rejects non-admin direct entry before fetching users', () => {
    const fetch = mockFetch(() => ({ status: 500 }));
    setSession({ token: 't', userId: 'U', userType: 'U', roles: ['USER'] });
    open('/admin/users');
    expect(screen.getByText('CardDemo sign-on')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('CU00 MAIN-PARA SEND-USRLST-SCREEN POPULATE-HEADER-INFO and RETURN-TO-PREV-SCREEN from CA00', async () => {
    mockFetch((method, url) => {
      if (method === 'POST') return { status: 200, body: { route: '/admin/users' } };
      if (url === '/api/menus/admin') return menu;
      return { status: 200, body: page() };
    });
    loginAsAdmin();
    open('/admin');
    fireEvent.click(await screen.findByText('01. User List (Security)'));
    expect(await screen.findByRole('heading', { name: 'List Users' })).toBeInTheDocument();
    expect(screen.getByText('Prog: COUSR00C')).toBeInTheDocument();
    expect(await screen.findByText('TEST01')).toBeInTheDocument();
    key('F3');
    expect(await screen.findByRole('heading', { name: 'Admin Menu' })).toBeInTheDocument();
  });

  it('CU00 PROCESS-PF7/PF8, PROCESS-PAGE-FORWARD/BACKWARD and INITIALIZE/POPULATE-USER-DATA', async () => {
    const fetch = mockFetch((_method, url) => ({
      status: 200, body: url.includes('after=') ? page([other]) : page([user], true),
    }));
    loginAsAdmin();
    open('/admin/users');
    await screen.findByText('TEST01');
    key('F7');
    expect(screen.getByText('You are already at the top of the page...')).toBeInTheDocument();
    type('Search User ID', 'TEST01');
    key('Enter');
    await waitFor(() => expect(screen.getByLabelText('Search User ID')).toHaveValue(''));
    expect(last(fetch.mock.calls)?.[0]).toBe(`${base}?startId=TEST01`);
    type('Selection for TEST01', 'd');
    key('F8');
    expect(await screen.findByText('TEST02')).toBeInTheDocument();
    expect(screen.getByText('Page 2')).toBeInTheDocument();
    expect(screen.queryByText('TEST01')).not.toBeInTheDocument();
    expect(last(fetch.mock.calls)?.[0]).toBe(`${base}?startId=TEST01&after=TEST01`);
    key('F8');
    expect(screen.getByText('You are already at the bottom of the page...')).toBeInTheDocument();
    key('F7');
    expect(await screen.findByText('TEST01')).toBeInTheDocument();
    expect(screen.getByLabelText('Selection for TEST01')).toHaveValue('');
    expect(screen.getByText('Page 1')).toBeInTheDocument();
    expect(last(fetch.mock.calls)?.[0]).toBe(`${base}?startId=TEST01&before=TEST02`);
  });

  it.each([['u', 'Update User', 'COUSR02C'], ['d', 'Delete User', 'COUSR03C']])(
    'CU00 PROCESS-ENTER-KEY/RECEIVE selects first row action %s and passes its user to MAIN-PARA',
    async (action, heading, program) => {
      const fetch = mockFetch((method, url) => method === 'PUT'
        ? { status: 400, body: { code: 'NO_CHANGE', message: 'Please modify to update ...' } }
        : { status: 200, body: url.includes('/record') ? user : page([user, other]) });
      loginAsAdmin();
      open('/admin/users');
      await screen.findByText('TEST01');
      type('Selection for TEST01', 'x');
      key('Enter');
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid selection. Valid values are U and D');
      type('Selection for TEST01', action);
      type('Selection for TEST02', action);
      key('Enter');
      expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
      expect(screen.getByText(`Prog: ${program}`)).toBeInTheDocument();
      await waitFor(() => expect(screen.getByLabelText('First Name')).toHaveValue('First'));
      expect(last(fetch.mock.calls)?.[0]).toBe(`${base}/record?userId=TEST01`);
      key('F3');
      expect(await screen.findByRole('heading', { name: 'List Users' })).toBeInTheDocument();
    },
  );

  it('CU01 SEND/RECEIVE-USRADD-SCREEN, PROCESS-ENTER-KEY, CLEAR-CURRENT-SCREEN and INITIALIZE-ALL-FIELDS', async () => {
    const fetch = mockFetch((method, _url, body) => {
      if (method === 'GET') return menu;
      return (body as { firstName: string }).firstName
        ? { status: 201, body: { message: 'User TEST01 has been added ...' } }
        : { status: 400, body: { message: 'First Name can NOT be empty...' } };
    });
    loginAsAdmin();
    open('/admin/users/add');
    expect(screen.getByText('Tran: CU01')).toBeInTheDocument();
    expect(screen.getByLabelText('First Name')).toHaveFocus();
    key('Enter');
    expect(await screen.findByRole('alert')).toHaveTextContent('First Name can NOT be empty...');
    type('First Name', 'Discard');
    key('F4');
    expect(screen.getByLabelText('First Name')).toHaveValue('');
    key('F12');
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid key pressed. Please see below...');
    type('First Name', 'First');
    type('Last Name', 'Last');
    type('User ID', 'TEST01');
    type('User Type', 'U');
    type('Password', 'initial');
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    key('Enter');
    expect(await screen.findByText('User TEST01 has been added ...')).toBeInTheDocument();
    expect(JSON.parse(String(last(fetch.mock.calls)?.[1]?.body))).toEqual({ ...user, password: 'initial' });
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(screen.getByLabelText('User ID')).toHaveValue('');
    key('F3');
    expect(await screen.findByRole('heading', { name: 'Admin Menu' })).toBeInTheDocument();
  });

  it('CU02 PROCESS-ENTER-KEY, UPDATE-USER-INFO, CLEAR-CURRENT-SCREEN and PF3 Save & Exit', async () => {
    const fetch = mockFetch((method, url, body) => {
      if (url === '/api/menus/admin') return menu;
      if (method === 'GET') return { status: 200, body: user };
      return (body as { firstName: string }).firstName === 'First'
        ? { status: 400, body: { code: 'NO_CHANGE', message: 'Please modify to update ...' } }
        : { status: 200, body: { message: 'User TEST01 has been updated ...' } };
    });
    loginAsAdmin();
    open('/admin/users/update');
    type('Enter User ID', 'TEST01');
    key('Enter');
    expect(await screen.findByText('Press PF5 key to save your updates ...')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveValue('');
    key('F5');
    expect(await screen.findByRole('alert')).toHaveTextContent('Please modify to update ...');
    type('First Name', 'Changed');
    key('F5');
    expect(await screen.findByText('User TEST01 has been updated ...')).toBeInTheDocument();
    expect(JSON.parse(String(last(fetch.mock.calls)?.[1]?.body))).toEqual({
      firstName: 'Changed', lastName: 'Last', userType: 'U', password: '',
    });
    key('F4');
    expect(screen.getByLabelText('Enter User ID')).toHaveValue('');
    expect(screen.getByLabelText('First Name')).toHaveValue('');
    type('Enter User ID', 'TEST01');
    key('Enter');
    await screen.findByText('Press PF5 key to save your updates ...');
    type('First Name', 'Saved');
    key('F3');
    expect(await screen.findByRole('heading', { name: 'Admin Menu' })).toBeInTheDocument();
    expect(fetch.mock.calls.some(([url, init]) =>
      url.includes('/record') && init?.method === 'PUT' && String(init.body).includes('Saved'))).toBe(true);
  });

  it('CU03 PROCESS-ENTER-KEY, DELETE-USER-INFO, CLEAR-CURRENT-SCREEN and INITIALIZE-ALL-FIELDS', async () => {
    const fetch = mockFetch((method, url) => {
      if (url === '/api/menus/admin') return menu;
      return method === 'GET' ? { status: 200, body: user }
        : { status: 200, body: { message: 'User TEST01 has been deleted ...' } };
    });
    loginAsAdmin();
    open('/admin/users/delete');
    type('Enter User ID', 'TEST01');
    key('Enter');
    await screen.findByText('Press PF5 key to delete this user ...');
    expect(screen.getByLabelText('First Name')).toHaveAttribute('readonly');
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    key('F4');
    expect(screen.getByLabelText('First Name')).toHaveValue('');
    type('Enter User ID', 'TEST01');
    key('Enter');
    await screen.findByText('Press PF5 key to delete this user ...');
    key('F5');
    expect(await screen.findByText('User TEST01 has been deleted ...')).toBeInTheDocument();
    expect(screen.getByLabelText('Enter User ID')).toHaveValue('');
    expect(last(fetch.mock.calls)?.[1]?.method).toBe('DELETE');
    key('F12');
    expect(await screen.findByRole('heading', { name: 'Admin Menu' })).toBeInTheDocument();
  });
});
