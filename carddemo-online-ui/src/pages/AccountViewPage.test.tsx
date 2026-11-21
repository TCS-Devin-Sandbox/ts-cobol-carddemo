import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { getSession, setSession } from '../api/client';
import { mockFetch } from '../test/fetchMock';

const account = {
  activeStatus: 'Y', currentBalance: -1234.56, creditLimit: 9000, cashCreditLimit: 300,
  openDate: '2020-01-01', expirationDate: '2030-01-01', reissueDate: '2025-03-01',
  currentCycleCredit: 25.01, currentCycleDebit: 6.5, accountGroupId: 'GROUP',
};
const customer = {
  customerId: '000000007', firstName: 'Example', middleName: 'Q', lastName: 'Fixture',
  addressLine1: '123 Sample St', addressLine2: 'Unit 2', addressLine3: 'Sample City',
  stateCode: 'TS', countryCode: 'USA', zipCode: '01234-5678', phoneNumber1: '(000)000-0000  ',
  phoneNumber2: '(000)000-0001  ', socialSecurityNumber: '000001234', governmentIssuedId: 'TEST-ID',
  dateOfBirth: '1980-02-03', electronicFundsTransferAccountId: '0000000812',
  primaryCardholderIndicator: 'Y', ficoCreditScore: '075',
};
const success = { account, customer, error: null };
const empty = { account: null, customer: null };
const home = { status: 200, body: { options: ['01. Account View'], tranId: 'CM00', program: 'COMEN01C' } };
const found = '00000000042';
const field = () => screen.getByRole('textbox', { name: 'Account Number' });
const enter = () => fireEvent.keyDown(field(), { key: 'Enter' });

function open(path = '/accounts/view', signedIn = true) {
  if (signedIn) setSession({ token: 't', userId: 'USER', userType: 'U', roles: ['USER'] });
  render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>);
}

function protectedValues(region: string) {
  return within(screen.getByRole('region', { name: region })).getAllByRole('definition').map(el => el.textContent);
}

describe('COACTVWC / CACTVWA', () => {
  it('0000 / 1100: shows blank, focused first entry from CM00', async () => {
    const fetch = mockFetch((method) => method === 'GET' ? home : {
      status: 200, body: { route: '/accounts/view', tranId: 'CAVW', program: 'COACTVWC' },
    });
    open('/menu');
    await userEvent.click(await screen.findByRole('button', { name: /01. Account View/ }));
    expect(await screen.findByRole('heading', { name: 'View Account' })).toBeInTheDocument();
    expect(screen.getByText('Tran: CAVW')).toBeInTheDocument();
    expect(screen.getByText('Prog: COACTVWC')).toBeInTheDocument();
    expect(screen.getByText(/AWS Mainframe Modernization.*CardDemo/)).toBeInTheDocument();
    expect(screen.getByTestId('infomsg')).toHaveTextContent('Enter or update id of account to display');
    expect(field()).toHaveFocus();
    expect(field()).toHaveValue('');
    expect(field()).toHaveAttribute('maxlength', '11');
    expect(screen.getAllByRole('textbox')).toHaveLength(1);
    expect(protectedValues('Account Details').every(value => value === '')).toBe(true);
    expect(protectedValues('Customer Details').every(value => value === '')).toBe(true);
    expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/menus/main', '/api/menus/main/select']);
  });

  it('0000: redirects an unauthenticated direct entry', () => {
    const fetch = mockFetch(() => ({ status: 500 }));
    open('/accounts/view', false);
    expect(screen.getByRole('heading', { name: 'CardDemo sign-on' })).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('2000 / 2100 / 1200: Enter preserves the ID and displays all protected values', async () => {
    const fetch = mockFetch(() => ({ status: 200, body: success }));
    open();
    await userEvent.type(field(), found);
    enter();
    expect(await screen.findByText('-1,234.56')).toBeInTheDocument();
    expect(fetch.mock.calls[0]).toEqual(['/api/accounts/view', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ accountId: found }), headers: expect.objectContaining({ Authorization: 'Bearer t' }),
    })]);
    expect(field()).toHaveFocus();
    expect(field()).toHaveValue(found);
    expect(protectedValues('Account Details')).toEqual([
      'Y', '-1,234.56', '2020-01-01', '+9,000.00', '2030-01-01', '+300.00', '2025-03-01', '+25.01', 'GROUP', '+6.50',
    ]);
    expect(protectedValues('Customer Details')).toEqual([
      '000000007', '000-00-1234', '1980-02-03', '075', 'Example', 'Q', 'Fixture', '123 Sample St', 'TS', 'Unit 2',
      '01234', 'Sample City', 'USA', '(000)000-0000', 'TEST-ID', '(000)000-0001', '0000000812', 'Y',
    ]);
  });

  it('1300 / 1400 / 2200 / 2210: blank marks the focused field, resubmission clears stale results', async () => {
    const fetch = mockFetch((_method, _url, body) => {
      switch ((body as { accountId: string }).accountId) {
        case found: return { status: 200, body: success };
        case '': return { status: 200, body: { ...empty, error: {
          code: 'NO_INPUT', message: 'No input received', accountNumberInvalid: true,
        } } };
        default: return { status: 200, body: { ...empty, error: {
          code: 'INVALID_ACCOUNT_ID', message: 'Account Filter must  be a non-zero 11 digit number', accountNumberInvalid: true,
        } } };
      }
    });
    open();
    await userEvent.click(screen.getByRole('button', { name: 'Enter=View' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No input received');
    expect(field()).toHaveFocus();
    expect(field()).toHaveAttribute('aria-invalid', 'true');
    expect(field()).toHaveAttribute('placeholder', '*');
    await userEvent.type(field(), found);
    enter();
    expect(await screen.findByText('Fixture')).toBeInTheDocument();
    expect(field()).toHaveAttribute('aria-invalid', 'false');
    await userEvent.clear(field());
    await userEvent.type(field(), '00000000000');
    fireEvent.keyDown(field(), { key: 'F8' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Account Filter must be a non-zero 11 digit number');
    expect(field()).toHaveValue('00000000000');
    expect(screen.queryByText('Fixture')).not.toBeInTheDocument();
    expect(screen.queryByText('-1,234.56')).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it('1200 / 9400: a missing customer leaves the account visible and the field valid', async () => {
    mockFetch(() => ({ status: 200, body: { account, customer: null, error: {
      code: 'CUSTOMER_NOT_FOUND', message: 'CustId:000000008 not found in customer master.', accountNumberInvalid: false,
    } } }));
    open();
    await userEvent.type(field(), found);
    enter();
    expect(await screen.findByRole('alert')).toHaveTextContent('CustId:000000008 not found in customer master.');
    expect(screen.getByText('-1,234.56')).toBeInTheDocument();
    expect(protectedValues('Customer Details').every(value => value === '')).toBe(true);
    expect(field()).toHaveAttribute('aria-invalid', 'false');
  });

  it.each(['keyboard', 'button'])('0000 / CSSTRPFY: F3 exits via %s without a lookup or sign-off', async mode => {
    const fetch = mockFetch(() => home);
    open();
    if (mode === 'keyboard') fireEvent.keyDown(field(), { key: 'F3' });
    else await userEvent.click(screen.getByRole('button', { name: 'F3=Exit' }));
    expect(await screen.findByRole('button', { name: /01. Account View/ })).toBeInTheDocument();
    expect(getSession()?.userType).toBe('U');
    expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/menus/main']);
  });

  it('ABEND-ROUTINE: shows a lookup failure without exposing database details', async () => {
    mockFetch(() => ({ status: 503, body: { message: 'database diagnostics' } }));
    open();
    await userEvent.type(field(), found);
    enter();
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to look up account');
    expect(field()).toHaveFocus();
  });
});
