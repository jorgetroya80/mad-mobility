import { render, screen } from '@testing-library/react';
import { DataAttribution } from './DataAttribution';

describe('DataAttribution', () => {
  it('credits EMT as the MobilityLabs terms require, with a safe link to EMT', () => {
    render(<DataAttribution />);

    expect(screen.getByText(/Powered by/)).toHaveTextContent('Powered by EMT de Madrid');
    const link = screen.getByRole('link', { name: 'EMT de Madrid' });
    expect(link).toHaveAttribute('href', 'https://www.emtmadrid.es');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });
});
