import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TransactionTypeListPage } from './TransactionTypeListPage';
import { TransactionTypeEditPage } from './TransactionTypeEditPage';
import { mockFetch, rows } from '../test/fetchMock';

function open(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/transaction-types" element={<TransactionTypeListPage />} />
        <Route path="/transaction-types/maintain" element={<TransactionTypeEditPage />} />
        <Route path="/admin" element={<div>ADMIN MENU</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('transaction-type keyboard actions', () => {
  it('CTLI supports Enter, save, page boundaries, add and exit', async () => {
    mockFetch(() => ({ status: 200, body: { rows: rows(1, 1), firstKey: '01', lastKey: '01',
      nextCursor: null, hasNext: false, hasPrev: false, pageNumber: 1, message: null } }));
    open('/transaction-types');
    await screen.findByTestId('row-01');
    await userEvent.keyboard('{F7}');
    expect(screen.getByTestId('errmsg')).toHaveTextContent('No previous pages to display');
    await userEvent.keyboard('{F8}');
    expect(screen.getByTestId('errmsg')).toHaveTextContent('No more pages to display');
    await userEvent.type(screen.getByLabelText('action 01'), 'X');
    await userEvent.keyboard('{Enter}');
    expect(screen.getByTestId('errmsg')).toHaveTextContent('Action code selected is invalid');
    await userEvent.clear(screen.getByLabelText('action 01'));
    await userEvent.type(screen.getByLabelText('action 01'), 'D');
    await userEvent.keyboard('{Enter}');
    expect(screen.getByTestId('infomsg')).toHaveTextContent('Delete HIGHLIGHTED row');
    await userEvent.keyboard('{F10}');
    await waitFor(() => expect(screen.getByTestId('infomsg')).toHaveTextContent('HIGHLIGHTED row deleted'));
    await userEvent.keyboard('{F2}');
    expect(screen.getByLabelText('Transaction Type')).toBeInTheDocument();
    await userEvent.keyboard('{F3}');
    expect(await screen.findByTestId('row-01')).toBeInTheDocument();
    await userEvent.keyboard('{F3}');
    expect(screen.getByText('ADMIN MENU')).toBeInTheDocument();
  });

  it('CTTU gates invalid keys and supports search, confirmation, cancel, save and delete', async () => {
    mockFetch((method) => method === 'DELETE' ? { status: 204 }
      : { status: 200, body: { trType: '01', trDescription: 'PURCHASE' } });
    open('/transaction-types/maintain');
    await userEvent.keyboard('{F4}{F5}{F12}');
    expect(screen.getByTestId('infomsg')).toHaveTextContent('Enter transaction type');
    await userEvent.type(screen.getByLabelText('Transaction Type'), '1');
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(screen.getByLabelText('Description')).toHaveValue('PURCHASE'));
    await userEvent.clear(screen.getByLabelText('Description'));
    await userEvent.type(screen.getByLabelText('Description'), 'CHANGED');
    await userEvent.keyboard('{Enter}{F12}');
    expect(screen.getByLabelText('Description')).toHaveValue('PURCHASE');
    await userEvent.clear(screen.getByLabelText('Description'));
    await userEvent.type(screen.getByLabelText('Description'), 'CHANGED');
    await userEvent.keyboard('{Enter}{F5}');
    await waitFor(() => expect(screen.getByTestId('infomsg')).toHaveTextContent('Changes committed'));
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(screen.getByLabelText('Description')).toBeEnabled());
    await userEvent.keyboard('{F4}{F12}');
    expect(screen.getByTestId('errmsg')).toHaveTextContent('Delete was cancelled');
    await userEvent.type(screen.getByLabelText('Transaction Type'), '1');
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(screen.getByLabelText('Description')).toBeEnabled());
    await userEvent.keyboard('{F4}{Enter}');
    expect(screen.getByText('F4=Delete')).toBeInTheDocument();
    await userEvent.keyboard('{F4}');
    await waitFor(() => expect(screen.getByTestId('infomsg')).toHaveTextContent('Delete successful.'));
  });
});
