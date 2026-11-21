import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FunctionKeyBar } from './FunctionKeyBar';

describe('FunctionKeyBar', () => {
  it('keeps clickable actions and suppresses disabled and hidden actions', async () => {
    const action = vi.fn();
    render(<FunctionKeyBar keys={[
      { label: 'F3=Exit', onClick: action },
      { label: 'F5=Save', onClick: action, disabled: true },
      { label: 'F4=Delete', onClick: action, hidden: true },
    ]} />);
    await userEvent.click(screen.getByText('F3=Exit'));
    expect(screen.getByText('F5=Save')).toBeDisabled();
    fireEvent.click(screen.getByText('F5=Save'));
    expect(action).toHaveBeenCalledOnce();
    expect(screen.queryByText('F4=Delete')).not.toBeInTheDocument();
  });

  it('renders an empty bar', () => {
    render(<FunctionKeyBar keys={[]} />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
