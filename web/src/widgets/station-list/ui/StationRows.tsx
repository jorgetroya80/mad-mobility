import { type Need, type Station, StationRow } from '@/entities/station';

type StationRowsProps = {
  stations: readonly Station[];
  need: Need;
  onSelect: (number: string) => void;
};

// `data-station-number` lets the page return the focus to the row a detail was opened from.
export function StationRows({ stations, need, onSelect }: StationRowsProps) {
  return (
    <ul className="divide-y divide-slate-200">
      {stations.map((station) => (
        <li key={station.number}>
          <StationRow
            station={station}
            need={need}
            data-station-number={station.number}
            onClick={() => onSelect(station.number)}
          />
        </li>
      ))}
    </ul>
  );
}
