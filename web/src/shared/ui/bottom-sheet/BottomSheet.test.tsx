import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BottomSheet } from './BottomSheet';

function renderSheet(props: { defaultExpanded?: boolean } = {}) {
  render(
    <BottomSheet label="Estaciones" {...props}>
      <p>Contenido del panel</p>
    </BottomSheet>,
  );
  const handle = screen.getByRole('button', { name: 'Estaciones' });
  const content = screen.getByText('Contenido del panel').parentElement;
  return { handle, content };
}

describe('BottomSheet', () => {
  // jsdom and Testing Library ignore `inert`, so the tests assert the attribute itself.
  it('starts collapsed with its content rendered but inert', () => {
    const { handle, content } = renderSheet();

    expect(handle).toHaveAttribute('type', 'button');
    expect(handle).toHaveAttribute('aria-expanded', 'false');
    expect(content).toHaveAttribute('inert');
    expect(content).not.toHaveAttribute('hidden');
  });

  it('slides down to show only the handle when collapsed and up when expanded', async () => {
    const user = userEvent.setup();
    const { handle } = renderSheet();
    const sheet = handle.parentElement;

    // One variable sets the handle height, the visible strip and the space kept in the flow.
    expect(sheet).toHaveClass('h-[60dvh]', 'translate-y-[calc(100%-var(--handle-height))]');
    expect(handle).toHaveClass('h-(--handle-height)');

    await user.click(handle);
    expect(sheet).toHaveClass('h-[60dvh]', 'translate-y-0');
    expect(sheet).not.toHaveClass('translate-y-[calc(100%-var(--handle-height))]');
  });

  it('keeps the handle height in the page flow so it covers nothing above it', () => {
    const { handle } = renderSheet();
    const strip = handle.parentElement?.parentElement;

    expect(strip).toHaveClass('[--handle-height:--spacing(11)]', 'h-(--handle-height)', 'shrink-0');
  });

  it('animates the slide unless the user prefers reduced motion', () => {
    const { handle } = renderSheet();

    expect(handle.parentElement).toHaveClass(
      'transition-transform',
      'duration-300',
      'ease-out',
      'motion-reduce:transition-none',
    );
  });

  it('points the handle at the content region', () => {
    const { handle, content } = renderSheet({ defaultExpanded: true });

    expect(content).toHaveAttribute('id', handle.getAttribute('aria-controls'));
    expect(screen.getByRole('region', { name: 'Estaciones' })).toBe(content);
  });

  it('starts expanded when asked to', () => {
    const { handle, content } = renderSheet({ defaultExpanded: true });

    expect(handle).toHaveAttribute('aria-expanded', 'true');
    expect(content).not.toHaveAttribute('inert');
  });

  it('expands and collapses on click', async () => {
    const user = userEvent.setup();
    const { handle, content } = renderSheet();

    await user.click(handle);
    expect(handle).toHaveAttribute('aria-expanded', 'true');
    expect(content).not.toHaveAttribute('inert');

    await user.click(handle);
    expect(handle).toHaveAttribute('aria-expanded', 'false');
    expect(content).toHaveAttribute('inert');
  });

  it.each([
    ['Enter', '{Enter}'],
    ['Space', ' '],
  ])('expands and collapses with %s', async (_key, keys) => {
    const user = userEvent.setup();
    const { handle, content } = renderSheet();

    await user.tab();
    expect(handle).toHaveFocus();

    await user.keyboard(keys);
    expect(handle).toHaveAttribute('aria-expanded', 'true');
    expect(content).not.toHaveAttribute('inert');

    await user.keyboard(keys);
    expect(handle).toHaveAttribute('aria-expanded', 'false');
    expect(content).toHaveAttribute('inert');
  });
});

describe('BottomSheet controlled', () => {
  function renderControlled(
    expanded: boolean,
    onExpandedChange = vi.fn<(expanded: boolean) => void>(),
  ) {
    const view = render(
      <BottomSheet label="Estaciones" expanded={expanded} onExpandedChange={onExpandedChange}>
        <p>Contenido del panel</p>
      </BottomSheet>,
    );
    const handle = screen.getByRole('button', { name: 'Estaciones' });
    const content = screen.getByText('Contenido del panel').parentElement;
    return { ...view, handle, content, onExpandedChange };
  }

  it('asks the parent to expand and stays collapsed until the parent agrees', async () => {
    const user = userEvent.setup();
    const { handle, content, onExpandedChange } = renderControlled(false);

    await user.click(handle);

    expect(onExpandedChange).toHaveBeenCalledExactlyOnceWith(true);
    expect(handle).toHaveAttribute('aria-expanded', 'false');
    expect(content).toHaveAttribute('inert');
  });

  it('asks the parent to collapse when expanded', async () => {
    const user = userEvent.setup();
    const { handle, onExpandedChange } = renderControlled(true);

    await user.click(handle);

    expect(onExpandedChange).toHaveBeenCalledExactlyOnceWith(false);
    expect(handle).toHaveAttribute('aria-expanded', 'true');
  });

  it('follows the parent when it changes expanded', () => {
    const { handle, content, rerender } = renderControlled(false);

    rerender(
      <BottomSheet
        label="Estaciones"
        expanded
        onExpandedChange={vi.fn<(expanded: boolean) => void>()}
      >
        <p>Contenido del panel</p>
      </BottomSheet>,
    );

    expect(handle).toHaveAttribute('aria-expanded', 'true');
    expect(content).not.toHaveAttribute('inert');
  });

  it('toggles by itself and notifies when only onExpandedChange is given', async () => {
    const user = userEvent.setup();
    const onExpandedChange = vi.fn<(expanded: boolean) => void>();
    render(
      <BottomSheet label="Estaciones" onExpandedChange={onExpandedChange}>
        <p>Contenido del panel</p>
      </BottomSheet>,
    );
    const handle = screen.getByRole('button', { name: 'Estaciones' });

    await user.click(handle);
    expect(handle).toHaveAttribute('aria-expanded', 'true');
    expect(onExpandedChange).toHaveBeenLastCalledWith(true);

    await user.click(handle);
    expect(handle).toHaveAttribute('aria-expanded', 'false');
    expect(onExpandedChange).toHaveBeenLastCalledWith(false);
  });
});
