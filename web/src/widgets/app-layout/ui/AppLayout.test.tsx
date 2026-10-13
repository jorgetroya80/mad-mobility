import { render, screen, within } from '@testing-library/react';
import { AppLayout } from './AppLayout';

// jsdom loads no CSS: both panel containers are in the tree; the browser shows one per breakpoint.
function renderLayout() {
  return render(
    <AppLayout
      map={<div data-testid="map" />}
      panelLabel="Estaciones"
      panel={<p>Contenido del panel</p>}
    />,
  );
}

describe('AppLayout', () => {
  it('renders the map inside the main landmark', () => {
    renderLayout();

    expect(within(screen.getByRole('main')).getByTestId('map')).toBeInTheDocument();
  });

  it('renders the panel inside a named side panel shown from md up', () => {
    renderLayout();

    const sidePanel = screen.getByRole('complementary', { name: 'Estaciones' });
    expect(within(sidePanel).getByText('Contenido del panel')).toBeInTheDocument();
    expect(sidePanel).toHaveClass('hidden', 'md:block');
  });

  it('renders the panel inside a named bottom sheet hidden from md up', () => {
    renderLayout();

    const sheet = screen.getByRole('region', { name: 'Estaciones' });
    expect(within(sheet).getByText('Contenido del panel')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Estaciones' }).closest('.md\\:hidden'),
    ).not.toBeNull();
  });

  it('fills the dynamic viewport height', () => {
    const { container } = renderLayout();

    // Column below md, so the bottom sheet strip sits under the map instead of over it.
    expect(container.firstElementChild).toHaveClass('flex', 'h-dvh', 'flex-col', 'md:flex-row');
  });
});
