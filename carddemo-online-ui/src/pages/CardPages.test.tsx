import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CardListPage, CardViewPage } from './CardPages';
import { App } from '../App';
import { setSession } from '../api/client';
import { mockFetch } from '../test/fetchMock';

const rows = Array.from({ length: 7 }, (_, i) => ({ accountId: '10000000001', cardNumber: `400000000000000${i + 1}`, activeStatus: 'Y' }));
const page = { rows, firstKey: rows[0].cardNumber, nextCursor: '4000000000000008', pageNumber: 1,
  lastPageShown: false, message: null, info: 'TYPE S FOR DETAIL, U TO UPDATE ANY RECORD' };
const detail = { ...rows[0], embossedName: 'SYNTHETIC CARDHOLDER', expirationMonth: '12', expirationYear: '2030' };
function login(type: 'A' | 'U' = 'U') {
  setSession({ token: 't', userId: 'TEST', userType: type, roles: [type === 'A' ? 'ADMIN' : 'USER'] });
}
function renderPage(path = '/cards') {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/cards" element={<CardListPage />} /><Route path="/cards/view" element={<CardViewPage />} />
    <Route path="/menu" element={<div>MAIN MENU</div>} /><Route path="/login" element={<div>SIGN ON</div>} />
  </Routes></MemoryRouter>);
}

describe('CCLI paragraphs', () => {
  it.each(['A', 'U'] as const)('0000-MAIN / 1000-SEND-MAP / 1100-SCREEN-INIT / 1200-SCREEN-ARRAY-INIT / 1250-SETUP-ARRAY-ATTRIBS / 1300-SETUP-SCREEN-ATTRS / 1500-SEND-SCREEN: type %s', async type => {
    login(type);
    mockFetch(() => ({ status: 200, body: page }));
    renderPage();
    expect(await screen.findByText(rows[6].cardNumber)).toBeInTheDocument();
    expect(screen.getAllByRole('textbox', { name: /^action / })).toHaveLength(7);
    expect(screen.getByText('Tran: CCLI')).toBeInTheDocument();
    expect(screen.getByText('Prog: COCRDLIC')).toBeInTheDocument();
    expect(screen.getByTestId('pageno')).toHaveTextContent('Page 1');
    expect(screen.getByTestId('infomsg')).toHaveTextContent(page.info);
    expect(screen.getByLabelText('Account ID')).toHaveFocus();
    expect(screen.getByLabelText('Account ID')).not.toHaveAttribute('readonly');
  });

  it('9000-READ-FORWARD / 9100-READ-BACKWARDS / 1400-SETUP-MESSAGE: PF7/PF8 and boundary messages', async () => {
    login();
    const fetch = mockFetch((_method, url) => {
      const params = new URL(url, 'http://test').searchParams;
      if (params.get('key') === 'PF8') return { status: 200, body: { ...page,
        rows: [{ ...rows[0], cardNumber: '4000000000000008' }], firstKey: '4000000000000008', nextCursor: null,
        pageNumber: 2, lastPageShown: true, message: params.get('lastPageShown') === 'true' ? 'NO MORE PAGES TO DISPLAY' : 'NO MORE RECORDS TO SHOW' } };
      return { status: 200, body: { ...page, message: params.get('key') === 'PF7' && params.get('pageNumber') === '1'
        ? 'NO PREVIOUS PAGES TO DISPLAY' : null } };
    });
    renderPage();
    await screen.findByText(rows[0].cardNumber);
    fireEvent.keyDown(window, { key: 'F7' });
    expect(await screen.findByRole('alert')).toHaveTextContent('NO PREVIOUS PAGES TO DISPLAY');
    fireEvent.keyDown(window, { key: 'F8' });
    expect(await screen.findByText('4000000000000008')).toBeInTheDocument();
    expect(screen.getByTestId('pageno')).toHaveTextContent('Page 2');
    expect(screen.getByRole('alert')).toHaveTextContent('NO MORE RECORDS TO SHOW');
    expect(fetch.mock.calls[2][0]).toContain('cursor=4000000000000008');
    await userEvent.click(screen.getByText('F8=Page Dn'));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('NO MORE PAGES TO DISPLAY'));
    await userEvent.click(screen.getByText('F7=Page Up'));
    await screen.findByText(rows[0].cardNumber);
    expect(fetch.mock.calls[4][0]).toContain('cursor=4000000000000008');
    expect(screen.getByTestId('pageno')).toHaveTextContent('Page 1');
  });

  it('2000-RECEIVE-MAP / 2100-RECEIVE-SCREEN / 2200-EDIT-INPUTS / 2210-EDIT-ACCOUNT / 2220-EDIT-CARD: filter errors, empty results, Enter remapping', async () => {
    login();
    const fetch = mockFetch((_method, url) => {
      if (url.includes('accountId=x')) return { status: 400, body: { message: 'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER' } };
      if (url.includes('cardNumber=x')) return { status: 400, body: { message: 'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER' } };
      return { status: 200, body: { ...page, rows: [], message: 'NO RECORDS FOUND FOR THIS SEARCH CONDITION.', info: null } };
    });
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('NO RECORDS FOUND FOR THIS SEARCH CONDITION.');
    expect(screen.queryByRole('textbox', { name: /^action / })).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Account ID'), 'x');
    fireEvent.keyDown(window, { key: 'Enter' });
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER'));
    await userEvent.clear(screen.getByLabelText('Account ID'));
    await userEvent.type(screen.getByLabelText('Card Number'), 'x');
    fireEvent.keyDown(window, { key: 'F2' });
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER'));
    expect(fetch.mock.calls[2][0]).toContain('cardNumber=x&key=ENTER');
    fireEvent.keyDown(window, { key: 'F3' });
    expect(screen.getByText('MAIN MENU')).toBeInTheDocument();
  });

  it('2250-EDIT-ARRAY: invalid action, only-one selection, and U not installed', async () => {
    login();
    const fetch = mockFetch((method, _url, body) => {
      if (method === 'GET') return { status: 200, body: page };
      const actions = (body as { selections: { action: string }[] }).selections.map(row => row.action).filter(Boolean);
      return { status: actions.includes('X') || actions.length > 1 ? 400 : 200, body: { message: actions.includes('X')
        ? 'INVALID ACTION CODE' : actions.length > 1 ? 'PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE' : 'This option is not installed ...' } };
    });
    renderPage();
    await screen.findByText(rows[0].cardNumber);
    await userEvent.type(screen.getByLabelText(`action ${rows[0].cardNumber}`), 'X');
    await userEvent.click(screen.getByText('Enter=Select'));
    expect(await screen.findByRole('alert')).toHaveTextContent('INVALID ACTION CODE');
    await userEvent.clear(screen.getByLabelText(`action ${rows[0].cardNumber}`));
    await userEvent.type(screen.getByLabelText(`action ${rows[0].cardNumber}`), 'S');
    await userEvent.type(screen.getByLabelText(`action ${rows[1].cardNumber}`), 'U');
    await userEvent.click(screen.getByText('Enter=Select'));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE'));
    await userEvent.clear(screen.getByLabelText(`action ${rows[0].cardNumber}`));
    await userEvent.click(screen.getByText('Enter=Select'));
    expect(await screen.findByText('This option is not installed ...')).toBeInTheDocument();
    expect(JSON.parse(String(fetch.mock.calls[6][1]?.body)).selections[1].action).toBe('U');
  });

  it('0000-MAIN / COMMON-RETURN: S selection, protected detail keys, PF3 restores list filters/page', async () => {
    login();
    mockFetch((method, url) => {
      if (url.startsWith('/api/cards/view')) return { status: 200, body: detail };
      if (method === 'POST') return { status: 200, body: { route: '/cards/view', accountId: rows[0].accountId, cardNumber: rows[0].cardNumber } };
      return { status: 200, body: { ...page, pageNumber: 2 } };
    });
    renderPage();
    await screen.findByText(rows[0].cardNumber);
    await userEvent.type(screen.getByLabelText('Account ID'), '10000000001');
    await userEvent.type(screen.getByLabelText(`action ${rows[0].cardNumber}`), 'S');
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByDisplayValue('SYNTHETIC CARDHOLDER')).toBeInTheDocument();
    expect(screen.getByLabelText('Account ID')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('Card Number')).toHaveAttribute('readonly');
    fireEvent.keyDown(window, { key: 'F3' });
    await screen.findByText(rows[0].cardNumber);
    expect(screen.getByLabelText('Account ID')).toHaveValue('10000000001');
    expect(screen.getByTestId('pageno')).toHaveTextContent('Page 2');
    expect(screen.getByLabelText(`action ${rows[0].cardNumber}`)).toHaveValue('');
  });

  it('SEND-PLAIN-TEXT / SEND-LONG-TEXT: file failure surfaced as a message, not transport internals', async () => {
    login();
    mockFetch(() => ({ status: 500, body: { message: 'Error reading Card Data File' } }));
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('Error reading Card Data File');
  });
});

describe('CCDL paragraphs', () => {
  it('0000-MAIN / 1000-SEND-MAP / 1100-SCREEN-INIT / 1200-SETUP-SCREEN-VARS / 1300-SETUP-SCREEN-ATTRS / 1400-SEND-SCREEN: direct-entry prompt and editable inputs', () => {
    login();
    renderPage('/cards/view');
    expect(screen.getByText('Tran: CCDL')).toBeInTheDocument();
    expect(screen.getByText('Prog: COCRDSLC')).toBeInTheDocument();
    expect(screen.getByTestId('infomsg')).toHaveTextContent('Please enter Account and Card Number');
    expect(screen.getByLabelText('Account ID')).not.toHaveAttribute('readonly');
    expect(screen.getByLabelText('Card Number')).not.toHaveAttribute('readonly');
    expect(screen.getByLabelText('Name on Card')).toHaveValue('');
    expect(screen.getByLabelText('Account ID')).toHaveFocus();
    fireEvent.keyDown(window, { key: 'F3' });
    expect(screen.getByText('MAIN MENU')).toBeInTheDocument();
  });

  it('2000-PROCESS-INPUTS / 2100-RECEIVE-MAP / 2200-EDIT-MAP-INPUTS / 2210-EDIT-ACCOUNT / 2220-EDIT-CARD: validation and not found', async () => {
    login();
    const messages = ['No input received', 'Account number not provided', 'Card number not provided',
      'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER', 'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER',
      'Did not find cards for this search condition', 'Error reading Card Data File'];
    let i = 0;
    mockFetch(() => ({ status: 400, body: { message: messages[i++] } }));
    renderPage('/cards/view');
    for (const message of messages) {
      await userEvent.click(screen.getByText('Enter=Search'));
      await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(message));
      expect(screen.getByLabelText('Name on Card')).toHaveValue('');
    }
  });

  it('9000-READ-DATA / 9100-GETCARD-BYACCTCARD: shows card-only results without CVV, clears stale details on error', async () => {
    login();
    const fetch = mockFetch((_method, url) => url.includes('cardNumber=4000000000000001')
      ? { status: 200, body: detail } : { status: 404, body: { message: 'Did not find cards for this search condition' } });
    renderPage('/cards/view');
    await userEvent.type(screen.getByLabelText('Account ID'), '99999999999');
    await userEvent.type(screen.getByLabelText('Card Number'), rows[0].cardNumber);
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByDisplayValue('SYNTHETIC CARDHOLDER')).toBeInTheDocument();
    expect(screen.getByLabelText('Expiration Month')).toHaveValue('12');
    expect(screen.getByLabelText('Expiration Year')).toHaveValue('2030');
    expect(screen.getByLabelText('Active Status')).toHaveValue('Y');
    expect(screen.getByTestId('infomsg')).toHaveTextContent('Displaying requested details');
    expect(screen.queryByLabelText(/CVV/i)).not.toBeInTheDocument();
    expect(fetch.mock.calls[0][0]).toContain('accountId=99999999999');
    await userEvent.clear(screen.getByLabelText('Card Number'));
    fireEvent.keyDown(window, { key: 'F7' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Did not find cards for this search condition');
    expect(screen.getByLabelText('Name on Card')).toHaveValue('');
  });
});

describe('E2 menu integration', () => {
  it.each(['/cards', '/cards/view'])('0000-MAIN: guards %s without a session', path => {
    renderPage(path);
    expect(screen.getByText('SIGN ON')).toBeInTheDocument();
  });
  it.each([['3', '/cards', 'Credit Card List'], ['4', '/cards/view', 'Credit Card View']])('menu option %s reaches %s', async (option, route, heading) => {
    login();
    mockFetch((method, url) => {
      if (method === 'POST') return { status: 200, body: { route } };
      if (url === '/api/menus/main') return { status: 200, body: { options: ['01. Account View'], tranId: 'CM00', program: 'COMEN01C' } };
      return { status: 200, body: page };
    });
    render(<MemoryRouter initialEntries={['/menu']}><App /></MemoryRouter>);
    await screen.findByText('01. Account View');
    await userEvent.type(screen.getByLabelText('Option'), option);
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
  });
});
