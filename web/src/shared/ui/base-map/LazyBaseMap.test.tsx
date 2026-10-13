import { render, screen } from '@testing-library/react';
import { LazyBaseMap } from './LazyBaseMap';

vi.mock('./BaseMap', () => ({
  BaseMap: ({ className }: { className?: string }) => (
    <section aria-label="Mapa de Madrid" className={className} />
  ),
}));

describe('LazyBaseMap', () => {
  it('shows a placeholder with the same size while the map loads, then the map', async () => {
    const { container } = render(<LazyBaseMap className="h-full" />);

    const placeholder = container.firstElementChild;
    expect(placeholder).toHaveAttribute('aria-hidden', 'true');
    expect(placeholder).toHaveClass('h-full');

    const map = await screen.findByRole('region', { name: 'Mapa de Madrid' });
    expect(map).toHaveClass('h-full');
    expect(container.querySelector('[aria-hidden="true"]')).not.toBeInTheDocument();
  });
});
