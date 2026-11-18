import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import { setSession } from './api/client';
import { mockFetch } from './test/fetchMock';

describe('routing and admin guard', () => {
  it('redirects unauthenticated users to login', () => {
    render(<MemoryRouter initialEntries={['/transaction-types']}><App /></MemoryRouter>);
    expect(screen.getByText('CardDemo sign-on')).toBeInTheDocument();
  });

  it('redirects non-admin sessions to login', () => {
    setSession({ token: 't', username: 'user', roles: ['USER'] });
    render(<MemoryRouter initialEntries={['/admin']}><App /></MemoryRouter>);
    expect(screen.getByText('CardDemo sign-on')).toBeInTheDocument();
  });

  it('rejects non-admin login', async () => {
    mockFetch(() => ({ status: 200, body: { token: 't', username: 'user', roles: ['USER'] } }));
    render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText('User ID'), 'user');
    await userEvent.type(screen.getByLabelText('Password'), 'user123');
    await userEvent.click(screen.getByText('Sign on'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Access restricted to administrators');
  });

  it('admin login shows admin menu options 5 and 6', async () => {
    mockFetch(() => ({ status: 200, body: { token: 't', username: 'admin', roles: ['ADMIN'] } }));
    render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText('User ID'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'admin123');
    await userEvent.click(screen.getByText('Sign on'));
    expect(await screen.findByText('5. Transaction Type List/Update (Db2)')).toBeInTheDocument();
    expect(screen.getByText('6. Transaction Type Maintenance (Db2)')).toBeInTheDocument();
  });
});
