import type { ComponentProps } from 'react';
import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { stubMatchMedia } from '../../../../test/matchMedia';
import { AppLayout } from './AppLayout';

function renderLayout(
  props: Pick<ComponentProps<typeof AppLayout>, 'panelExpanded' | 'onPanelExpandedChange'> = {},
) {
  return render(
    <AppLayout
      title="BiciMAD Now"
      map={<div data-testid="map" />}
      panelLabel="Estaciones"
      panel={<p>Contenido del panel</p>}
      {...props}
    />,
  );
}

describe('AppLayout', () => {
  it('names the page inside the main landmark', () => {
    renderLayout();

    expect(
      within(screen.getByRole('main')).getByRole('heading', { level: 1, name: 'BiciMAD Now' }),
    ).toBeInTheDocument();
  });

  it('renders the map inside the main landmark', () => {
    renderLayout();

    expect(within(screen.getByRole('main')).getByTestId('map')).toBeInTheDocument();
  });

  it('renders the panel once, inside a bottom sheet in the main landmark below md', () => {
    renderLayout();

    const sheet = within(screen.getByRole('main')).getByRole('region', { name: 'Estaciones' });
    expect(within(sheet).getByText('Contenido del panel')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Estaciones' })).toBeInTheDocument();
    expect(screen.getAllByText('Contenido del panel')).toHaveLength(1);
  });

  it('renders the panel once, inside a side panel in the main landmark from md up', () => {
    stubMatchMedia(true);
    renderLayout();

    const sidePanel = within(screen.getByRole('main')).getByRole('region', {
      name: 'Estaciones',
    });
    expect(within(sidePanel).getByText('Contenido del panel')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Estaciones' })).not.toBeInTheDocument();
    expect(screen.getAllByText('Contenido del panel')).toHaveLength(1);
  });

  it('switches the panel container when the viewport crosses md', () => {
    const media = stubMatchMedia(false);
    renderLayout();

    act(() => media.change(true));

    expect(screen.queryByRole('button', { name: 'Estaciones' })).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Estaciones' })).toHaveTextContent(
      'Contenido del panel',
    );
  });

  it('fills the dynamic viewport height', () => {
    renderLayout();

    // Column below md, so the bottom sheet strip sits under the map instead of over it.
    expect(screen.getByRole('main')).toHaveClass('flex', 'h-dvh', 'flex-col', 'md:flex-row');
  });

  it('lets the page control the bottom sheet below md', async () => {
    const user = userEvent.setup();
    const onPanelExpandedChange = vi.fn<(expanded: boolean) => void>();
    renderLayout({ panelExpanded: true, onPanelExpandedChange });
    const handle = screen.getByRole('button', { name: 'Estaciones' });

    expect(handle).toHaveAttribute('aria-expanded', 'true');

    await user.click(handle);
    expect(onPanelExpandedChange).toHaveBeenCalledExactlyOnceWith(false);
    expect(handle).toHaveAttribute('aria-expanded', 'true');
  });

  it('ignores the expanded state from md up', () => {
    stubMatchMedia(true);
    renderLayout({
      panelExpanded: false,
      onPanelExpandedChange: vi.fn<(expanded: boolean) => void>(),
    });

    const sidePanel = screen.getByRole('region', { name: 'Estaciones' });
    expect(sidePanel).not.toHaveAttribute('inert');
    expect(within(sidePanel).getByText('Contenido del panel')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Estaciones' })).not.toBeInTheDocument();
  });
});
