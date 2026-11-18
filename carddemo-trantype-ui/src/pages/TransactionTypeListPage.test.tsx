import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TransactionTypeListPage } from './TransactionTypeListPage';
import { loginAsAdmin, mockFetch, rows } from '../test/fetchMock';

const page = (over: object) => ({
  rows: rows(1, 7), firstKey: '01', lastKey: '07', nextCursor: '08', hasNext: true, hasPrev: false,
  pageNumber: 1, message: null, ...over,
});

function renderPage() {
  loginAsAdmin();
  return render(
    <MemoryRouter initialEntries={['/transaction-types']}>
      <Routes>
        <Route path="/transaction-types" element={<TransactionTypeListPage />} />
        <Route path="/admin" element={<div>ADMIN MENU</div>} />
        <Route path="/transaction-types/maintain" element={<div>CTTU SCREEN</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('TransactionTypeListPage (CTLI)', () => {
  it('loads first page with 7 rows and the record-actions prompt', async () => {
    const fetch = mockFetch(() => ({ status: 200, body: page({}) }));
    renderPage();
    expect(await screen.findByTestId('row-07')).toBeInTheDocument();
    expect(screen.getByTestId('pageno')).toHaveTextContent('Page 1');
    expect(screen.getByTestId('infomsg')).toHaveTextContent('Type U to update, D to delete any record');
    expect(fetch.mock.calls[0][0]).toBe('/api/transaction-types?page=1&size=7');
  });

  it('pages forward with the lookahead cursor and back with the first key', async () => {
    const fetch = mockFetch((_m, url) => {
      if (url.includes('direction=FORWARD')) {
        return { status: 200, body: page({ rows: rows(8, 1), firstKey: '08', lastKey: '08', nextCursor: null, hasNext: false, hasPrev: true, pageNumber: 2 }) };
      }
      return { status: 200, body: page({}) };
    });
    renderPage();
    await screen.findByTestId('row-01');
    await userEvent.click(screen.getByText('F8=Page Dn'));
    expect(await screen.findByTestId('row-08')).toBeInTheDocument();
    expect(fetch.mock.calls[1][0]).toContain('cursor=08&direction=FORWARD&page=2');
    expect(screen.getByTestId('pageno')).toHaveTextContent('Page 2');

    await userEvent.click(screen.getByText('F8=Page Dn'));
    expect(screen.getByRole('alert')).toHaveTextContent('No more pages to display');

    await userEvent.click(screen.getByText('F7=Page Up'));
    await screen.findByTestId('row-01');
    expect(fetch.mock.calls[2][0]).toContain('cursor=08&direction=BACKWARD&page=1');
  });

  it('F7 on first page shows no previous pages', async () => {
    mockFetch(() => ({ status: 200, body: page({}) }));
    renderPage();
    await screen.findByTestId('row-01');
    await userEvent.click(screen.getByText('F7=Page Up'));
    expect(screen.getByRole('alert')).toHaveTextContent('No previous pages to display');
  });

  it('shows the empty-table message', async () => {
    mockFetch(() => ({ status: 200, body: page({ rows: [], firstKey: null, lastKey: null, nextCursor: null, hasNext: false, message: 'No records found for this search condition.' }) }));
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('No records found for this search condition.');
  });

  it('enforces single valid action code', async () => {
    mockFetch(() => ({ status: 200, body: page({}) }));
    renderPage();
    await screen.findByTestId('row-01');
    await userEvent.type(screen.getByLabelText('action 01'), 'X');
    await userEvent.click(screen.getByText('ENTER'));
    expect(screen.getByRole('alert')).toHaveTextContent('Action code selected is invalid');
    await userEvent.clear(screen.getByLabelText('action 01'));
    await userEvent.type(screen.getByLabelText('action 01'), 'U');
    await userEvent.type(screen.getByLabelText('action 02'), 'D');
    await userEvent.click(screen.getByText('ENTER'));
    expect(screen.getByRole('alert')).toHaveTextContent('Please select only 1 action');
  });

  it('updates a description inline after F10 confirmation', async () => {
    const fetch = mockFetch((m) => (m === 'PATCH'
      ? { status: 200, body: { trType: '02', trDescription: 'PAYMENTS' } }
      : { status: 200, body: page({}) }));
    renderPage();
    await screen.findByTestId('row-02');
    await userEvent.type(screen.getByLabelText('action 02'), 'u');
    const desc = screen.getByLabelText('description 02');
    expect(desc).toBeEnabled();
    expect(screen.getByLabelText('description 01')).toBeDisabled();
    await userEvent.click(screen.getByText('ENTER'));
    expect(screen.getByTestId('infomsg')).toHaveTextContent('Update HIGHLIGHTED row. Press F10 to save');

    await userEvent.click(screen.getByText('F10=Save'));
    expect(screen.getByRole('alert')).toHaveTextContent('No change detected with respect to database values.');

    await userEvent.clear(desc);
    await userEvent.click(screen.getByText('ENTER'));
    await userEvent.click(screen.getByText('F10=Save'));
    expect(screen.getByRole('alert')).toHaveTextContent('Transaction Desc must be supplied.');

    await userEvent.type(desc, 'PAYMENTS');
    await userEvent.click(screen.getByText('ENTER'));
    await userEvent.click(screen.getByText('F10=Save'));
    await waitFor(() => expect(screen.getByTestId('infomsg')).toHaveTextContent('HIGHLIGHTED row was updated'));
    const patch = fetch.mock.calls.find((c) => c[1]?.method === 'PATCH')!;
    expect(patch[0]).toBe('/api/transaction-types/02');
    expect(JSON.parse(String(patch[1]!.body))).toEqual({ trDescription: 'PAYMENTS' });
  });

  it('delete with children shows the FK message', async () => {
    mockFetch((m) => (m === 'DELETE'
      ? { status: 409, body: { code: 'FK_RESTRICT', message: 'Please delete associated child records first:SQLCODE :-532' } }
      : { status: 200, body: page({}) }));
    renderPage();
    await screen.findByTestId('row-01');
    await userEvent.type(screen.getByLabelText('action 01'), 'D');
    await userEvent.click(screen.getByText('ENTER'));
    expect(screen.getByTestId('infomsg')).toHaveTextContent('Delete HIGHLIGHTED row ? Press F10 to confirm');
    await userEvent.click(screen.getByText('F10=Save'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Please delete associated child records first:');
  });

  it('delete success reloads page 1', async () => {
    mockFetch((m) => (m === 'DELETE' ? { status: 204 } : { status: 200, body: page({}) }));
    renderPage();
    await screen.findByTestId('row-01');
    await userEvent.type(screen.getByLabelText('action 03'), 'D');
    await userEvent.click(screen.getByText('ENTER'));
    await userEvent.click(screen.getByText('F10=Save'));
    await waitFor(() => expect(screen.getByTestId('infomsg'))
      .toHaveTextContent('HIGHLIGHTED row deleted.Hit Enter to continue'));
  });

  it('validates the type filter and F2/F3 navigate', async () => {
    mockFetch(() => ({ status: 200, body: page({}) }));
    renderPage();
    await screen.findByTestId('row-01');
    await userEvent.type(screen.getByLabelText('Type Filter'), 'A');
    await userEvent.click(screen.getByText('ENTER'));
    expect(screen.getByRole('alert')).toHaveTextContent('TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER');
    await userEvent.click(screen.getByText('F3=Exit'));
    expect(screen.getByText('ADMIN MENU')).toBeInTheDocument();
  });

  it('F2 opens the add screen', async () => {
    mockFetch(() => ({ status: 200, body: page({}) }));
    renderPage();
    await screen.findByTestId('row-01');
    await userEvent.click(screen.getByText('F2=Add'));
    expect(screen.getByText('CTTU SCREEN')).toBeInTheDocument();
  });
});
