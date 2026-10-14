import type { ComponentProps } from 'react';

type MessageActionProps = Omit<ComponentProps<'button'>, 'type'>;

export function MessageAction({ className = '', ...props }: MessageActionProps) {
  return (
    <button
      type="button"
      {...props}
      className={`min-h-11 rounded-md px-3 font-medium underline focus-visible:outline-2 focus-visible:outline-sky-700 ${className}`}
    />
  );
}
