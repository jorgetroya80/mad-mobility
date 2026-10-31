import { useId } from 'react';
import type { Coordinates } from '@/entities/station';
import { useStationDetail } from '../model/useStationDetail';
import { BackButton } from './BackButton';
import { DetailBody } from './DetailBody';
import { DetailTitle } from './DetailTitle';

type SelectedStationDetailProps = { number: string; center: Coordinates | undefined };

export function SelectedStationDetail({ number, center }: SelectedStationDetailProps) {
  const detail = useStationDetail(number);
  const headingId = useId();

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-4">
      <BackButton />
      <DetailTitle
        id={headingId}
        number={number}
        name={detail.status === 'not-found' ? undefined : detail.station?.name}
      />
      <DetailBody detail={detail} center={center} />
    </section>
  );
}
