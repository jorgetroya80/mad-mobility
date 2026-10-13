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
  it('starts collapsed with its content rendered but hidden', () => {
    const { handle, content } = renderSheet();

    expect(handle).toHaveAttribute('type', 'button');
    expect(handle).toHaveAttribute('aria-expanded', 'false');
    expect(content).toHaveAttribute('hidden');
  });

  it('points the handle at the content region', () => {
    const { handle, content } = renderSheet({ defaultExpanded: true });

    expect(content).toHaveAttribute('id', handle.getAttribute('aria-controls'));
    expect(screen.getByRole('region', { name: 'Estaciones' })).toBe(content);
  });

  it('starts expanded when asked to', () => {
    const { handle, content } = renderSheet({ defaultExpanded: true });

    expect(handle).toHaveAttribute('aria-expanded', 'true');
    expect(content).not.toHaveAttribute('hidden');
  });

  it('expands and collapses on click', async () => {
    const user = userEvent.setup();
    const { handle, content } = renderSheet();

    await user.click(handle);
    expect(handle).toHaveAttribute('aria-expanded', 'true');
    expect(content).not.toHaveAttribute('hidden');

    await user.click(handle);
    expect(handle).toHaveAttribute('aria-expanded', 'false');
    expect(content).toHaveAttribute('hidden');
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
    expect(content).not.toHaveAttribute('hidden');

    await user.keyboard(keys);
    expect(handle).toHaveAttribute('aria-expanded', 'false');
    expect(content).toHaveAttribute('hidden');
  });
});
