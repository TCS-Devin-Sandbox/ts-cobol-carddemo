import { render, screen } from '@testing-library/react';
import { MessageLine } from './MessageLine';

it('renders info and error lines', () => {
  render(<MessageLine info="Type U to update, D to delete any record" error="Please select only 1 action" />);
  expect(screen.getByTestId('infomsg')).toHaveTextContent('Type U to update, D to delete any record');
  expect(screen.getByRole('alert')).toHaveTextContent('Please select only 1 action');
});
