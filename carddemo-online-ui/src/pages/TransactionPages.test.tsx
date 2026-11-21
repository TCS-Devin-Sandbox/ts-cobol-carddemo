import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TransactionListPage, TransactionViewPage } from './TransactionPages';
import { loginAsAdmin, mockFetch } from '../test/fetchMock';

const row = { transaction_id: '0000000000000001', description: 'SYNTHETIC', amount: -1.23, origination_timestamp: '2022-07-05 10:00:00.000000' };
function renderPage(view = false) {
  loginAsAdmin();
  return render(<MemoryRouter initialEntries={[view ? '/transactions/view' : '/transactions']}><Routes>
    <Route path="/transactions" element={<TransactionListPage />} />
    <Route path="/transactions/view" element={<TransactionViewPage />} />
    <Route path="/menu" element={<div>MAIN MENU</div>} />
  </Routes></MemoryRouter>);
}
describe('CT00', () => {
  it('POPULATE-TRAN-DATA formats every list column to the COTRN00 BMS width', async () => {
    mockFetch(() => ({ status: 200, body: { rows: [{ ...row, transaction_id: '1234567890123456EXTRA', description: 'D'.repeat(100), amount: 100000001.23 }], pageNumber: 1, message: '' } }));
    renderPage();
    expect(await screen.findByText('1234567890123456')).toBeInTheDocument();
    expect(screen.getByText('07-05-22')).toBeInTheDocument();
    expect(screen.getByText('D'.repeat(26))).toBeInTheDocument();
    expect(screen.getByText('+00000001.23')).toBeInTheDocument();
  });
  it('PROCESS-ENTER-KEY permits selecting a later row with preceding blank selection fields', async () => {
    const second = { ...row, transaction_id: '0000000000000002' };
    const fetch = mockFetch((_method, url) => ({ status: 200, body: url.includes('/view') ? { transaction: second, message: '' } : { rows: [row, second], pageNumber: 1, message: '' } }));
    renderPage();
    await userEvent.type(await screen.findByLabelText('Select row 2'), 'S');
    fireEvent.keyDown(window, { key: 'Enter' });
    await screen.findByText('Transaction View');
    await waitFor(() => expect(fetch.mock.calls[fetch.mock.calls.length - 1]?.[0]).toContain('transactionId=0000000000000002'));
  });
  it('MAIN-PARA SEND-TRNLST-SCREEN POPULATE-HEADER-INFO INITIALIZE-TRAN-DATA', async () => {
    mockFetch(() => ({ status: 200, body: { rows: [], pageNumber: 0, message: 'You are at the top of the page...' } }));
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('You are at the top of the page...');
    expect(screen.getByText(/COTRN00C/)).toBeInTheDocument();
  });
  it('PROCESS-PF7-KEY PROCESS-PF8-KEY boundaries; RETURN-TO-PREV-SCREEN', async () => {
    const fetch = mockFetch(() => ({ status: 200, body: { rows: [row], pageNumber: 1, message: '' } }));
    renderPage();
    await screen.findByText('SYNTHETIC');
    fireEvent.keyDown(window, { key: 'F8' });
    expect(screen.getByRole('alert')).toHaveTextContent('You are already at the bottom of the page...');
    fireEvent.keyDown(window, { key: 'F7' });
    expect(screen.getByRole('alert')).toHaveTextContent('You are already at the top of the page...');
    expect(fetch).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(window, { key: 'F3' });
    expect(screen.getByText('MAIN MENU')).toBeInTheDocument();
  });
  it('PROCESS-PAGE-FORWARD PROCESS-PAGE-BACKWARD READPREV boundaries', async () => {
    const first = Array.from({ length: 10 }, (_, i) => ({ ...row, transaction_id: String(i + 1).padStart(16, '0') }));
    const fetch = mockFetch((_method, url) => ({ status: 200, body: url.includes('FORWARD')
      ? { rows: [{ ...row, transaction_id: '0000000000000011' }], pageNumber: 2, message: 'You have reached the bottom of the page...' }
      : { rows: first, pageNumber: 1, message: '' } }));
    renderPage();
    await screen.findByText('0000000000000010');
    fireEvent.keyDown(window, { key: 'F8' });
    expect(await screen.findByText('0000000000000011')).toBeInTheDocument();
    expect(fetch.mock.calls[fetch.mock.calls.length - 1]?.[0]).toContain('startKey=0000000000000010&direction=FORWARD&pageNumber=1');
    fireEvent.keyDown(window, { key: 'F7' });
    await screen.findByText('0000000000000010');
    expect(fetch.mock.calls[fetch.mock.calls.length - 1]?.[0]).toContain('startKey=0000000000000011&direction=BACKWARD&pageNumber=2');
  });
  it('RECEIVE-TRNLST-SCREEN PROCESS-ENTER-KEY first selection wins, lowercase s selects', async () => {
    mockFetch((_method, url) => ({ status: 200, body: url.includes('/view') ? { transaction: row, message: '' } : { rows: [row], pageNumber: 1, message: '' } }));
    renderPage();
    await userEvent.type(await screen.findByLabelText('Select row 1'), 's');
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByText('Transaction View')).toBeInTheDocument();
    expect(await screen.findByText('SYNTHETIC')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'F3' });
    expect(await screen.findByText('Transaction List')).toBeInTheDocument();
  });
  it('PROCESS-ENTER-KEY invalid selection and invalid AID', async () => {
    mockFetch(() => ({ status: 200, body: { rows: [row], pageNumber: 1, message: '' } }));
    renderPage();
    await userEvent.type(await screen.findByLabelText('Select row 1'), 'X');
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid selection. Valid value is S');
    fireEvent.keyDown(window, { key: 'F4' });
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid key pressed. Please see below...');
  });
  it('PROCESS-ENTER-KEY passes Search Tran ID to STARTBR', async () => {
    const fetch = mockFetch(() => ({ status: 200, body: { rows: [], pageNumber: 0, message: '' } }));
    renderPage();
    await userEvent.type(screen.getByLabelText('Search Tran ID'), '0000000000000011');
    fireEvent.keyDown(window, { key: 'Enter' });
    await waitFor(() => expect(fetch.mock.calls[fetch.mock.calls.length - 1]?.[0]).toContain('startKey=0000000000000011'));
  });
});
describe('CT01', () => {
  const formattingCases: [string, string | number, string, string][] = [
    ['transaction_id', '1234567890123456EXTRA', 'Transaction ID:', '1234567890123456'],
    ['card_number', '9876543210987654EXTRA', 'Card Number:', '9876543210987654'],
    ['transaction_type_code', '01EXTRA', 'Type CD:', '01'],
    ['transaction_category_code', 7, 'Category CD:', '0007'],
    ['source', '1234567890EXTRA', 'Source:', '1234567890'],
    ['description', 'D'.repeat(100), 'Description:', 'D'.repeat(60)],
    ['amount', 1.23, 'Amount:', '+00000001.23'],
    ['origination_timestamp', '2022-07-05 10:00:00.000000', 'Orig Date:', '2022-07-05'],
    ['processed_timestamp', '2022-08-06 11:22:33.444444', 'Proc Date:', '2022-08-06'],
    ['merchant_id', 12345, 'Merchant ID:', '000012345'],
    ['merchant_name', 'N'.repeat(50), 'Merchant Name:', 'N'.repeat(30)],
    ['merchant_city', 'C'.repeat(50), 'Merchant City:', 'C'.repeat(25)],
    ['merchant_zip_code', '1234567890EXTRA', 'Merchant Zip:', '1234567890'],
  ];
  it.each(formattingCases)('PROCESS-ENTER-KEY COTRN01 BMS formats %s', async (key, value, label, expected) => {
    mockFetch(() => ({ status: 200, body: { transaction: { ...row, [key]: value }, message: '' } }));
    renderPage(true);
    await userEvent.type(screen.getByLabelText('Tran ID'), '1');
    fireEvent.keyDown(window, { key: 'Enter' });
    await waitFor(() => expect(screen.getByLabelText(label).textContent).toBe(expected));
  });
  it('SEND-TRNVIEW-SCREEN renders labeled fields in BMS order and blanks on INITIALIZE-ALL-FIELDS', async () => {
    renderPage(true);
    expect(screen.getAllByRole('term').map(element => element.textContent)).toEqual(formattingCases.map(([, , label]) => label));
    expect(screen.getAllByRole('definition').every(element => element.textContent === '')).toBe(true);
  });
  it('PROCESS-ENTER-KEY preserves WS-TRAN-AMT eight-digit truncation', async () => {
    mockFetch(() => ({ status: 200, body: { transaction: { ...row, amount: -100000001.23 }, message: '' } }));
    renderPage(true);
    await userEvent.type(screen.getByLabelText('Tran ID'), '1');
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByText('-00000001.23')).toBeInTheDocument();
  });
  it('PROCESS-ENTER-KEY RECEIVE-TRNVIEW-SCREEN SEND-TRNVIEW-SCREEN CLEAR-CURRENT-SCREEN INITIALIZE-ALL-FIELDS', async () => {
    mockFetch(() => ({ status: 200, body: { transaction: row, message: '' } }));
    renderPage(true);
    await userEvent.type(screen.getByLabelText('Tran ID'), '0000000000000001');
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByText('SYNTHETIC')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'F4' });
    expect(screen.getByLabelText('Tran ID')).toHaveValue('');
    expect(screen.queryByText('SYNTHETIC')).not.toBeInTheDocument();
  });
  it('MAIN-PARA PF5 browse and RETURN-TO-PREV-SCREEN', async () => {
    mockFetch(() => ({ status: 200, body: { rows: [], pageNumber: 0, message: '' } }));
    renderPage(true);
    fireEvent.keyDown(window, { key: 'F5' });
    expect(await screen.findByText('Transaction List')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'F3' });
    expect(screen.getByText('MAIN MENU')).toBeInTheDocument();
  });
  it('READ-TRANSACT-FILE not found and invalid AID', async () => {
    mockFetch(() => ({ status: 200, body: { transaction: null, message: 'Transaction ID NOT found...' } }));
    renderPage(true);
    await userEvent.type(screen.getByLabelText('Tran ID'), 'ABC');
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Transaction ID NOT found...');
    fireEvent.keyDown(window, { key: 'F8' });
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid key pressed. Please see below...');
  });
  it('READ-TRANSACT-FILE transport failure', async () => {
    mockFetch(() => ({ status: 500, body: { message: 'Unable to lookup Transaction...' } }));
    renderPage(true);
    await userEvent.type(screen.getByLabelText('Tran ID'), '1');
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to lookup Transaction...');
  });
});
