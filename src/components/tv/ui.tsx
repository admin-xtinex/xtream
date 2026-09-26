import type { ButtonHTMLAttributes, ReactNode } from "react";
import { clsx } from "clsx";
import { ArrowLeft } from "lucide-react";

type TvButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  primary?: boolean;
};

export function TvButton({ primary, className, type = "button", ...props }: TvButtonProps) {
  return (
    <button
      type={type}
      data-tv=""
      {...(primary ? { "data-tv-primary": "" } : {})}
      className={clsx("tv", className)}
      {...props}
    />
  );
}

export function BackButton({ onClick, primary = false }: { onClick: () => void; primary?: boolean }) {
  return (
    <TvButton
      primary={primary}
      aria-label="Back"
      onClick={onClick}
      className="grid size-12 shrink-0 place-items-center rounded-full bg-surface text-fg"
    >
      <ArrowLeft className="size-5" />
    </TvButton>
  );
}

export function Screen({
  children,
  footer,
}: {
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="flex h-full flex-col bg-bg text-fg">
      <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
      {footer ? <div className="shrink-0 border-t border-line px-5 py-3 md:px-10">{footer}</div> : null}
    </div>
  );
}

export function SectionLabel({ children }: { children: ReactNode }) {
  return (
    <h2 className="text-sm font-semibold tracking-widest text-amber uppercase">{children}</h2>
  );
}

export function ago(ts: number): string {
  const seconds = Math.max(0, Math.round((Date.now() - ts) / 1000));
  if (seconds < 45) return "Just now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  const days = Math.round(hours / 24);
  return `${days} d ago`;
}
