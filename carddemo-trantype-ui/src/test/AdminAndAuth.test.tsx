import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { RequireAdmin } from '../auth';
import { AdminMenuPage } from '../pages/AdminMenuPage';
import { LoginPage } from '../pages/LoginPage';
import { apiError, mockApi, renderAt } from './helpers';

describe('Admin menu placeholder (CA00 / COADM01C)', () => {
  it('lists options 5 and 6 and navigates to them', async () => {
    renderAt(mockApi(), '/admin', <AdminMenuPage />);
    expect(screen.getByText('5. Transaction Type List/Update (Db2)')).toBeInTheDocument();
    expect(screen.getByText('6. Transaction Type Maintenance (Db2)')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('option'), '5');
    await userEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/transaction-types');
  });

  it('rejects invalid option', async () => {
    renderAt(mockApi(), '/admin', <AdminMenuPage />);
    await userEvent.type(screen.getByLabelText('option'), '9');
    await userEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));
    expect(screen.getByTestId('errmsg')).toHaveTextContent('Please enter a valid option number...');
  });
});

describe('Admin guard', () => {
  it('non-admin user is rejected', async () => {
    const api = mockApi({ me: vi.fn().mockResolvedValue({ username: 'user', roles: ['USER'] }) });
    renderAt(api, '/admin', <RequireAdmin><AdminMenuPage /></RequireAdmin>);
    expect(await screen.findByText('Access restricted to administrators')).toBeInTheDocument();
    expect(screen.queryByText('5. Transaction Type List/Update (Db2)')).not.toBeInTheDocument();
  });

  it('anonymous user is redirected to sign-on', async () => {
    const api = mockApi({ me: vi.fn().mockRejectedValue(apiError(401, 'UNAUTHORIZED', 'x')) });
    renderAt(api, '/admin', <RequireAdmin><AdminMenuPage /></RequireAdmin>);
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
  });

  it('sign-on shows wrong password message', async () => {
    const api = mockApi({
      me: vi.fn().mockRejectedValue(apiError(401, 'UNAUTHORIZED', 'x')),
      login: vi.fn().mockRejectedValue(apiError(401, 'UNAUTHORIZED', 'Wrong Password. Try again ...')),
    });
    renderAt(api, '/login', <LoginPage />);
    await userEvent.click(screen.getByRole('button', { name: 'ENTER=Sign-in' }));
    expect(screen.getByTestId('errmsg')).toHaveTextContent('Please enter User ID ...');
    await userEvent.type(screen.getByLabelText('User ID'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'bad');
    await userEvent.click(screen.getByRole('button', { name: 'ENTER=Sign-in' }));
    await waitFor(() => expect(screen.getByTestId('errmsg')).toHaveTextContent('Wrong Password. Try again ...'));
  });
});
