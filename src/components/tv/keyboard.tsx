import { useState } from "react";
import { clsx } from "clsx";
import { Delete } from "lucide-react";
import { TvButton } from "@/components/tv/ui";

const ROWS = ["abcdef", "ghijkl", "mnopqr", "stuvwx", "yz.-/:", "123456", "7890@_"];

type Props = {
  draft: string;
  placeholder: string;
  goLabel: string;
  notice?: string;
  onDraft: (next: string) => void;
  onClose: () => void;
  onSubmit: () => void;
};

export function Keyboard({ draft, placeholder, goLabel, notice, onDraft, onClose, onSubmit }: Props) {
  const [shift, setShift] = useState(false);

  function insert(value: string) {
    if (value === "\b") {
      onDraft(Array.from(draft).slice(0, -1).join(""));
      return;
    }
    const next = shift && value.length === 1 ? value.toUpperCase() : value;
    onDraft((draft + next).slice(0, 500));
  }

  return (
    <div className="flex h-full flex-col bg-bg text-fg">
      <div className="px-5 pt-6 md:px-10">
        <p className="text-sm font-semibold tracking-widest text-amber uppercase">Address</p>
        <div className="mt-3 min-h-16 rounded-xl bg-surface px-4 py-3" aria-live="polite">
          {draft ? (
            <p className="text-2xl leading-snug break-all text-fg md:text-3xl">
              {draft}
              <span className="ml-1 inline-block h-7 w-0.5 translate-y-1 bg-amber align-middle motion-safe:animate-pulse" />
            </p>
          ) : (
            <p className="text-2xl text-muted md:text-3xl">{placeholder}</p>
          )}
        </div>
        <p className="mt-2 text-sm text-muted">Type, paste, or use the keys. Arrows move. OK presses a key.</p>
        {notice ? <p className="mt-2 text-sm text-danger">{notice}</p> : null}
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4 md:px-10">
        <div className="mx-auto grid max-w-3xl grid-cols-6 gap-2">
          {ROWS.map((row) =>
            [...row].map((key) => (
              <TvButton
                key={key}
                onClick={() => insert(key)}
                className="min-h-12 bg-surface text-lg font-semibold text-fg"
              >
                {shift && /[a-z]/.test(key) ? key.toUpperCase() : key}
              </TvButton>
            )),
          )}
          <TvButton
            onClick={() => insert(" ")}
            className="col-span-3 min-h-12 bg-surface text-base font-semibold text-fg"
          >
            space
          </TvButton>
          <TvButton
            onClick={() => insert("\b")}
            aria-label="Delete"
            className="col-span-3 flex min-h-12 items-center justify-center gap-2 bg-surface text-base font-semibold text-fg"
          >
            <Delete className="size-5" />
            delete
          </TvButton>
          {[
            [".com", ".com"],
            [".org", ".org"],
            [".net", ".net"],
            ["https://", "https://"],
            ["www.", "www."],
            [shift ? "ABC" : "abc", "shift"],
          ].map(([label, value]) => (
            <TvButton
              key={value}
              onClick={() => {
                if (value === "shift") {
                  setShift((on) => !on);
                  return;
                }
                insert(value ?? "");
              }}
              aria-pressed={value === "shift" ? shift : undefined}
              className={clsx(
                "col-span-2 min-h-12 text-base font-semibold",
                value === "shift" && shift ? "bg-amber text-amber-ink" : "bg-surface-2 text-fg",
              )}
            >
              {label}
            </TvButton>
          ))}
          <TvButton
            onClick={onClose}
            className="col-span-3 min-h-14 bg-surface-2 text-base font-semibold text-fg"
          >
            Close
          </TvButton>
          <TvButton
            primary
            onClick={onSubmit}
            className="col-span-3 min-h-14 bg-amber text-base font-semibold text-amber-ink"
          >
            {goLabel}
          </TvButton>
        </div>
      </div>
    </div>
  );
}
