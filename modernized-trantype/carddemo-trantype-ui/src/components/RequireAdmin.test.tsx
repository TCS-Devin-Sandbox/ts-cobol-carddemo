import { screen } from '@testing-library/react';
import { RequireAdmin } from './RequireAdmin';
import { renderAt } from '../test/utils';
import { MSG } from '../logic/messages';

describe('RequireAdmin (TP-53, TP-54)', () => {
  it('rejects non-admin users with the COMEN01C message', () => {
    renderAt('/x', <RequireAdmin><div>SECRET</div></RequireAdmin>, { token: 't', userId: 'USER0001', userType: 'U' });
    expect(screen.getByTestId('admin-only')).toHaveTextContent(MSG.ADMIN_ONLY.trim());
    expect(screen.queryByText('SECRET')).not.toBeInTheDocument();
  });

  it('redirects anonymous users to login', () => {
    renderAt('/x', <RequireAdmin><div>SECRET</div></RequireAdmin>, null);
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument();
  });

  it('renders children for admins', () => {
    renderAt('/x', <RequireAdmin><div>SECRET</div></RequireAdmin>);
    expect(screen.getByText('SECRET')).toBeInTheDocument();
  });
});
