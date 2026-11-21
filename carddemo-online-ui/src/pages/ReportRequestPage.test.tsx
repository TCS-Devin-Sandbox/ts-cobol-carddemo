import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { setSession } from '../api/client';
import { mockFetch } from '../test/fetchMock';

const session = { token: 'report-test', userId: 'USER0001', userType: 'U' as const, roles: ['USER'] };
const monthly = () => screen.getByRole('checkbox', { name: 'Monthly (Current Month)' });
const confirmation = () => screen.getByRole('textbox', { name: 'Confirm (Y/N)' });
const enter = () => fireEvent.keyDown(window, { key: 'Enter' });

function openReport() {
  setSession(session);
  render(<MemoryRouter initialEntries={['/reports']}><App /></MemoryRouter>);
}

describe('CR00 CORPT00C', () => {
  it('MAIN-PARA requires session, then CM00 option 09 opens the blank CORPT00 map', async () => {
    const fetch = mockFetch((_method, url) => {
      if (url === '/api/auth/login') return { status: 200, body: session };
      if (url === '/api/menus/main') return { status: 200, body: {
        options: Array.from({ length: 9 }, (_, i) => i < 8 ? `Option ${i + 1}` : '09. Transaction Reports'),
      } };
      if (url === '/api/menus/main/select') return { status: 200, body: { route: '/reports' } };
      throw new Error(`Unexpected request: ${url}`);
    });
    render(<MemoryRouter initialEntries={['/reports']}><App /></MemoryRouter>);
    expect(screen.getByText('CardDemo sign-on')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('User ID'), 'test');
    await userEvent.type(screen.getByLabelText('Password'), 'password');
    fireEvent.submit(screen.getByText('Sign on').closest('form')!);
    await userEvent.click(await screen.findByRole('button', { name: '09. Transaction Reports' }));
    expect(await screen.findByRole('heading', { name: 'Transaction Reports' })).toBeInTheDocument();
    expect(screen.getByText('Tran: CR00')).toBeInTheDocument();
    expect(screen.getByText('Prog: CORPT00C')).toBeInTheDocument();
    expect(monthly()).toHaveFocus();
    expect(monthly()).not.toBeChecked();
    expect(confirmation()).toHaveValue('');
    expect(fetch.mock.calls[2][1]?.body).toBe('{"option":"9"}');
  });

  it('RECEIVE maps Enter, invalid keys and PF3 back to the main menu', async () => {
    const fetch = mockFetch((method, url) => {
      if (method === 'GET' && url === '/api/menus/main') return { status: 200, body: { options: [] } };
      expect(url).toBe('/api/reports');
      return { status: 200, body: { message: 'Select a report type to print report...',
        severity: 'error', focus: 'monthly', reset: false } };
    });
    openReport();
    fireEvent.keyDown(window, { key: 'F5' });
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid key pressed. Please see below...');
    expect(fetch).not.toHaveBeenCalled();
    enter();
    expect(await screen.findByRole('alert')).toHaveTextContent('Select a report type to print report...');
    expect(monthly()).toHaveFocus();
    fireEvent.keyDown(window, { key: 'F3' });
    expect(await screen.findByRole('heading', { name: 'Main Menu' })).toBeInTheDocument();
  });

  it('SEND and INITIALIZE retain edits until confirmation and clear on cancellation or acceptance', async () => {
    const fetch = mockFetch((_method, url, body) => {
      expect(url).toBe('/api/reports');
      const value = (body as { confirmation: string }).confirmation;
      if (!value) return { status: 200, body: { message: 'Please confirm to print the Custom report...',
        severity: 'error', focus: 'confirmation', reset: false } };
      if (value === 'n') return { status: 200, body: { focus: 'monthly', reset: true } };
      return { status: 202, body: { message: 'Custom report submitted for printing ...',
        severity: 'success', focus: 'monthly', reset: true, jobId: 'accepted' } };
    });
    openReport();
    const custom = screen.getByRole('checkbox', { name: 'Custom (Date Range)' });
    await userEvent.click(custom);
    const startMonth = screen.getByRole('textbox', { name: 'Start Date Month' });
    await userEvent.type(startMonth, '07');
    enter();
    expect(await screen.findByRole('alert')).toHaveTextContent('Please confirm to print the Custom report...');
    expect(confirmation()).toHaveFocus();
    expect(startMonth).toHaveValue('07');
    await userEvent.type(confirmation(), 'n');
    enter();
    await waitFor(() => expect(monthly()).toHaveFocus());
    expect(custom).not.toBeChecked();
    expect(startMonth).toHaveValue('');
    expect(confirmation()).toHaveValue('');
    await userEvent.click(custom);
    await userEvent.type(startMonth, '06');
    await userEvent.type(confirmation(), 'Y');
    await userEvent.click(screen.getByRole('button', { name: 'Enter=Continue' }));
    expect(await screen.findByText('Custom report submitted for printing ...')).toBeInTheDocument();
    expect(custom).not.toBeChecked();
    expect(monthly()).toHaveFocus();
    expect(JSON.parse(String(fetch.mock.calls[2][1]?.body))).toMatchObject({
      custom: true, startDate: { month: '06' }, confirmation: 'Y',
    });
  });

  it('WRITE-JOBSUB-TDQ replacement keeps the request on a launcher failure', async () => {
    mockFetch(() => ({ status: 503, body: { message: 'Unable to submit transaction report...' } }));
    openReport();
    await userEvent.click(monthly());
    await userEvent.type(confirmation(), 'Y');
    enter();
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to submit transaction report...');
    expect(monthly()).toBeChecked();
    expect(confirmation()).toHaveValue('Y');
  });
});
