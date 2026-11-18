import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TransactionTypeEditPage } from './TransactionTypeEditPage';
import { loginAsAdmin, mockFetch } from '../test/fetchMock';

function renderPage(path = '/transaction-types/maintain') {
  loginAsAdmin();
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/transaction-types/maintain" element={<TransactionTypeEditPage />} />
        <Route path="/admin" element={<div>ADMIN MENU</div>} />
        <Route path="/transaction-types" element={<div>CTLI LIST</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

const info = () => screen.getByTestId('infomsg');
const err = () => screen.getByTestId('errmsg');

describe('TransactionTypeEditPage (CTTU)', () => {
  it('prompts for key and rejects blank / invalid input', async () => {
    mockFetch(() => ({ status: 500 }));
    renderPage();
    expect(info()).toHaveTextContent('Enter transaction type to be maintained');
    await userEvent.click(screen.getByText('ENTER=Process'));
    expect(err()).toHaveTextContent('No input received');
    await userEvent.type(screen.getByLabelText('Transaction Type'), '00');
    await userEvent.click(screen.getByText('ENTER=Process'));
    expect(err()).toHaveTextContent('Tran Type code must not be zero.');
  });

  it('adds a new record: not found -> F5 -> validate -> F5 save', async () => {
    const fetch = mockFetch((m) => {
      if (m === 'GET') return { status: 404, body: { code: 'NOT_FOUND', message: 'No record found for this key in database' } };
      return { status: 201, body: { trType: '08', trDescription: 'FEE' } };
    });
    renderPage();
    await userEvent.type(screen.getByLabelText('Transaction Type'), '8');
    await userEvent.click(screen.getByText('ENTER=Process'));
    await waitFor(() => expect(err()).toHaveTextContent('No record found for this key in database'));
    expect(info()).toHaveTextContent('Press F05 to add. F12 to cancel');
    expect(fetch.mock.calls[0][0]).toBe('/api/transaction-types/08');

    await userEvent.click(screen.getByText('F5=Save'));
    expect(info()).toHaveTextContent('Enter new transaction type details.');
    await userEvent.type(screen.getByLabelText('Description'), 'F$E');
    await userEvent.click(screen.getByText('ENTER=Process'));
    expect(err()).toHaveTextContent('Transaction Desc can have numbers or alphabets only.');
    await userEvent.clear(screen.getByLabelText('Description'));
    await userEvent.type(screen.getByLabelText('Description'), 'FEE');
    await userEvent.click(screen.getByText('ENTER=Process'));
    expect(info()).toHaveTextContent('Changes validated.Press F5 to save');
    await userEvent.click(screen.getByText('F5=Save'));
    await waitFor(() => expect(info()).toHaveTextContent('Changes committed to database'));
    const put = fetch.mock.calls.find((c) => c[1]?.method === 'PUT')!;
    expect(put[0]).toBe('/api/transaction-types/08');
  });

  it('changing the key after a failed lookup requires a new lookup', async () => {
    mockFetch(() => ({ status: 404, body: { code: 'NOT_FOUND', message: 'No record found for this key in database' } }));
    renderPage();
    await userEvent.type(screen.getByLabelText('Transaction Type'), '8');
    await userEvent.click(screen.getByText('ENTER=Process'));
    await waitFor(() => expect(screen.getByText('F5=Save')).toBeInTheDocument());
    await userEvent.clear(screen.getByLabelText('Transaction Type'));
    await userEvent.type(screen.getByLabelText('Transaction Type'), '01');
    expect(info()).toHaveTextContent('Enter transaction type to be maintained');
    expect(screen.queryByText('F5=Save')).not.toBeInTheDocument();
  });

  it('edits an existing record, detects no change, and cancels with F12', async () => {
    mockFetch(() => ({ status: 200, body: { trType: '01', trDescription: 'PURCHASE' } }));
    renderPage();
    await userEvent.type(screen.getByLabelText('Transaction Type'), '1');
    await userEvent.click(screen.getByText('ENTER=Process'));
    await waitFor(() => expect(info()).toHaveTextContent('Update transaction type details shown.'));
    expect(screen.getByLabelText('Transaction Type')).toBeDisabled();
    await userEvent.click(screen.getByText('ENTER=Process'));
    expect(err()).toHaveTextContent('No change detected with respect to values fetched.');
    await userEvent.type(screen.getByLabelText('Description'), 'S');
    await userEvent.click(screen.getByText('ENTER=Process'));
    expect(info()).toHaveTextContent('Changes validated.Press F5 to save');
    await userEvent.click(screen.getByText('F12=Cancel'));
    expect(err()).toHaveTextContent('Update was cancelled');
    expect(screen.getByLabelText('Description')).toHaveValue('PURCHASE');
  });

  it('save failure shows server message', async () => {
    mockFetch((m) => (m === 'PUT'
      ? { status: 409, body: { code: 'LOCK_ERROR', message: 'Could not lock record for update' } }
      : { status: 200, body: { trType: '01', trDescription: 'PURCHASE' } }));
    renderPage();
    await userEvent.type(screen.getByLabelText('Transaction Type'), '01');
    await userEvent.click(screen.getByText('ENTER=Process'));
    await waitFor(() => expect(screen.getByLabelText('Description')).toBeEnabled());
    await userEvent.type(screen.getByLabelText('Description'), 'X');
    await userEvent.click(screen.getByText('ENTER=Process'));
    await userEvent.click(screen.getByText('F5=Save'));
    await waitFor(() => expect(err()).toHaveTextContent('Could not lock record for update'));
    expect(info()).toHaveTextContent('Changes unsuccessful');
  });

  it('deletes with F4 confirmation, cancel with F12, and FK failure', async () => {
    let deleteStatus = 409;
    mockFetch((m) => {
      if (m === 'DELETE') {
        return deleteStatus === 204 ? { status: 204 }
          : { status: 409, body: { code: 'FK_RESTRICT', message: 'Please delete associated child records first:SQLCODE :-532' } };
      }
      return { status: 200, body: { trType: '05', trDescription: 'REFUND' } };
    });
    renderPage();
    const fetchKey = async () => {
      await userEvent.type(screen.getByLabelText('Transaction Type'), '5');
      await userEvent.click(screen.getByText('ENTER=Process'));
      await waitFor(() => expect(screen.getByText('F4=Delete')).toBeInTheDocument());
    };
    await fetchKey();
    await userEvent.click(screen.getByText('F4=Delete'));
    expect(info()).toHaveTextContent('Delete this record ? Press F4 to confirm');
    expect(screen.queryByText('ENTER=Process')).not.toBeInTheDocument();
    await userEvent.click(screen.getByText('F12=Cancel'));
    expect(err()).toHaveTextContent('Delete was cancelled');

    await fetchKey();
    await userEvent.click(screen.getByText('F4=Delete'));
    await userEvent.click(screen.getByText('F4=Delete'));
    await waitFor(() => expect(err()).toHaveTextContent('Please delete associated child records first:'));

    deleteStatus = 204;
    await userEvent.clear(screen.getByLabelText('Transaction Type'));
    await fetchKey();
    await userEvent.click(screen.getByText('F4=Delete'));
    await userEvent.click(screen.getByText('F4=Delete'));
    await waitFor(() => expect(info()).toHaveTextContent('Delete successful.'));
  });

  it('F3 returns to the list when launched from CTLI, else to the admin menu', async () => {
    mockFetch(() => ({ status: 200 }));
    renderPage('/transaction-types/maintain?from=list');
    await userEvent.click(screen.getByText('F3=Exit'));
    expect(screen.getByText('CTLI LIST')).toBeInTheDocument();
  });

  it('F3 without caller goes to admin menu', async () => {
    mockFetch(() => ({ status: 200 }));
    renderPage();
    await userEvent.click(screen.getByText('F3=Exit'));
    expect(screen.getByText('ADMIN MENU')).toBeInTheDocument();
  });
});
