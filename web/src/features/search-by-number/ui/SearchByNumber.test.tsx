import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { SearchSource } from '../model/types';
import { SearchByNumber } from './SearchByNumber';

function bicimadSource(overrides: Partial<SearchSource> = {}): SearchSource {
  return {
    id: 'bicimad',
    label: 'BiciMAD',
    itemLabel: 'estación',
    itemLabelPlural: 'estaciones',
    items: [
      { number: '10', title: 'Tirso de Molina' },
      { number: '1a', title: 'Puerta del Sol A', subtitle: 'Puerta del Sol nº 1' },
      { number: '1b', title: 'Puerta del Sol B' },
      { number: '25A', title: 'Fuencarral' },
    ],
    status: 'ready',
    onSelect: vi.fn<(number: string) => void>(),
    ...overrides,
  };
}

function busSource(overrides: Partial<SearchSource> = {}): SearchSource {
  return {
    id: 'bus',
    label: 'Autobús',
    itemLabel: 'parada',
    itemLabelPlural: 'paradas',
    items: [
      { number: '1234', title: 'Gran Vía' },
      { number: '12', title: 'Atocha' },
    ],
    status: 'ready',
    onSelect: vi.fn<(number: string) => void>(),
    ...overrides,
  };
}

function searchBox() {
  return screen.getByRole('searchbox');
}

function resultButtons() {
  return within(screen.getByRole('list')).getAllByRole('button');
}

function resultNumbers() {
  return screen
    .queryAllByRole('listitem')
    .map((item) => item.querySelector('button')?.dataset.number);
}

function status() {
  return screen.getByRole('status');
}

describe('SearchByNumber', () => {
  describe('with one source', () => {
    it('shows no service selector and labels the box with the item label', () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);

      expect(screen.queryByRole('group')).not.toBeInTheDocument();
      expect(screen.queryByRole('radio')).not.toBeInTheDocument();
      expect(screen.getByRole('searchbox', { name: 'Buscar estación por número' })).toBeVisible();
      expect(searchBox()).toHaveAttribute('autocomplete', 'off');
      expect(searchBox()).toHaveAttribute('inputmode', 'text');
    });

    it('starts with no results and no message', () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);

      expect(screen.queryByRole('list')).not.toBeInTheDocument();
      expect(status()).toBeEmptyDOMElement();
    });

    it('lists matches by number as buttons named by number, title and subtitle', async () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);

      await userEvent.type(searchBox(), '1');

      expect(resultNumbers()).toEqual(['1a', '1b', '10']);
      expect(
        screen.getByRole('button', { name: '1a, Puerta del Sol A, Puerta del Sol nº 1' }),
      ).toBeVisible();
      expect(screen.getByRole('button', { name: '10, Tirso de Molina' })).toBeVisible();
    });

    it('announces how many results there are', async () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);

      await userEvent.type(searchBox(), '1');
      expect(status()).toHaveTextContent('3 estaciones');

      await userEvent.type(searchBox(), 'a');
      expect(status()).toHaveTextContent('1 estación');
    });

    it('does not search by name', async () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);

      await userEvent.type(searchBox(), 'sol');

      expect(screen.queryByRole('list')).not.toBeInTheDocument();
      expect(status()).toHaveTextContent('Ninguna estación con el número sol');
    });
  });

  describe('keyboard', () => {
    it('moves the focus with the arrows, from the box through the results and back', async () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);
      await userEvent.type(searchBox(), '1');
      const [first, second, third] = resultButtons();

      await userEvent.keyboard('{ArrowDown}');
      expect(first).toHaveFocus();

      await userEvent.keyboard('{ArrowDown}{ArrowDown}');
      expect(third).toHaveFocus();

      await userEvent.keyboard('{ArrowDown}');
      expect(third).toHaveFocus();

      await userEvent.keyboard('{ArrowUp}');
      expect(second).toHaveFocus();

      await userEvent.keyboard('{ArrowUp}{ArrowUp}');
      expect(searchBox()).toHaveFocus();
      expect(searchBox()).toHaveValue('1');
    });

    it('ignores ArrowDown in the box without results', async () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);
      await userEvent.type(searchBox(), 'sol');

      await userEvent.keyboard('{ArrowDown}');

      expect(searchBox()).toHaveFocus();
    });

    it('goes back to the box with Escape, keeping the text', async () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);
      await userEvent.type(searchBox(), '1');
      await userEvent.keyboard('{ArrowDown}{ArrowDown}');

      await userEvent.keyboard('{Escape}');

      expect(searchBox()).toHaveFocus();
      expect(searchBox()).toHaveValue('1');
      expect(resultNumbers()).toEqual(['1a', '1b', '10']);
    });

    it('chooses the focused result with Enter and clears the box', async () => {
      const source = bicimadSource();
      render(<SearchByNumber sources={[source]} />);
      await userEvent.type(searchBox(), '1');

      await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}');

      expect(source.onSelect).toHaveBeenCalledExactlyOnceWith('1b');
      expect(searchBox()).toHaveValue('');
      expect(screen.queryByRole('list')).not.toBeInTheDocument();
    });

    it('chooses the exact match with Enter in the box', async () => {
      const source = bicimadSource();
      render(<SearchByNumber sources={[source]} />);

      await userEvent.type(searchBox(), ' 25a {Enter}');

      expect(source.onSelect).toHaveBeenCalledExactlyOnceWith('25A');
      expect(searchBox()).toHaveValue('');
    });

    it('ignores Enter in the box without an exact match', async () => {
      const source = bicimadSource();
      render(<SearchByNumber sources={[source]} />);

      await userEvent.type(searchBox(), '1{Enter}');

      expect(source.onSelect).not.toHaveBeenCalled();
      expect(searchBox()).toHaveValue('1');
    });

    it('ignores other keys on a result', async () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);
      await userEvent.type(searchBox(), '1{ArrowDown}');

      await userEvent.keyboard('{ArrowLeft}');

      expect(resultButtons()[0]).toHaveFocus();
    });
  });

  describe('pointer', () => {
    it('chooses a result on click and clears the box', async () => {
      const source = bicimadSource();
      render(<SearchByNumber sources={[source]} />);
      await userEvent.type(searchBox(), '1');

      await userEvent.click(screen.getByRole('button', { name: /Puerta del Sol B/ }));

      expect(source.onSelect).toHaveBeenCalledExactlyOnceWith('1b');
      expect(searchBox()).toHaveValue('');
    });

    // The source decides where the focus goes (the page opens the detail and focuses its title);
    // pulling it back to the box would open the on-screen keyboard on mobile.
    it.each([
      {
        how: 'click',
        choose: () => userEvent.click(screen.getByRole('button', { name: /Puerta del Sol B/ })),
      },
      {
        how: 'Enter on a result',
        choose: () => userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}'),
      },
      { how: 'Enter on an exact match', choose: () => userEvent.keyboard('b{Enter}') },
    ])('leaves the focus where the source put it ($how)', async ({ choose }) => {
      const source = bicimadSource({
        onSelect: vi.fn<SearchSource['onSelect']>(() =>
          screen.getByRole('button', { name: 'Detalle' }).focus(),
        ),
      });
      render(
        <>
          <SearchByNumber sources={[source]} />
          <button type="button">Detalle</button>
        </>,
      );
      await userEvent.type(searchBox(), '1');

      await choose();

      expect(screen.getByRole('button', { name: 'Detalle' })).toHaveFocus();
    });
  });

  describe('states', () => {
    it('announces loading with the plural item label', async () => {
      render(<SearchByNumber sources={[bicimadSource({ items: [], status: 'loading' })]} />);

      await userEvent.type(searchBox(), '1');

      expect(status()).toHaveTextContent('Cargando estaciones…');
      expect(screen.queryByRole('list')).not.toBeInTheDocument();
    });

    it('announces an error with the plural item label', async () => {
      render(<SearchByNumber sources={[bicimadSource({ items: [], status: 'error' })]} />);

      await userEvent.type(searchBox(), '1');

      expect(status()).toHaveTextContent('No se pudieron cargar las estaciones');
    });

    it('announces no results with the query as typed, trimmed', async () => {
      render(<SearchByNumber sources={[bicimadSource()]} />);

      await userEvent.type(searchBox(), ' 99Z ');

      expect(status()).toHaveTextContent('Ninguna estación con el número 99Z');
    });

    it('shows no message for a blank query, even while loading', async () => {
      render(<SearchByNumber sources={[bicimadSource({ status: 'loading' })]} />);

      await userEvent.type(searchBox(), '   ');

      expect(status()).toBeEmptyDOMElement();
    });
  });

  describe('with two sources', () => {
    it('shows the service selector with the first source chosen', () => {
      render(<SearchByNumber sources={[bicimadSource(), busSource()]} />);

      expect(screen.getByRole('group', { name: 'Servicio' })).toBeVisible();
      expect(screen.getByRole('radio', { name: 'BiciMAD' })).toBeChecked();
      expect(screen.getByRole('radio', { name: 'Autobús' })).not.toBeChecked();
      expect(searchBox()).toHaveAccessibleName('Buscar estación por número');
    });

    it('searches only in the chosen source and keeps the text when switching', async () => {
      const bicimad = bicimadSource();
      const bus = busSource();
      render(<SearchByNumber sources={[bicimad, bus]} />);
      await userEvent.type(searchBox(), '1');
      expect(resultNumbers()).toEqual(['1a', '1b', '10']);

      await userEvent.click(screen.getByRole('radio', { name: 'Autobús' }));

      expect(searchBox()).toHaveAccessibleName('Buscar parada por número');
      expect(searchBox()).toHaveValue('1');
      expect(resultNumbers()).toEqual(['12', '1234']);
      expect(status()).toHaveTextContent('2 paradas');

      await userEvent.click(screen.getByRole('button', { name: '12, Atocha' }));

      expect(bus.onSelect).toHaveBeenCalledExactlyOnceWith('12');
      expect(bicimad.onSelect).not.toHaveBeenCalled();
    });

    it('uses the chosen source labels in the messages', async () => {
      render(<SearchByNumber sources={[bicimadSource(), busSource({ status: 'error' })]} />);

      await userEvent.click(screen.getByRole('radio', { name: 'Autobús' }));
      await userEvent.type(searchBox(), '1');

      expect(status()).toHaveTextContent('No se pudieron cargar las paradas');
    });
  });
});
