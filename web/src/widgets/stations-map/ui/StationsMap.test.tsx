import { act, screen, waitFor } from '@testing-library/react';
import type { Coordinates } from '@/entities/station';
import { stationsFixture } from '@/shared/api/mocks/fixtures';
import type * as SharedUi from '@/shared/ui';
import type { MapPoint } from '@/shared/ui';
import { renderWithRouter } from '../../../../test/renderWithRouter';
import { StationsMap } from './StationsMap';

type CapturedMapProps = {
  points?: readonly MapPoint[];
  selectedId?: string;
  onSelectPoint?: (id: string) => void;
  focus?: { lng: number; lat: number };
  userLocation?: { lng: number; lat: number };
  focusPadding?: { bottom: number };
  className?: string;
};

const mapRenders = vi.hoisted(() => [] as CapturedMapProps[]);

// jsdom has no WebGL: the map only records the props it receives.
vi.mock('@/shared/ui', async (importOriginal) => ({
  ...(await importOriginal<typeof SharedUi>()),
  BaseMap: (props: CapturedMapProps) => {
    mapRenders.push(props);
    return <div data-testid="base-map" className={props.className} />;
  },
}));

const SOL: Coordinates = { lat: 40.41702, lng: -3.70243 };
const USER: Coordinates = { lat: 40.42, lng: -3.7 };
// Station 1a in the fixtures.
const PUERTA_DEL_SOL_A: Coordinates = { lat: 40.4170224, lng: -3.7024255 };
const STATION_COUNT = stationsFixture.stations.length;

// Taps a point the way MapLibre would, letting the navigation settle inside `act`.
async function tapPoint(id: string) {
  const { onSelectPoint } = lastMapProps();
  await act(async () => onSelectPoint?.(id));
}

function lastMapProps(): CapturedMapProps {
  const props = mapRenders.at(-1);
  if (!props) throw new Error('BaseMap was not rendered');
  return props;
}

async function waitForPoints() {
  await waitFor(() => expect(lastMapProps().points).toHaveLength(STATION_COUNT));
}

function renderMap(initialEntry = '/', { center }: { center?: Coordinates } = { center: SOL }) {
  return renderWithRouter(<StationsMap center={center} userLocation={USER} />, {
    initialEntries: [initialEntry],
  });
}

beforeEach(() => {
  mapRenders.length = 0;
});

describe('StationsMap', () => {
  it('renders the map before the stations arrive', async () => {
    await renderMap();

    expect(screen.getByTestId('base-map')).toBeInTheDocument();
    expect(mapRenders[0]?.points).toEqual([]);
  });

  it('shows every station with its bikes by default', async () => {
    await renderMap();
    await waitForPoints();

    expect(lastMapProps().points?.map(({ id, label, tone }) => ({ id, label, tone }))).toEqual([
      { id: '1a', label: '12', tone: 'good' },
      { id: '1b', label: '2', tone: 'low' },
      { id: '2', label: '–', tone: 'off' },
      { id: '3', label: '0', tone: 'empty' },
    ]);
  });

  it('shows the free docks with ?need=docks', async () => {
    await renderMap('/?need=docks');
    await waitForPoints();

    expect(lastMapProps().points?.map(({ id, label, tone }) => ({ id, label, tone }))).toEqual([
      { id: '1a', label: '12', tone: 'good' },
      { id: '1b', label: '22', tone: 'good' },
      { id: '2', label: '–', tone: 'off' },
      { id: '3', label: '18', tone: 'good' },
    ]);
  });

  it('selects and centers on the station in ?station, whatever its case', async () => {
    await renderMap('/?station=1A');
    await waitForPoints();

    expect(lastMapProps()).toMatchObject({
      selectedId: '1a',
      focus: PUERTA_DEL_SOL_A,
    });
  });

  it('centers on the given center without a selected station', async () => {
    await renderMap();
    await waitForPoints();

    expect(lastMapProps()).toMatchObject({ selectedId: undefined, focus: SOL });
  });

  it('centers on the given center while ?station is not among the stations', async () => {
    await renderMap('/?station=999');
    await waitForPoints();

    expect(lastMapProps()).toMatchObject({ selectedId: undefined, focus: SOL });
  });

  it('centers on the given center, keeping the selection, when the selected station is not the focus', async () => {
    await renderWithRouter(<StationsMap center={SOL} userLocation={USER} focusSelected={false} />, {
      initialEntries: ['/?station=1a'],
    });
    await waitForPoints();

    expect(lastMapProps()).toMatchObject({ selectedId: '1a', focus: SOL });
  });

  it('does not move the map before there is a center', async () => {
    await renderMap('/', {});
    await waitForPoints();

    expect(lastMapProps().focus).toBeUndefined();
  });

  it('passes the user location through', async () => {
    await renderMap();

    expect(lastMapProps().userLocation).toEqual(USER);
  });

  it('passes the focus padding through', async () => {
    await renderWithRouter(
      <StationsMap center={SOL} userLocation={USER} focusPadding={{ bottom: 400 }} />,
    );

    expect(lastMapProps().focusPadding).toEqual({ bottom: 400 });
  });

  it('selects the station of the tapped point', async () => {
    const { router } = await renderMap('/?need=docks');
    await waitForPoints();

    await tapPoint('2');

    await waitFor(() =>
      expect(router.state.location.search).toEqual({ need: 'docks', station: '2' }),
    );
  });

  it('keeps the same points when only the selection changes', async () => {
    await renderMap();
    await waitForPoints();
    const { points } = lastMapProps();

    await tapPoint('1b');
    await waitFor(() => expect(lastMapProps().selectedId).toBe('1b'));

    expect(lastMapProps().points).toBe(points);
  });
});
