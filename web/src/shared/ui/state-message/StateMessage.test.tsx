import { render, screen } from '@testing-library/react';
import { StateMessage } from './StateMessage';

describe('StateMessage', () => {
  it('announces an empty state as a status', () => {
    render(
      <StateMessage variant="empty" title="Sin estaciones">
        Las estaciones llegan pronto
      </StateMessage>,
    );

    const message = screen.getByRole('status');
    expect(message).toHaveTextContent('Sin estaciones');
    expect(message).toHaveTextContent('Las estaciones llegan pronto');
    expect(message).not.toHaveAttribute('aria-busy');
  });

  it('announces an error as an alert', () => {
    render(<StateMessage variant="error" title="No se pudieron cargar los datos" />);

    expect(screen.getByRole('alert')).toHaveTextContent('No se pudieron cargar los datos');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('announces loading as a busy status', () => {
    render(<StateMessage variant="loading" title="Cargando estaciones" />);

    const message = screen.getByRole('status');
    expect(message).toHaveTextContent('Cargando estaciones');
    expect(message).toHaveAttribute('aria-busy', 'true');
  });
});
