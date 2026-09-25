import { useEffect, useRef } from "react";

type Dir = "up" | "down" | "left" | "right";

const DIRS: Record<string, Dir> = {
  ArrowUp: "up",
  ArrowDown: "down",
  ArrowLeft: "left",
  ArrowRight: "right",
};

function visible(el: HTMLElement): boolean {
  if (el.closest("[inert]")) return false;
  if (el.hasAttribute("disabled") || el.getAttribute("aria-disabled") === "true") return false;
  const style = getComputedStyle(el);
  if (style.display === "none" || style.visibility === "hidden") return false;
  const rect = el.getBoundingClientRect();
  return rect.width > 0 && rect.height > 0;
}

export function focusables(): HTMLElement[] {
  return [...document.querySelectorAll<HTMLElement>("[data-tv]")].filter(visible);
}

function syncFocusClass(el: HTMLElement | null) {
  document.querySelectorAll(".is-on").forEach((node) => node.classList.remove("is-on"));
  if (el?.hasAttribute("data-tv")) el.classList.add("is-on");
}

function pick(current: HTMLElement, items: HTMLElement[], dir: Dir): HTMLElement | null {
  const c = current.getBoundingClientRect();
  const cx = c.left + c.width / 2;
  const cy = c.top + c.height / 2;
  let best: { el: HTMLElement; score: number } | null = null;
  let loose: { el: HTMLElement; score: number } | null = null;

  for (const el of items) {
    if (el === current) continue;
    const r = el.getBoundingClientRect();
    const dx = r.left + r.width / 2 - cx;
    const dy = r.top + r.height / 2 - cy;
    const primary = dir === "left" ? -dx : dir === "right" ? dx : dir === "up" ? -dy : dy;
    const secondary = dir === "left" || dir === "right" ? Math.abs(dy) : Math.abs(dx);
    if (primary < 6) continue;
    const looseScore = primary + secondary * 2;
    if (!loose || looseScore < loose.score) loose = { el, score: looseScore };
    const overlap =
      dir === "left" || dir === "right"
        ? Math.min(c.bottom, r.bottom) - Math.max(c.top, r.top)
        : Math.min(c.right, r.right) - Math.max(c.left, r.left);
    const aligned = overlap > 8;
    if (!aligned && secondary > primary * 1.2) continue;
    const score = primary + secondary * (aligned ? 0.3 : 1.5);
    if (!best || score < best.score) best = { el, score };
  }
  return (best ?? loose)?.el ?? null;
}

export function moveFocus(dir: Dir) {
  const items = focusables();
  const current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  if (!current || !current.hasAttribute("data-tv") || !items.includes(current)) {
    const primary =
      document.querySelector<HTMLElement>("#focus-root [data-tv-primary]") ??
      document.querySelector<HTMLElement>("#focus-root [data-tv]");
    primary?.focus();
    return;
  }
  const next = pick(current, items, dir);
  if (!next) return;
  next.focus();
  next.scrollIntoView({ block: "nearest", inline: "nearest" });
}

export function focusPrimary() {
  const el =
    document.querySelector<HTMLElement>("#focus-root [data-tv-primary]") ??
    document.querySelector<HTMLElement>("#focus-root [data-tv]");
  el?.focus();
}

type RemoteOpts = {
  onBack: () => void;
  onText: ((text: string) => void) | null;
  onChrome: () => boolean;
};

export function useRemote(opts: RemoteOpts) {
  const ref = useRef(opts);
  ref.current = opts;

  useEffect(() => {
    const onFocusIn = (event: FocusEvent) => {
      syncFocusClass(event.target instanceof HTMLElement ? event.target : null);
    };
    const onKey = (event: KeyboardEvent) => {
      const o = ref.current;
      if (event.metaKey || event.ctrlKey || event.altKey) return;
      const chromeKeys = ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Enter", " "];
      if (chromeKeys.includes(event.key) && o.onChrome()) {
        event.preventDefault();
        return;
      }
      const active = document.activeElement;
      if (
        (event.key === "ArrowLeft" || event.key === "ArrowRight") &&
        active instanceof HTMLElement &&
        active.hasAttribute("data-tv-seek")
      ) {
        event.preventDefault();
        active.dispatchEvent(
          new CustomEvent("xtream-nudge", { detail: event.key === "ArrowRight" ? 10 : -10 }),
        );
        return;
      }
      if (event.key in DIRS) {
        event.preventDefault();
        moveFocus(DIRS[event.key] as Dir);
        return;
      }
      if (event.key === "Escape") {
        event.preventDefault();
        o.onBack();
        return;
      }
      if (event.key === "Backspace") {
        event.preventDefault();
        if (o.onText) o.onText("\b");
        else o.onBack();
        return;
      }
      if (o.onText && event.key.length === 1 && event.key >= " " && event.key <= "~") {
        event.preventDefault();
        o.onText(event.key);
      }
    };
    const onPaste = (event: ClipboardEvent) => {
      const o = ref.current;
      if (!o.onText) return;
      const text = event.clipboardData?.getData("text") ?? "";
      const clean = text.replace(/\s+/g, " ").trim();
      if (!clean) return;
      event.preventDefault();
      o.onText(clean.slice(0, 400));
    };
    window.addEventListener("focusin", onFocusIn);
    window.addEventListener("keydown", onKey);
    window.addEventListener("paste", onPaste);
    return () => {
      window.removeEventListener("focusin", onFocusIn);
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("paste", onPaste);
    };
  }, []);
}
