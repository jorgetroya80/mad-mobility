import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LocateButton } from './LocateButton';

describe('LocateButton', () => {
  it('asks for the location when pressed', async () => {
    const onLocate = vi.fn<() => void>();
    render(<LocateButton status="denied" onLocate={onLocate} />);

    await userEvent.click(screen.getByRole('button', { name: 'Mi ubicación' }));

    expect(onLocate).toHaveBeenCalledOnce();
  });

  it('is not busy once the location is known', () => {
    render(<LocateButton status="granted" onLocate={() => {}} />);

    const button = screen.getByRole('button', { name: 'Mi ubicación' });
    expect(button).not.toHaveAttribute('aria-busy');
    expect(button).not.toHaveAttribute('aria-disabled');
  });

  it('is busy and ignores presses while the location is pending', async () => {
    const onLocate = vi.fn<() => void>();
    render(<LocateButton status="pending" onLocate={onLocate} />);

    const button = screen.getByRole('button', { name: 'Localizando…' });
    await userEvent.click(button);

    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(button).toHaveAttribute('aria-disabled', 'true');
    expect(onLocate).not.toHaveBeenCalled();
  });

  // aria-disabled instead of disabled: a disabled button would drop the focus it just had.
  it('keeps the focus while pending', async () => {
    const { rerender } = render(<LocateButton status="denied" onLocate={() => {}} />);
    const button = screen.getByRole('button');
    await userEvent.click(button);

    rerender(<LocateButton status="pending" onLocate={() => {}} />);

    expect(button).toHaveFocus();
  });

  it('forwards native button props', () => {
    render(
      <LocateButton status="granted" onLocate={() => {}} className="extra" data-testid="locate" />,
    );

    expect(screen.getByTestId('locate')).toHaveClass('extra');
  });
});
