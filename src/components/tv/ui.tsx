import type { ButtonHTMLAttributes, ReactNode } from "react";
import { clsx } from "clsx";
import {
  ArrowLeft,
  Bookmark,
  History,
  House,
  Monitor,
  Phone,
  RotateCw,
  Settings,
  Sparkles,
  Tv,
} from "lucide-react";
import type { DeviceMode, MobileOrientation } from "@/lib/tv/store";

type TvButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  primary?: boolean;
  variant?: "primary" | "secondary" | "surface" | "ghost" | "danger" | "glass";
  glow?: boolean;
};

export function TvButton({
  primary,
  variant = "surface",
  glow = false,
  className,
  type = "button",
  ...props
}: TvButtonProps) {
  const variantStyles = {
    primary:
      "bg-gradient-to-r from-amber to-blue-500 text-amber-ink font-semibold shadow-lg shadow-amber/20 hover:brightness-110",
    secondary:
      "bg-surface-2 text-fg hover:bg-surface-hover border border-line/60",
    surface:
      "bg-surface text-fg hover:bg-surface-hover border border-line/40",
    ghost:
      "bg-transparent text-muted hover:text-fg hover:bg-surface/50",
    danger:
      "bg-danger/15 text-danger border border-danger/30 hover:bg-danger/25",
    glass:
      "glass-panel text-fg hover:bg-surface-2/80",
  };

  return (
    <button
      type={type}
      data-tv=""
      {...(primary ? { "data-tv-primary": "" } : {})}
      className={clsx(
        "tv select-none active:scale-[0.98]",
        variantStyles[variant],
        glow && "glow-amber",
        className,
      )}
      {...props}
    />
  );
}

export function BackButton({
  onClick,
  primary = false,
  label = "Back",
}: {
  onClick: () => void;
  primary?: boolean;
  label?: string;
}) {
  return (
    <TvButton
      primary={primary}
      aria-label={label}
      onClick={onClick}
      className="grid size-11 shrink-0 place-items-center rounded-2xl bg-surface/80 text-fg hover:bg-surface-2 transition border border-line/50 hover:border-amber/50"
    >
      <ArrowLeft className="size-5 transition group-hover:-translate-x-0.5" />
    </TvButton>
  );
}

export function Screen({
  children,
  header,
  footer,
}: {
  children: ReactNode;
  header?: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="flex h-full flex-col bg-bg text-fg overflow-hidden relative">
      {header ? <div className="shrink-0 border-b border-line/40 z-20">{header}</div> : null}
      <main className="min-h-0 flex-1 overflow-y-auto relative">{children}</main>
      {footer ? <div className="shrink-0 border-t border-line/40 z-20">{footer}</div> : null}
    </div>
  );
}

export function SectionLabel({
  children,
  badge,
  icon,
}: {
  children: ReactNode;
  badge?: string | number;
  icon?: ReactNode;
}) {
  return (
    <div className="flex items-center gap-2.5">
      {icon ? <span className="text-amber">{icon}</span> : <div className="size-2 rounded-full bg-amber shadow-[0_0_8px_#3ecbff]" />}
      <h2 className="text-xs font-bold tracking-widest text-muted uppercase">{children}</h2>
      {badge !== undefined ? (
        <span className="rounded-full bg-surface-2 px-2 py-0.5 text-[10px] font-semibold text-amber border border-line/60">
          {badge}
        </span>
      ) : null}
    </div>
  );
}

/**
 * Top Global Controls Bar: Device mode switcher (TV, Mobile, Auto), Orientation, Virtual Remote
 */
export function DeviceSwitcherBar({
  mode,
  orientation,
  virtualRemote,
  onModeChange,
  onOrientationToggle,
  onVirtualRemoteToggle,
}: {
  mode: DeviceMode;
  orientation: MobileOrientation;
  virtualRemote: boolean;
  onModeChange: (mode: DeviceMode) => void;
  onOrientationToggle: () => void;
  onVirtualRemoteToggle: () => void;
}) {
  return (
    <div className="w-full flex items-center justify-between px-4 py-2 border-b border-line/30 bg-surface/40 backdrop-blur-md text-xs z-30">
      {/* Brand & Live status */}
      <div className="flex items-center gap-2.5">
        <div className="flex items-center gap-1.5 font-bold tracking-wide text-fg">
          <span className="bg-gradient-to-r from-amber to-magenta bg-clip-text text-transparent">XTREAM</span>
          <span className="text-[10px] text-muted font-mono px-1.5 py-0.5 rounded bg-surface border border-line/40">v2.4</span>
        </div>
        <div className="hidden sm:flex items-center gap-1 text-[11px] text-emerald-400 bg-emerald-950/40 border border-emerald-800/40 px-2 py-0.5 rounded-full">
          <span className="size-1.5 rounded-full bg-emerald-400 animate-pulse" />
          <span>Living Room Ready</span>
        </div>
      </div>

      {/* Mode selectors */}
      <div className="flex items-center gap-1.5 bg-surface-2/80 p-1 rounded-xl border border-line/60">
        <button
          type="button"
          onClick={() => onModeChange("tv")}
          className={clsx(
            "flex items-center gap-1.5 px-2.5 py-1 rounded-lg font-semibold transition text-[11px]",
            mode === "tv"
              ? "bg-amber text-amber-ink shadow-sm font-bold"
              : "text-muted hover:text-fg hover:bg-surface/50",
          )}
          title="TV Mode: 16:9 Living-Room Experience"
        >
          <Tv className="size-3.5" />
          <span>TV</span>
        </button>

        <button
          type="button"
          onClick={() => onModeChange("mobile")}
          className={clsx(
            "flex items-center gap-1.5 px-2.5 py-1 rounded-lg font-semibold transition text-[11px]",
            mode === "mobile"
              ? "bg-amber text-amber-ink shadow-sm font-bold"
              : "text-muted hover:text-fg hover:bg-surface/50",
          )}
          title="Mobile Mode: Smartphone Experience"
        >
          <Phone className="size-3.5" />
          <span>Mobile</span>
        </button>

        <button
          type="button"
          onClick={() => onModeChange("auto")}
          className={clsx(
            "flex items-center gap-1.5 px-2.5 py-1 rounded-lg font-semibold transition text-[11px]",
            mode === "auto"
              ? "bg-amber text-amber-ink shadow-sm font-bold"
              : "text-muted hover:text-fg hover:bg-surface/50",
          )}
          title="Responsive Fluid Fullscreen"
        >
          <Monitor className="size-3.5" />
          <span>Fluid</span>
        </button>

        {mode === "mobile" && (
          <button
            type="button"
            onClick={onOrientationToggle}
            className="flex items-center gap-1 px-2 py-1 rounded-lg text-[11px] font-semibold text-muted hover:text-fg hover:bg-surface/60 border-l border-line/40 ml-1 transition"
            title="Rotate phone orientation"
          >
            <RotateCw className="size-3 text-amber" />
            <span className="capitalize">{orientation}</span>
          </button>
        )}
      </div>

      {/* Virtual Remote Controller Toggle */}
      <button
        type="button"
        onClick={onVirtualRemoteToggle}
        className={clsx(
          "flex items-center gap-1.5 px-3 py-1.5 rounded-xl border text-[11px] font-semibold transition",
          virtualRemote
            ? "bg-amber/15 border-amber/50 text-amber shadow-[0_0_12px_rgba(62,203,255,0.25)]"
            : "bg-surface/80 border-line/60 text-muted hover:text-fg hover:border-amber/40",
        )}
      >
        <Sparkles className="size-3.5 text-amber" />
        <span className="hidden sm:inline">TV Remote</span>
        <span className="sm:hidden">Remote</span>
      </button>
    </div>
  );
}

/**
 * Mobile Bottom Navigation Bar (Dock)
 */
export function MobileBottomDock({
  activeTab,
  onNavigate,
  bookmarkCount = 0,
}: {
  activeTab: "home" | "bookmarks" | "history" | "settings";
  onNavigate: (tab: "home" | "bookmarks" | "history" | "settings") => void;
  bookmarkCount?: number;
}) {
  return (
    <nav
      aria-label="Mobile Navigation"
      className="shrink-0 flex items-center justify-around bg-surface/90 backdrop-blur-xl border-t border-line/60 py-2 px-3 z-30"
    >
      <button
        type="button"
        onClick={() => onNavigate("home")}
        className={clsx(
          "flex flex-col items-center gap-1 px-3 py-1 rounded-xl transition",
          activeTab === "home" ? "text-amber font-bold" : "text-muted hover:text-fg",
        )}
      >
        <House className="size-5" />
        <span className="text-[10px] tracking-tight">Home</span>
      </button>

      <button
        type="button"
        onClick={() => onNavigate("bookmarks")}
        className={clsx(
          "relative flex flex-col items-center gap-1 px-3 py-1 rounded-xl transition",
          activeTab === "bookmarks" ? "text-amber font-bold" : "text-muted hover:text-fg",
        )}
      >
        <Bookmark className="size-5" />
        {bookmarkCount > 0 && (
          <span className="absolute -top-0.5 right-2 size-4 rounded-full bg-amber text-amber-ink text-[9px] font-extrabold flex items-center justify-center">
            {bookmarkCount}
          </span>
        )}
        <span className="text-[10px] tracking-tight">Bookmarks</span>
      </button>

      <button
        type="button"
        onClick={() => onNavigate("history")}
        className={clsx(
          "flex flex-col items-center gap-1 px-3 py-1 rounded-xl transition",
          activeTab === "history" ? "text-amber font-bold" : "text-muted hover:text-fg",
        )}
      >
        <History className="size-5" />
        <span className="text-[10px] tracking-tight">History</span>
      </button>

      <button
        type="button"
        onClick={() => onNavigate("settings")}
        className={clsx(
          "flex flex-col items-center gap-1 px-3 py-1 rounded-xl transition",
          activeTab === "settings" ? "text-amber font-bold" : "text-muted hover:text-fg",
        )}
      >
        <Settings className="size-5" />
        <span className="text-[10px] tracking-tight">Settings</span>
      </button>
    </nav>
  );
}

export function ago(ts: number): string {
  const seconds = Math.max(0, Math.round((Date.now() - ts) / 1000));
  if (seconds < 45) return "Just now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  return `${days}d ago`;
}
