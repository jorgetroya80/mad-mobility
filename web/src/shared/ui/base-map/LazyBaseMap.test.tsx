import { render, screen } from '@testing-library/react';
import type { BaseMapProps } from './BaseMap';
import { LazyBaseMap } from './LazyBaseMap';

const baseMap = vi.hoisted(() => ({ props: [] as unknown[] }));

vi.mock('./BaseMap', () => ({
  BaseMap: (props: BaseMapProps) => {
    baseMap.props.push(props);
    return <section aria-label="Mapa de Madrid" className={props.className} />;
  },
}));

describe('LazyBaseMap', () => {
  beforeEach(() => {
    baseMap.props.length = 0;
  });

  it('shows a placeholder with the same size while the map loads, then the map', async () => {
    const { container } = render(<LazyBaseMap className="h-full" />);

    const placeholder = container.firstElementChild;
    expect(placeholder).toHaveAttribute('aria-hidden', 'true');
    expect(placeholder).toHaveClass('h-full');

    const map = await screen.findByRole('region', { name: 'Mapa de Madrid' });
    expect(map).toHaveClass('h-full');
    expect(container.querySelector('[aria-hidden="true"]')).not.toBeInTheDocument();
  });

  it('forwards every prop to the map', async () => {
    const props: BaseMapProps = {
      className: 'h-full',
      points: [{ id: '1a', lng: -3.7, lat: 40.4, label: '7', tone: 'good' }],
      selectedId: '1a',
      onSelectPoint: () => {},
      focus: { lng: -3.7, lat: 40.4 },
      userLocation: { lng: -3.71, lat: 40.41 },
    };
    render(<LazyBaseMap {...props} />);

    await screen.findByRole('region', { name: 'Mapa de Madrid' });
    expect(baseMap.props.at(-1)).toEqual(props);
  });
});
