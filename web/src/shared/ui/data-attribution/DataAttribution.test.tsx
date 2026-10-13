import { render, screen } from '@testing-library/react';
import { DataAttribution } from './DataAttribution';

describe('DataAttribution', () => {
  it('credits EMT Madrid MobilityLabs with a safe link to EMT', () => {
    render(<DataAttribution />);

    expect(screen.getByText(/Datos:/)).toHaveTextContent('Datos: EMT Madrid MobilityLabs');
    const link = screen.getByRole('link', { name: 'EMT Madrid MobilityLabs' });
    expect(link).toHaveAttribute('href', 'https://www.emtmadrid.es');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });
});
