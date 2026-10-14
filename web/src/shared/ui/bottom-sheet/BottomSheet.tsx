import { useId, useState, type CSSProperties, type ReactNode } from 'react';
import { EXPANDED_SHEET_VIEWPORT_PERCENT, HANDLE_HEIGHT_PX } from './sheetSize';

type BottomSheetProps = {
  label: string;
  defaultExpanded?: boolean;
  // Passing `expanded` makes the parent own the state; the handle only asks for the change.
  expanded?: boolean;
  onExpandedChange?: (expanded: boolean) => void;
  children: ReactNode;
};

// Inline styles, not Tailwind classes: classes cannot be built from the shared size constants.
// CSSProperties does not type custom properties.
const stripStyle = { '--handle-height': `${HANDLE_HEIGHT_PX}px` } as CSSProperties;
const sheetStyle: CSSProperties = { height: `${EXPANDED_SHEET_VIEWPORT_PERCENT}dvh` };

// Two states only: collapsed shows the handle, expanded takes 60 % of the viewport height.
// dvh, not vh: 100vh ignores the Safari iOS toolbar and would hide the bottom of the sheet.
// The sheet keeps its height and slides with a transform, so the change can be animated.
// The root is an in-flow strip as tall as the handle, so the collapsed sheet covers nothing
// above it (e.g. the map attribution). --handle-height carries that height to every class.
export function BottomSheet({
  label,
  defaultExpanded = false,
  expanded: controlledExpanded,
  onExpandedChange,
  children,
}: BottomSheetProps) {
  const [uncontrolledExpanded, setUncontrolledExpanded] = useState(defaultExpanded);
  const expanded = controlledExpanded ?? uncontrolledExpanded;
  const handleId = useId();
  const contentId = useId();

  function toggle() {
    setUncontrolledExpanded(!expanded);
    onExpandedChange?.(!expanded);
  }

  return (
    <div className="h-(--handle-height) shrink-0" style={stripStyle}>
      <div
        style={sheetStyle}
        className={`fixed inset-x-0 bottom-0 z-10 flex flex-col rounded-t-2xl bg-white shadow-[0_-4px_16px_rgb(0_0_0/0.15)] transition-transform duration-300 ease-out motion-reduce:transition-none ${expanded ? 'translate-y-0' : 'translate-y-[calc(100%-var(--handle-height))]'}`}
      >
        <button
          id={handleId}
          type="button"
          aria-expanded={expanded}
          aria-controls={contentId}
          onClick={toggle}
          className="flex h-(--handle-height) w-full shrink-0 flex-col items-center justify-center gap-1 px-4 font-medium text-slate-800 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-sky-700"
        >
          <span aria-hidden="true" className="h-1 w-10 rounded-full bg-slate-300" />
          {label}
        </button>
        {/* inert, not hidden: collapsed content stays painted while it slides out, but is
          unreachable for focus and assistive technology. */}
        <section
          id={contentId}
          aria-labelledby={handleId}
          inert={!expanded}
          className="min-h-0 flex-1 overflow-y-auto px-4 pb-4"
        >
          {children}
        </section>
      </div>
    </div>
  );
}
