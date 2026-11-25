import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { setSession } from '../api/client';
import { mockFetch } from '../test/fetchMock';

const account = {
  customerId: '000000007', name: 'Example Q Fixture', addressLine1: '123 Sample St,Unit 2',
  addressLine2: 'Sample City,TS,01234', phoneNumber1: '(000)000-0000', creditLimit: 9000, cashCreditLimit: 300,
};
const summary = {
  found: true, approvedCount: 6, declinedCount: 1, creditBalance: -123.45, cashBalance: 0,
  approvedAmount: 120, declinedAmount: 3.45,
};
const row = (i: number) => ({
  authorizationKey: `7399999989000${i}`, transactionId: `TXN00000000000${i}`, date: '01/02/26',
  time: `10:00:5${i}`, type: 'PURC', approval: i === 1 ? 'D' : 'A', matchStatus: 'P', approvedAmount: 10 + i,
});
const state = (page: number, next: boolean) => ({
  accountId: '00000000042', pageNumber: page, previousPageKeys: [], lastKey: null, nextPage: next, rowKeys: [],
});
const first = {
  account, summary, rows: [0, 1, 2, 3, 4].map(row), message: null, accountIdInvalid: false, transfer: null,
  state: state(1, true),
};
const field = () => screen.getByRole('textbox', { name: 'Search Acct Id' });
const key = (k: string) => fireEvent.keyDown(field(), { key: k });
const ids = () => within(screen.getByRole('table', { name: 'Pending Authorizations' })).queryAllByRole('row')
  .slice(1).map(r => within(r).getAllByRole('cell')[1].textContent);

function open(path: string | { pathname: string; state: unknown } = '/authorizations') {
  setSession({ token: 't', userId: 'USER', userType: 'U', roles: ['USER'] });
  render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>);
}

describe('COPAUS0C / COPAU0A', () => {
  it('MAIN-PARA: opens from main menu option 11 blank and focused', async () => {
    const fetch = mockFetch((method) => method === 'GET'
      ? { status: 200, body: { options: ['11. Pending Authorization View'], tranId: 'CM00', program: 'COMEN01C' } }
      : { status: 200, body: { route: '/authorizations', tranId: 'CPVS', program: 'COPAUS0C' } });
    open('/menu');
    await userEvent.click(await screen.findByRole('button', { name: /11. Pending Authorization View/ }));
    expect(await screen.findByText('Tran: CPVS')).toBeInTheDocument();
    expect(screen.getByText('Prog: COPAUS0C')).toBeInTheDocument();
    expect(field()).toHaveFocus();
    expect(field()).toHaveAttribute('maxlength', '11');
    expect(ids()).toEqual([]);
    expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/menus/main', '/api/menus/main/select']);
  });

  it('PROCESS-ENTER-KEY / GATHER-DETAILS / POPULATE-AUTH-LIST: shows the summary and five rows', async () => {
    const fetch = mockFetch(() => ({ status: 200, body: first }));
    open();
    await userEvent.type(field(), '00000000042');
    key('Enter');
    expect(await screen.findByText('TXN000000000000')).toBeInTheDocument();
    expect(ids()).toEqual(['TXN000000000000', 'TXN000000000001', 'TXN000000000002', 'TXN000000000003',
      'TXN000000000004']);
    const dl = screen.getByLabelText('Account Summary');
    for (const text of ['Example Q Fixture', '000000007', '(000)000-0000', '006', '001', '9000.00', '300.00',
      '120.00', '-123.45', '0.00', '3.45', 'Sample City,TS,01234']) {
      expect(within(dl).getAllByText(text).length).toBeGreaterThan(0);
    }
    expect(screen.getAllByText('D')).toHaveLength(1);
    expect(fetch.mock.calls[0][1]?.body).toBe(JSON.stringify({
      action: 'ENTER', accountId: '00000000042', selections: ['', '', '', '', ''], state: null,
    }));
  });

  it('PROCESS-ENTER-KEY: invalid account id clears the screen and flags the field', async () => {
    mockFetch(() => ({ status: 200, body: {
      account: null, summary: null, rows: [], message: 'Please enter Acct Id...', accountIdInvalid: true,
      transfer: null, state: state(0, false),
    } }));
    open();
    key('Enter');
    expect(await screen.findByRole('alert')).toHaveTextContent('Please enter Acct Id...');
    expect(field()).toHaveAttribute('aria-invalid', 'true');
  });

  it('PROCESS-PF8-KEY / PROCESS-PF7-KEY: pages with the returned state and keeps rows on top/bottom', async () => {
    const second = { ...first, account: null, summary: null, rows: [5, 6].map(row), state: state(2, false) };
    const bottom = { ...second, rows: null, message: 'You are already at the bottom of the page...' };
    const back = { ...first, account: null, summary: null, state: state(2, true) };
    const replies = [first, second, bottom, back];
    const fetch = mockFetch(() => ({ status: 200, body: replies.shift() }));
    open();
    await userEvent.type(field(), '00000000042');
    key('Enter');
    await screen.findByText('TXN000000000000');
    key('F8');
    expect(await screen.findByText('TXN000000000005')).toBeInTheDocument();
    expect(ids()).toEqual(['TXN000000000005', 'TXN000000000006']);
    expect(screen.getByText('Example Q Fixture')).toBeInTheDocument();
    key('F8');
    expect(await screen.findByRole('alert')).toHaveTextContent('You are already at the bottom of the page...');
    expect(ids()).toEqual(['TXN000000000005', 'TXN000000000006']);
    key('F7');
    expect(await screen.findByText('TXN000000000000')).toBeInTheDocument();
    const bodies = fetch.mock.calls.map(([, init]) => JSON.parse(String(init?.body)));
    expect(bodies.map(b => b.action)).toEqual(['ENTER', 'PF8', 'PF8', 'PF7']);
    expect(bodies[1].state).toEqual(state(1, true));
    expect(bodies[3].state).toEqual(state(2, false));
  });

  it('PROCESS-PF8-KEY: shows the 20-page bound message', async () => {
    const replies = [first, { ...first, account: null, summary: null, rows: null,
      message: 'Only 20 pages can be browsed. Press Enter to start again...', state: state(20, true) }];
    mockFetch(() => ({ status: 200, body: replies.shift() }));
    open();
    await userEvent.type(field(), '00000000042');
    key('Enter');
    await screen.findByText('TXN000000000000');
    key('F8');
    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Only 20 pages can be browsed. Press Enter to start again...');
    expect(ids()).toHaveLength(5);
  });

  it('PROCESS-ENTER-KEY: S selection sends the row and reports the CPVD transfer', async () => {
    const replies = [first, { ...first, rows: null, transfer: {
      program: 'COPAUS1C', tranId: 'CPVD', accountId: '00000000042', authorizationKey: '73999999890002',
    } }];
    const fetch = mockFetch(() => ({ status: 200, body: replies.shift() }));
    open();
    await userEvent.type(field(), '00000000042');
    key('Enter');
    await screen.findByText('TXN000000000000');
    await userEvent.type(screen.getByRole('textbox', { name: 'Select TXN000000000002' }), 's');
    key('Enter');
    expect(await screen.findByRole('alert')).toHaveTextContent('CPVD (COPAUS1C) is not installed yet...');
    expect(JSON.parse(String(fetch.mock.calls[1][1]?.body)).selections).toEqual(['', '', 's', '', '']);
  });

  it('MAIN-PARA: re-entry with an account id gathers details at once; F3 returns; other keys are invalid', async () => {
    mockFetch((_method, url) => url === '/api/authorizations/summary' ? { status: 200, body: first }
      : { status: 200, body: { options: [], tranId: 'CM00', program: 'COMEN01C' } });
    open({ pathname: '/authorizations', state: { accountId: '00000000042' } });
    expect(await screen.findByText('TXN000000000000')).toBeInTheDocument();
    expect(field()).toHaveValue('00000000042');
    key('F5');
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid key pressed. Please see below...');
    key('F3');
    expect(await screen.findByText('Tran: CM00')).toBeInTheDocument();
  });
});
