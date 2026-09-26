import { useEffect, useState } from "react";
import { clsx } from "clsx";
import {
  House,
  Monitor,
  RotateCw,
  Star,
  Tv,
} from "lucide-react";
import { inspectCached, searchCached } from "@/lib/tv/cache";
import type { HistoryEntry, Settings as SettingsModel } from "@/lib/tv/store";
import { APP_VERSION, type InspectResult, type SearchHit } from "@/lib/tv/types";
import {
  engineLabel,
  hostOf,
  isSafeClientUrl,
  resolveInput,
  searchPageUrl,
  titleFromUrl,
} from "@/lib/tv/url";
import { ago, BackButton, Screen, SectionLabel, TvButton } from "@/components/tv/ui";

const SUGGESTED = [
  { title: "Wikipedia", url: "https://www.wikipedia.org" },
  { title: "Internet Archive", url: "https://archive.org" },
  { title: "NASA", url: "https://www.nasa.gov" },
];

export function HomeScreen({
  engineName,
  history,
  onOpen,
  onBookmarks,
}: {
  engineName: string;
  history: HistoryEntry[];
  onOpen: (url: string) => void;
  onBookmarks: () => void;
}) {
  const [draft, setDraft] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const recent = history.slice(0, 8);

  function submit() {
    const resolved = resolveInput(draft);
    if (resolved.kind === "empty") return;
    if (resolved.kind === "url" && !isSafeClientUrl(resolved.url)) {
      setNotice("Enter a public web address or a search.");
      return;
    }
    setNotice(null);
    onOpen(resolved.kind === "url" ? resolved.url : draft);
  }

  return (
    <Screen>
      <div className="px-5 pt-6 pb-8">
        <img src="/xtream-logo.png" alt="Xtream" className="h-24 w-auto max-w-full object-contain object-left" />
        <form
          className="mt-4"
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <label className="sr-only" htmlFor="address">
            Search {engineName} or enter an address
          </label>
          <input
            id="address"
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value);
              setNotice(null);
            }}
            placeholder={`Search ${engineName} or enter an address`}
            enterKeyHint="go"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            className="w-full rounded-2xl bg-surface px-4 py-4 text-base text-fg outline-none placeholder:text-muted focus:outline focus:outline-2 focus:outline-amber"
          />
        </form>
        {notice ? <p className="mt-2 text-sm text-danger">{notice}</p> : null}
        <div className="mt-4 flex gap-3">
          <TvButton onClick={submit} className="min-h-12 bg-amber px-6 font-semibold text-amber-ink">
            Go
          </TvButton>
          <TvButton onClick={onBookmarks} className="min-h-12 bg-surface px-5 font-semibold text-fg">
            Bookmarks
          </TvButton>
        </div>

        {recent.length > 0 ? (
          <section className="mt-8">
            <SectionLabel>Recent</SectionLabel>
            <div className="mt-3 flex gap-3 overflow-x-auto pb-1">
              {recent.map((item) => (
                <TvButton
                  key={item.id}
                  onClick={() => onOpen(item.url)}
                  className="flex min-h-16 w-40 shrink-0 flex-col items-start justify-center bg-surface px-4 text-left"
                >
                  <span className="block w-full truncate text-base font-semibold text-fg">{item.title}</span>
                  <span className="block w-full truncate text-sm text-muted">{hostOf(item.url)}</span>
                </TvButton>
              ))}
            </div>
          </section>
        ) : null}

        <section className="mt-8">
          <SectionLabel>Suggested</SectionLabel>
          <div className="mt-3 flex gap-3 overflow-x-auto pb-1">
            {SUGGESTED.map((page) => (
              <TvButton
                key={page.url}
                onClick={() => onOpen(page.url)}
                className="flex min-h-16 w-40 shrink-0 items-center bg-surface px-4 text-left text-base font-semibold text-fg"
              >
                {page.title}
              </TvButton>
            ))}
          </div>
        </section>
      </div>
    </Screen>
  );
}

export function ResultsScreen({
  query,
  engine,
  onBack,
  onOpen,
}: {
  query: string;
  engine: SettingsModel["searchEngine"];
  onBack: () => void;
  onOpen: (url: string) => void;
}) {
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancel = false;
    setHits(null);
    setError(null);
    searchCached(query)
      .then((rows) => {
        if (!cancel) setHits(rows);
      })
      .catch((err: unknown) => {
        if (cancel) return;
        setError(err instanceof Error && err.message ? err.message : "Search is unavailable right now.");
        setHits([]);
      });
    return () => {
      cancel = true;
    };
  }, [query, attempt]);

  useEffect(() => {
    if (!hits || hits.length === 0) return;
    const active = document.activeElement;
    if (active instanceof HTMLElement && active.getAttribute("aria-label") === "Back") {
      document.querySelector<HTMLElement>("[data-result]")?.focus();
    }
  }, [hits]);

  return (
    <Screen>
      <div className="px-5 pt-6 pb-10 md:px-10">
        <div className="flex items-center gap-3">
          <BackButton onClick={onBack} primary={hits === null || hits.length === 0} />
          <div className="min-w-0">
            <p className="text-sm font-semibold tracking-widest text-amber uppercase">Search</p>
            <h1 className="truncate font-display text-3xl text-fg md:text-4xl">{query}</h1>
          </div>
        </div>
        <p className="mt-4 max-w-2xl text-sm text-muted">
          The remote list comes from DuckDuckGo. Open on {engineLabel(engine)} if you want that site’s own results page.
        </p>
        <TvButton
          onClick={() => onOpen(searchPageUrl(engine, query))}
          className="mt-4 min-h-12 bg-surface px-4 font-semibold text-fg"
        >
          Open on {engineLabel(engine)}
        </TvButton>

        {hits === null ? (
          <p className="mt-8 text-lg text-muted" aria-live="polite">
            Searching…
          </p>
        ) : error ? (
          <div className="mt-8 max-w-xl">
            <h2 className="font-display text-3xl text-fg">Search did not finish</h2>
            <p className="mt-2 text-base text-muted">{error}</p>
            <TvButton
              onClick={() => setAttempt((n) => n + 1)}
              className="mt-4 min-h-12 bg-amber px-5 font-semibold text-amber-ink"
            >
              Retry
            </TvButton>
          </div>
        ) : hits.length === 0 ? (
          <p className="mt-8 text-lg text-muted">Nothing came back for that search.</p>
        ) : (
          <div className="mt-6 grid gap-3">
            {hits.map((hit, index) => (
              <TvButton
                key={hit.url}
                data-result={index === 0 ? "" : undefined}
                onClick={() => onOpen(hit.url)}
                className="flex w-full flex-col gap-1 bg-surface px-4 py-4 text-left"
              >
                <span className="text-lg font-semibold text-fg">{hit.title}</span>
                <span className="text-sm text-amber">{hit.host}</span>
                {hit.snippet ? <span className="text-sm text-pretty text-muted">{hit.snippet}</span> : null}
              </TvButton>
            ))}
          </div>
        )}
      </div>
    </Screen>
  );
}

export function PageScreen({
  url,
  mode,
  javascriptEnabled,
  canBack,
  canForward,
  bookmarked,
  onBack,
  onForward,
  onHome,
  onOpen,
  onToggleBookmark,
  onMedia,
}: {
  url: string;
  mode: SettingsModel["browserMode"];
  javascriptEnabled: boolean;
  canBack: boolean;
  canForward: boolean;
  bookmarked: boolean;
  onBack: () => void;
  onForward: () => void;
  onHome: () => void;
  onOpen: (url: string) => void;
  onToggleBookmark: (title: string) => void;
  onMedia: (url: string, title: string, format: "hls" | "dash" | "file") => void;
}) {
  const [result, setResult] = useState<InspectResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [live, setLive] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancel = false;
    setLoading(true);
    setResult(null);
    setLive(false);
    inspectCached(url, mode).then((next) => {
      if (cancel) return;
      if (next.ok && next.kind === "media") {
        onMedia(next.url, next.title, next.format);
        return;
      }
      setResult(next);
      setLoading(false);
    });
    return () => {
      cancel = true;
    };
  }, [url, mode, attempt, onMedia]);

  const page = result && result.ok && result.kind === "page" ? result : null;
  const failure = result && !result.ok ? result : null;
  const title = page?.title || (failure ? "Unable to load this page" : titleFromUrl(url));

  return (
    <div className="flex h-full flex-col bg-bg text-fg">
      <header className="shrink-0 border-b border-line px-4 py-3 md:px-8">
        <div className="flex items-center gap-2">
          <BackButton onClick={onBack} primary />
          <TvButton
            disabled={!canForward}
            onClick={onForward}
            aria-label="Forward"
            className="grid size-12 place-items-center rounded-full bg-surface text-fg disabled:text-muted"
          >
            <RotateCw className="size-5 -scale-x-100" />
          </TvButton>
          <TvButton
            onClick={() => setAttempt((n) => n + 1)}
            aria-label="Reload"
            className="grid size-12 place-items-center rounded-full bg-surface text-fg"
          >
            <RotateCw className="size-5" />
          </TvButton>
          <TvButton
            onClick={onHome}
            aria-label="Home"
            className="grid size-12 place-items-center rounded-full bg-surface text-fg"
          >
            <House className="size-5" />
          </TvButton>
          <p className="min-w-0 flex-1 truncate px-2 text-sm text-muted">{page?.url ?? url}</p>
          <TvButton
            aria-label={bookmarked ? "Remove bookmark" : "Save bookmark"}
            aria-pressed={bookmarked}
            onClick={() => onToggleBookmark(title)}
            className="grid size-12 shrink-0 place-items-center rounded-full bg-surface text-fg"
          >
            <Star className="size-5" fill={bookmarked ? "currentColor" : "none"} />
          </TvButton>
        </div>
        <div className="mt-3 h-1 overflow-hidden rounded-full bg-surface">
          {loading ? <div className="h-full w-1/3 rounded-full bg-amber motion-safe:animate-pulse" /> : null}
        </div>
      </header>

      {live && page && !page.frameBlocked ? (
        <iframe
          title={page.title || "Live page"}
          src={page.url}
          className="min-h-0 w-full flex-1 bg-fg"
          sandbox={
            javascriptEnabled
              ? "allow-scripts allow-forms allow-popups allow-presentation allow-same-origin"
              : "allow-forms"
          }
          allow="autoplay; fullscreen; encrypted-media"
          referrerPolicy="no-referrer-when-downgrade"
        />
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-6 md:px-10">
          {loading ? (
            <div className="flex h-full min-h-[50dvh] flex-col items-center justify-center gap-6" aria-live="polite">
              <div className="relative grid size-20 place-items-center">
                <span className="absolute inset-0 rounded-full border border-line" />
                <span className="xtream-spin absolute inset-0 rounded-full border-2 border-transparent border-t-amber border-r-magenta" />
                <span className="size-2 rounded-full bg-amber" />
              </div>
              <p className="text-sm font-semibold tracking-widest text-fg uppercase">Loading</p>
            </div>
          ) : failure ? (
            <div className="max-w-xl">
              <p className="text-sm font-semibold tracking-widest text-danger uppercase">Page</p>
              <h1 className="mt-2 font-display text-4xl text-fg">{failure.message}</h1>
              <p className="mt-3 text-base text-muted">Check the address, then retry or go home.</p>
              <div className="mt-6 flex flex-wrap gap-3">
                <TvButton
                  onClick={() => setAttempt((n) => n + 1)}
                  className="min-h-12 bg-amber px-5 font-semibold text-amber-ink"
                >
                  Retry
                </TvButton>
                <TvButton onClick={onHome} className="min-h-12 bg-surface px-5 font-semibold text-fg">
                  Home
                </TvButton>
              </div>
            </div>
          ) : page ? (
            <article className="mx-auto max-w-3xl">
              <div className="flex flex-wrap items-center gap-2">
                {page.insecure ? (
                  <span className="rounded-lg bg-surface px-2 py-1 text-sm text-danger">Not secure</span>
                ) : null}
                {page.frameBlocked ? (
                  <span className="rounded-lg bg-surface px-2 py-1 text-sm text-muted">Reading view</span>
                ) : (
                  <TvButton
                    onClick={() => setLive(true)}
                    className="min-h-10 bg-surface px-3 text-sm font-semibold text-fg"
                  >
                    Show live page
                  </TvButton>
                )}
              </div>
              <h1 className="mt-4 font-display text-4xl text-fg md:text-5xl">{page.title}</h1>
              <p className="mt-2 text-sm text-amber">{hostOf(page.url)}</p>
              {page.frameBlocked ? (
                <p className="mt-4 text-base text-muted">
                  This site does not allow an embedded page. The reading view is what works on the remote.
                </p>
              ) : (
                <p className="mt-4 text-base text-muted">
                  Reading view keeps the remote on Xtream. The live page is for a pointer or touch.
                </p>
              )}
              <div className="mt-6 grid gap-4">
                {page.blocks.map((block, index) =>
                  block.kind === "h" ? (
                    <h2 key={`${block.text}-${index}`} className="font-display text-2xl text-fg">
                      {block.text}
                    </h2>
                  ) : (
                    <p key={`${block.text}-${index}`} className="text-lg leading-relaxed text-fg">
                      {block.kind === "li" ? `• ${block.text}` : block.text}
                    </p>
                  ),
                )}
              </div>
              {page.links.length > 0 ? (
                <section className="mt-10">
                  <SectionLabel>On this page</SectionLabel>
                  <div className="mt-3 grid gap-2">
                    {page.links.map((link) => (
                      <TvButton
                        key={link.href}
                        onClick={() => onOpen(link.href)}
                        className="w-full bg-surface px-4 py-3 text-left"
                      >
                        <span className="block text-base font-semibold text-fg">{link.text}</span>
                        <span className="block truncate text-sm text-muted">{hostOf(link.href)}</span>
                      </TvButton>
                    ))}
                  </div>
                </section>
              ) : null}
            </article>
          ) : null}
        </div>
      )}
      {live ? (
        <div className="shrink-0 border-t border-line px-4 py-3">
          <TvButton onClick={() => setLive(false)} className="min-h-12 bg-surface px-4 font-semibold text-fg">
            Back to reading view
          </TvButton>
        </div>
      ) : null}
    </div>
  );
}

export function ListScreen({
  title,
  empty,
  rows,
  onBack,
  onOpen,
  onRemove,
}: {
  title: string;
  empty: string;
  rows: { id: string; title: string; url: string; meta?: string }[];
  onBack: () => void;
  onOpen: (url: string) => void;
  onRemove: (id: string) => void;
}) {
  return (
    <Screen>
      <div className="px-5 pt-6 pb-10 md:px-10">
        <div className="flex items-center gap-3">
          <BackButton onClick={onBack} primary />
          <h1 className="font-display text-4xl text-fg">{title}</h1>
        </div>
        {rows.length === 0 ? (
          <p className="mt-8 max-w-md text-lg text-muted">{empty}</p>
        ) : (
          <div className="mt-6 grid gap-3">
            {rows.map((row) => (
              <div key={row.id} className="flex items-stretch gap-2">
                <TvButton
                  onClick={() => onOpen(row.url)}
                  className="min-w-0 flex-1 bg-surface px-4 py-3 text-left"
                >
                  <span className="block truncate text-lg font-semibold text-fg">{row.title}</span>
                  <span className="block truncate text-sm text-muted">{hostOf(row.url)}</span>
                </TvButton>
                <div className="flex shrink-0 flex-col items-end justify-between">
                  {row.meta ? <span className="text-sm text-muted">{row.meta}</span> : <span />}
                  <TvButton
                    onClick={() => onRemove(row.id)}
                    aria-label={`Remove ${row.title}`}
                    className="min-h-12 bg-surface-2 px-4 text-sm font-semibold text-fg"
                  >
                    Remove
                  </TvButton>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Screen>
  );
}

export function SettingsScreen({
  settings,
  onBack,
  onChange,
  onEditHomepage,
  onClearHistory,
  onClearBookmarks,
  onClearCache,
  onClearAll,
}: {
  settings: SettingsModel;
  onBack: () => void;
  onChange: (patch: Partial<SettingsModel>) => void;
  onEditHomepage: () => void;
  onClearHistory: () => void;
  onClearBookmarks: () => void;
  onClearCache: () => void;
  onClearAll: () => void;
}) {
  const [pending, setPending] = useState<null | "history" | "bookmarks" | "all" | "cache">(null);
  const [note, setNote] = useState<string | null>(null);

  function confirm(kind: "history" | "bookmarks" | "all" | "cache") {
    if (pending !== kind) {
      setPending(kind);
      setNote(null);
      return;
    }
    if (kind === "history") onClearHistory();
    if (kind === "bookmarks") onClearBookmarks();
    if (kind === "cache") onClearCache();
    if (kind === "all") onClearAll();
    setPending(null);
    setNote(kind === "cache" ? "Readable-page cache cleared." : "Cleared.");
  }

  return (
    <Screen>
      <div className="px-5 pt-6 pb-12 md:px-10">
        <div className="flex items-center gap-3">
          <BackButton onClick={onBack} primary />
          <h1 className="font-display text-4xl text-fg">Settings</h1>
        </div>

        <section className="mt-8 max-w-3xl">
          <SectionLabel>Browser</SectionLabel>
          <TvButton
            onClick={onEditHomepage}
            className="mt-3 flex min-h-16 w-full items-center justify-between gap-3 bg-surface px-4 text-left"
          >
            <span>
              <span className="block text-base font-semibold text-fg">Homepage</span>
              <span className="block truncate text-sm text-muted">{settings.homepage || "Xtream home"}</span>
            </span>
            <span className="text-sm font-semibold text-amber">Edit</span>
          </TvButton>

          <p className="mt-5 text-sm text-muted">Search engine for the full results page</p>
          <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-3">
            {(["google", "duckduckgo", "bing"] as const).map((engine) => (
              <TvButton
                key={engine}
                aria-pressed={settings.searchEngine === engine}
                onClick={() => onChange({ searchEngine: engine })}
                className={clsx(
                  "min-h-12 font-semibold",
                  settings.searchEngine === engine ? "bg-amber text-amber-ink" : "bg-surface text-fg",
                )}
              >
                {engineLabel(engine)}
              </TvButton>
            ))}
          </div>

          <p className="mt-5 text-sm text-muted">Browser mode for the readable view</p>
          <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
            <TvButton
              aria-pressed={settings.browserMode === "standard"}
              onClick={() => onChange({ browserMode: "standard" })}
              className={clsx(
                "flex min-h-16 items-center justify-center gap-2 font-semibold",
                settings.browserMode === "standard" ? "bg-amber text-amber-ink" : "bg-surface text-fg",
              )}
            >
              <Tv className="size-4" />
              Standard
            </TvButton>
            <TvButton
              aria-pressed={settings.browserMode === "desktop"}
              onClick={() => onChange({ browserMode: "desktop" })}
              className={clsx(
                "flex min-h-16 items-center justify-center gap-2 font-semibold",
                settings.browserMode === "desktop" ? "bg-amber text-amber-ink" : "bg-surface text-fg",
              )}
            >
              <Monitor className="size-4" />
              Desktop
            </TvButton>
          </div>
          <p className="mt-2 text-sm text-muted">
            Desktop mode changes how the readable view is requested. A live page still uses this device’s browser.
          </p>

          <TvButton
            aria-pressed={settings.javascriptEnabled}
            onClick={() => onChange({ javascriptEnabled: !settings.javascriptEnabled })}
            className="mt-4 flex min-h-14 w-full items-center justify-between bg-surface px-4 text-left"
          >
            <span className="font-semibold text-fg">JavaScript on live pages</span>
            <span className="text-sm font-semibold text-amber">{settings.javascriptEnabled ? "On" : "Off"}</span>
          </TvButton>
        </section>

        <section className="mt-8 max-w-3xl">
          <SectionLabel>Video</SectionLabel>
          <TvButton
            aria-pressed={settings.hideControlsWhilePlaying}
            onClick={() => onChange({ hideControlsWhilePlaying: !settings.hideControlsWhilePlaying })}
            className="mt-3 flex min-h-14 w-full items-center justify-between bg-surface px-4 text-left"
          >
            <span className="font-semibold text-fg">Hide controls while playing</span>
            <span className="text-sm font-semibold text-amber">
              {settings.hideControlsWhilePlaying ? "On" : "Off"}
            </span>
          </TvButton>
        </section>

        <section className="mt-8 max-w-3xl">
          <SectionLabel>Privacy</SectionLabel>
          <p className="mt-3 text-sm text-muted">
            Xtream does not store site passwords. Bookmarks, history, and these settings stay on this device.
          </p>
          {note ? <p className="mt-2 text-sm text-amber">{note}</p> : null}
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            <ClearButton
              label="Clear history"
              pending={pending === "history"}
              onClick={() => confirm("history")}
            />
            <ClearButton
              label="Clear bookmarks"
              pending={pending === "bookmarks"}
              onClick={() => confirm("bookmarks")}
            />
            <ClearButton label="Clear cache" pending={pending === "cache"} onClick={() => confirm("cache")} />
            <ClearButton
              label="Clear browsing data"
              pending={pending === "all"}
              onClick={() => confirm("all")}
            />
          </div>
        </section>

        <section className="mt-8 max-w-3xl">
          <SectionLabel>About</SectionLabel>
          <div className="mt-3 grid gap-2 rounded-xl bg-surface p-4 text-sm text-muted">
            <p className="flex items-center gap-2 text-fg">
              <img src="/xtream-icon.png" alt="" className="size-8 rounded-lg" />
              Xtream {APP_VERSION}
            </p>
            <p>Ordinary HTML5 video, including MP4 and HLS. Protected DRM streams are refused on purpose.</p>
            <p>Readable pages use a {settings.browserMode === "desktop" ? "desktop" : "Android TV"} request.</p>
          </div>
        </section>
      </div>
    </Screen>
  );
}

function ClearButton({ label, pending, onClick }: { label: string; pending: boolean; onClick: () => void }) {
  return (
    <TvButton onClick={onClick} className="min-h-12 bg-surface-2 px-4 text-left font-semibold text-fg">
      {pending ? `Confirm ${label.toLowerCase()}` : label}
    </TvButton>
  );
}

export function ErrorScreen({
  title,
  message,
  onHome,
}: {
  title: string;
  message: string;
  onHome: () => void;
}) {
  return (
    <Screen>
      <div className="flex min-h-full flex-col items-start justify-center px-5 py-10 md:px-10">
        <p className="text-sm font-semibold tracking-widest text-danger uppercase">Can’t open</p>
        <h1 className="mt-3 max-w-xl font-display text-4xl text-fg">{title}</h1>
        <p className="mt-3 max-w-lg text-lg text-muted">{message}</p>
        <TvButton
          primary
          onClick={onHome}
          className="mt-6 min-h-12 bg-amber px-5 font-semibold text-amber-ink"
        >
          Home
        </TvButton>
      </div>
    </Screen>
  );
}
