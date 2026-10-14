import { useCanGoBack, useRouter } from '@tanstack/react-router';
import { useSelectedStation } from '@/features/select-station';

// The router numbers the entries it pushes; the first one is where the user landed. Past it,
// the previous entry belongs to the app, so going back returns there (the list, usually).
// On the landing entry (opened from a link) there is nothing of ours behind: close instead.
export function useGoBack(): () => void {
  const router = useRouter();
  const canGoBack = useCanGoBack();
  const { clear } = useSelectedStation();

  return canGoBack ? () => router.history.back() : clear;
}
