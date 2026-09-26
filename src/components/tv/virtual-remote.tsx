import { useState } from "react";
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  CornerDownLeft,
  House,
  Minus,
  Play,
  RotateCcw,
  Tv,
  X,
} from "lucide-react";

export function VirtualRemote({
  onClose,
}: {
  onClose: () => void;
}) {
  const [minimized, setMinimized] = useState(false);

  function dispatchKey(key: string, code?: string) {
    const eventDown = new KeyboardEvent("keydown", {
      key,
      code: code || key,
      bubbles: true,
      cancelable: true,
    });
    window.dispatchEvent(eventDown);

    // If Enter or space, also fire click on active element
    if (key === "Enter" || key === " ") {
      const active = document.activeElement;
      if (active instanceof HTMLElement) {
        active.click();
      }
    }
  }

  if (minimized) {
    return (
      <button
        type="button"
        onClick={() => setMinimized(false)}
        className="fixed bottom-5 right-5 z-[100] flex items-center gap-2 rounded-full border border-amber/40 bg-surface/90 px-4 py-2.5 text-xs font-semibold text-fg shadow-2xl backdrop-blur-xl transition hover:scale-105 hover:bg-surface-2"
        title="Open Virtual Remote"
      >
        <Tv className="size-4 text-amber" />
        <span>Remote</span>
      </button>
    );
  }

  return (
    <aside
      aria-label="Virtual TV Remote"
      className="virtual-remote-widget animate-in fade-in zoom-in-95 duration-200"
    >
      {/* Top Header */}
      <div className="flex w-full items-center justify-between pb-1 border-b border-line/50">
        <div className="flex items-center gap-2">
          <div className="size-2 rounded-full bg-amber animate-pulse" />
          <span className="text-xs font-bold tracking-wider text-fg uppercase">TV Remote</span>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setMinimized(true)}
            className="rounded p-1 text-muted hover:bg-surface-2 hover:text-fg"
            aria-label="Minimize remote"
          >
            <Minus className="size-3.5" />
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1 text-muted hover:bg-surface-2 hover:text-fg"
            aria-label="Close remote"
          >
            <X className="size-3.5" />
          </button>
        </div>
      </div>

      {/* D-Pad Circular Controller */}
      <div className="relative size-36 rounded-full bg-surface-2 border border-line/80 shadow-inner flex items-center justify-center">
        {/* UP */}
        <button
          type="button"
          onClick={() => dispatchKey("ArrowUp")}
          className="absolute top-1 left-1/2 -translate-x-1/2 flex size-10 items-center justify-center rounded-full text-muted hover:bg-surface hover:text-amber active:scale-90 transition"
          aria-label="Up"
        >
          <ArrowUp className="size-5" />
        </button>

        {/* LEFT */}
        <button
          type="button"
          onClick={() => dispatchKey("ArrowLeft")}
          className="absolute left-1 top-1/2 -translate-y-1/2 flex size-10 items-center justify-center rounded-full text-muted hover:bg-surface hover:text-amber active:scale-90 transition"
          aria-label="Left"
        >
          <ArrowLeft className="size-5" />
        </button>

        {/* CENTER / OK */}
        <button
          type="button"
          onClick={() => dispatchKey("Enter")}
          className="size-14 rounded-full bg-gradient-to-br from-amber to-blue-600 text-amber-ink font-bold text-sm flex items-center justify-center shadow-lg hover:brightness-110 active:scale-95 transition"
          aria-label="OK / Select"
        >
          OK
        </button>

        {/* RIGHT */}
        <button
          type="button"
          onClick={() => dispatchKey("ArrowRight")}
          className="absolute right-1 top-1/2 -translate-y-1/2 flex size-10 items-center justify-center rounded-full text-muted hover:bg-surface hover:text-amber active:scale-90 transition"
          aria-label="Right"
        >
          <ArrowRight className="size-5" />
        </button>

        {/* DOWN */}
        <button
          type="button"
          onClick={() => dispatchKey("ArrowDown")}
          className="absolute bottom-1 left-1/2 -translate-x-1/2 flex size-10 items-center justify-center rounded-full text-muted hover:bg-surface hover:text-amber active:scale-90 transition"
          aria-label="Down"
        >
          <ArrowDown className="size-5" />
        </button>
      </div>

      {/* Auxiliary Action Keys */}
      <div className="grid grid-cols-3 gap-2 w-full pt-1">
        <button
          type="button"
          onClick={() => dispatchKey("Escape")}
          className="flex flex-col items-center justify-center gap-1 rounded-xl bg-surface/80 py-2.5 text-[10px] font-semibold text-muted hover:bg-surface hover:text-fg active:scale-95 transition border border-line/40"
          title="Back"
        >
          <RotateCcw className="size-4 text-amber" />
          <span>BACK</span>
        </button>
        <button
          type="button"
          onClick={() => {
            const homeBtn = document.querySelector<HTMLElement>("[data-nav-home]");
            if (homeBtn) homeBtn.click();
            else dispatchKey("Escape");
          }}
          className="flex flex-col items-center justify-center gap-1 rounded-xl bg-surface/80 py-2.5 text-[10px] font-semibold text-muted hover:bg-surface hover:text-fg active:scale-95 transition border border-line/40"
          title="Home"
        >
          <House className="size-4 text-emerald" />
          <span>HOME</span>
        </button>
        <button
          type="button"
          onClick={() => dispatchKey(" ")}
          className="flex flex-col items-center justify-center gap-1 rounded-xl bg-surface/80 py-2.5 text-[10px] font-semibold text-muted hover:bg-surface hover:text-fg active:scale-95 transition border border-line/40"
          title="Play / Pause"
        >
          <Play className="size-4 text-magenta" />
          <span>PLAY</span>
        </button>
      </div>

      <div className="text-[10px] text-muted/60 text-center tracking-tight">
        Arrow Keys • Enter to select • Esc back
      </div>
    </aside>
  );
}
