import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import { getSession, setSession } from './api/client';
import { mockFetch } from './test/fetchMock';

describe('CC00 CM00 CA00 paragraphs', () => {
  it('MAIN-PARA guards both menus', () => {
    render(<MemoryRouter initialEntries={['/menu']}><App /></MemoryRouter>);
    expect(screen.getByText('CardDemo sign-on')).toBeInTheDocument();
  });
  it('SEND-PLAIN-TEXT F3 thanks and invalid keys', () => {
    render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>);
    fireEvent.keyDown(window, { key: 'F5' });
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid key pressed. Please see below...');
    fireEvent.keyDown(window, { key: 'F3' });
    expect(screen.getByText('Thank you for using CardDemo application...')).toBeInTheDocument();
  });
  it.each(['A', 'U'] as const)('PROCESS-ENTER-KEY routes type %s and RETURN-TO-SIGNON-SCREEN clears session', async type => {
    const fetch = mockFetch((method, url) => {
      if (url === '/api/auth/login') return { status: 200, body: { token: 't', userId: 'TEST', userType: type, roles: [type === 'A' ? 'ADMIN' : 'USER'] } };
      if (method === 'GET') return { status: 200, body: { options: ['01. Example'], program: 'MENU', tranId: 'MENU' } };
      return { status: 200, body: { message: 'This option is not installed ...', severity: 'info' } };
    });
    render(<MemoryRouter initialEntries={['/login']}><App /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText('User ID'), 'test');
    await userEvent.type(screen.getByLabelText('Password'), 'password');
    fireEvent.submit(screen.getByText('Sign on').closest('form')!);
    expect(await screen.findByText(type === 'A' ? 'Admin Menu' : 'Main Menu')).toBeInTheDocument();
    await screen.findByText('01. Example');
    expect(JSON.parse(String(fetch.mock.calls[0][1]!.body))).toEqual({ userId: 'test', password: 'password' });
    await userEvent.type(screen.getByLabelText('Option'), '1');
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(await screen.findByText('This option is not installed ...')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'F3' });
    expect(getSession()).toBeNull();
    expect(screen.getByText('CardDemo sign-on')).toBeInTheDocument();
  });
  it('MAIN-PARA denies USER access to CA00', () => {
    setSession({ token: 't', userId: 'USER', userType: 'U', roles: ['USER'] });
    render(<MemoryRouter initialEntries={['/admin']}><App /></MemoryRouter>);
    expect(screen.getByText('CardDemo sign-on')).toBeInTheDocument();
  });
});
