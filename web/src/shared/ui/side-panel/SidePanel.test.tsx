import { render, screen } from '@testing-library/react';
import { SidePanel } from './SidePanel';

describe('SidePanel', () => {
  it('renders its children inside a named complementary landmark', () => {
    render(
      <SidePanel label="Estaciones">
        <p>Contenido del panel</p>
      </SidePanel>,
    );

    const panel = screen.getByRole('complementary', { name: 'Estaciones' });
    expect(panel.tagName).toBe('ASIDE');
    expect(panel).toHaveTextContent('Contenido del panel');
  });

  it('forwards native aside props and merges the class name', () => {
    render(
      <SidePanel label="Estaciones" id="panel" className="extra">
        contenido
      </SidePanel>,
    );

    const panel = screen.getByRole('complementary', { name: 'Estaciones' });
    expect(panel).toHaveAttribute('id', 'panel');
    expect(panel).toHaveClass('extra', 'overflow-y-auto');
  });
});
