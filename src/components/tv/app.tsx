import { useCallback, useEffect, useState, type ReactNode } from "react";
import { clearBrowseCache } from "@/lib/tv/cache";
import { useTv } from "@/lib/tv/store";
import type { MediaFormat } from "@/lib/tv/types";
import {
  engineLabel,
  isDirectMedia,
  isSafeClientUrl,
  mediaFormat,
  resolveInput,
  titleFromUrl,
} from "@/lib/tv/url";
import { Keyboard } from "@/components/tv/keyboard";
import { Player } from "@/components/tv/player";
import { focusPrimary, useRemote } from "@/components/tv/remote";
import {
  ErrorScreen,
  HomeScreen,
  ListScreen,
  PageScreen,
  ResultsScreen,
  SettingsScreen,
} from "@/components/tv/screens";
import { ago, TvButton } from "@/components/tv/ui";

type PageEntry = { url: string };

type Route =
  | { id: "home" }
  | { id: "bookmarks" }
  | { id: "history" }
  | { id: "settings" }
  | { id: "results"; query: string }
  | { id: "page"; entries: PageEntry[]; index: number }
  | { id: "player"; url: string; title: string; format: MediaFormat }
  | { id: "error"; title: string; message: string };

type Overlay = { target: "query" | "homepage"; draft: string } | null;

export function XtreamApp() {
  const bookmarks = useTv((s) => s.bookmarks);
  const history = useTv((s) => s.history);
  const settings = useTv((s) => s.settings);
  const toggleBookmark = useTv((s) => s.toggleBookmark);
  const isBookmarked = useTv((s) => s.isBookmarked);
  const removeBookmark = useTv((s) => s.removeBookmark);
  const visit = useTv((s) => s.visit);
  const updateSettings = useTv((s) => s.updateSettings);
  const clearHistory = useTv((s) => s.clearHistory);
  const clearBookmarks = useTv((s) => s.clearBookmarks);
  const clearAll = useTv((s) => s.clearAll);

  const [stack, setStack] = useState<Route[]>([{ id: "home" }]);
  const [overlay, setOverlay] = useState<Overlay>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [confirmExit, setConfirmExit] = useState(false);
  const [asleep, setAsleep] = useState(false);

  useEffect(() => {
    void useTv.persist.rehydrate();
  }, []);

  const top = stack[stack.length - 1] ?? { id: "home" as const };
  const page = top.id === "page" ? top : null;
  const pageUrl = page ? page.entries[page.index]?.url ?? "" : "";

  const goHome = useCallback(() => {
    const homepage = useTv.getState().settings.homepage.trim();
    if (homepage && isSafeClientUrl(homepage) && !isDirectMedia(homepage)) {
      setStack([
        { id: "home" },
        { id: "page", entries: [{ url: homepage }], index: 0 },
      ]);
      return;
    }
    if (homepage && isSafeClientUrl(homepage) && isDirectMedia(homepage)) {
      setStack([
        { id: "home" },
        {
          id: "player",
          url: homepage,
          title: titleFromUrl(homepage),
          format: mediaFormat(homepage) ?? "file",
        },
      ]);
      return;
    }
    setStack([{ id: "home" }]);
  }, []);

  const openAddress = useCallback((raw: string) => {
    const resolved = resolveInput(raw);
    if (resolved.kind === "empty") return;
    if (resolved.kind === "search") {
      setStack((current) => [...current, { id: "results", query: resolved.query }]);
      return;
    }
    const url = resolved.url;
    if (!isSafeClientUrl(url)) {
      setStack((current) => [
        ...current,
        {
          id: "error",
          title: "That address is blocked",
          message: "Xtream only opens public http and https sites. Local and private addresses stay closed.",
        },
      ]);
      return;
    }
    const format = mediaFormat(url);
    if (format) {
      setStack((current) => [
        ...current,
        { id: "player", url, title: titleFromUrl(url), format },
      ]);
      return;
    }
    setStack((current) => {
      const last = current[current.length - 1];
      if (last?.id === "page") {
        const entries = last.entries.slice(0, last.index + 1).concat({ url });
        const copy = current.slice();
        copy[copy.length - 1] = { ...last, entries, index: entries.length - 1 };
        return copy;
      }
      return [...current, { id: "page", entries: [{ url }], index: 0 }];
    });
  }, []);

  const back = useCallback(() => {
    if (asleep) return;
    if (overlay) {
      setOverlay(null);
      setNotice(null);
      return;
    }
    if (confirmExit) {
      setConfirmExit(false);
      return;
    }
    if (page && page.index > 0) {
      setStack((current) => {
        const copy = current.slice();
        const last = copy[copy.length - 1];
        if (last?.id !== "page") return current;
        copy[copy.length - 1] = { ...last, index: last.index - 1 };
        return copy;
      });
      return;
    }
    if (top.id === "home") {
      setConfirmExit(true);
      return;
    }
    setStack((current) => (current.length > 1 ? current.slice(0, -1) : current));
  }, [asleep, confirmExit, overlay, page, top.id]);

  const applyText = useCallback(
    (chunk: string) => {
      if (asleep || confirmExit) return;
      if (chunk === "\b" && !overlay) {
        if (top.id !== "home") {
          back();
          return;
        }
        setConfirmExit(true);
        return;
      }
      const target = overlay?.target ?? "query";
      const base = overlay?.draft ?? (target === "homepage" ? settings.homepage : "");
      const next =
        chunk === "\b" ? Array.from(base).slice(0, -1).join("") : (base + chunk).slice(0, 500);
      setNotice(null);
      setOverlay({ target, draft: next });
    },
    [asleep, back, confirmExit, overlay, settings.homepage, top.id],
  );

  const typing =
    !asleep && !confirmExit && (overlay !== null || top.id === "home");

  useRemote({
    onBack: back,
    onText: typing ? applyText : null,
    onChrome: () => {
      if (top.id !== "player" || !settings.hideControlsWhilePlaying) return false;
      if (!document.getElementById("player-chrome-hidden")) return false;
      document.getElementById("player-root")?.setAttribute("data-show-chrome", "1");
      window.dispatchEvent(new CustomEvent("xtream-show-chrome"));
      return true;
    },
  });

  const focusToken = asleep
    ? "sleep"
    : confirmExit
      ? "exit"
      : overlay
        ? `key-${overlay.target}`
        : top.id === "page"
          ? `page-${pageUrl}`
          : top.id === "results"
            ? `results-${top.query}`
            : top.id === "player"
              ? `player-${top.url}`
              : top.id;

  useEffect(() => {
    const id = window.requestAnimationFrame(() => focusPrimary());
    return () => window.cancelAnimationFrame(id);
  }, [focusToken]);

  useEffect(() => {
    if (top.id === "player") visit(top.url, top.title);
  }, [top, visit]);

  function submitOverlay() {
    if (!overlay) return;
    const draft = overlay.draft.trim();
    if (overlay.target === "homepage") {
      if (!draft) {
        updateSettings({ homepage: "" });
        setOverlay(null);
        setNotice(null);
        return;
      }
      const resolved = resolveInput(draft);
      if (resolved.kind !== "url" || !isSafeClientUrl(resolved.url)) {
        setNotice("Enter a public web address, or clear the field to use the Xtream home.");
        return;
      }
      updateSettings({ homepage: resolved.url });
      setOverlay(null);
      setNotice(null);
      return;
    }
    if (!draft) {
      setNotice("Type an address or a few words to search.");
      return;
    }
    setOverlay(null);
    setNotice(null);
    openAddress(draft);
  }

  const becomeMedia = useCallback((url: string, title: string, format: MediaFormat) => {
    setStack((current) => {
      const copy = current.slice();
      copy[copy.length - 1] = { id: "player", url, title, format };
      return copy;
    });
  }, []);

  let body: ReactNode;
  if (asleep) {
    body = (
      <div className="grid h-full place-items-center bg-bg-deep px-6 text-center">
        <TvButton
          primary
          onClick={() => setAsleep(false)}
          className="grid min-h-24 place-items-center gap-3 bg-surface px-8"
        >
          <img src="/xtream-icon.png" alt="" className="mx-auto h-20 w-20 rounded-2xl" />
          <span className="text-lg font-semibold text-fg">Press OK to wake Xtream</span>
        </TvButton>
      </div>
    );
  } else if (confirmExit) {
    body = (
      <div className="grid h-full place-items-center bg-bg px-6">
        <div className="w-full max-w-lg text-center" role="dialog" aria-modal="true" aria-label="Exit Xtream">
          <h1 className="font-display text-4xl text-fg">Turn off Xtream?</h1>
          <p className="mt-3 text-base text-muted">Bookmarks and history stay on this device.</p>
          <div className="mt-6 flex justify-center gap-3">
            <TvButton
              primary
              onClick={() => setConfirmExit(false)}
              className="min-h-12 bg-amber px-6 font-semibold text-amber-ink"
            >
              Stay
            </TvButton>
            <TvButton
              onClick={() => {
                setConfirmExit(false);
                setAsleep(true);
              }}
              className="min-h-12 bg-surface px-6 font-semibold text-fg"
            >
              Turn off
            </TvButton>
          </div>
        </div>
      </div>
    );
  } else if (overlay) {
    const goLabel =
      overlay.target === "homepage"
        ? "Save"
        : resolveInput(overlay.draft).kind === "url"
          ? "Open"
          : "Search";
    body = (
      <Keyboard
        draft={overlay.draft}
        notice={notice ?? undefined}
        placeholder={
          overlay.target === "homepage"
            ? "Homepage address, or leave blank"
            : `Search ${engineLabel(settings.searchEngine)} or enter an address`
        }
        goLabel={goLabel}
        onDraft={(draft) => {
          setNotice(null);
          setOverlay({ ...overlay, draft });
        }}
        onClose={() => {
          setOverlay(null);
          setNotice(null);
        }}
        onSubmit={submitOverlay}
      />
    );
  } else if (top.id === "home") {
    body = (
      <HomeScreen
        engineName={engineLabel(settings.searchEngine)}
        history={history}
        onOpen={openAddress}
        onBookmarks={() => setStack((s) => [...s, { id: "bookmarks" }])}
      />
    );
  } else if (top.id === "results") {
    body = (
      <ResultsScreen
        query={top.query}
        engine={settings.searchEngine}
        onBack={back}
        onOpen={openAddress}
      />
    );
  } else if (top.id === "page") {
    body = (
      <PageScreen
        url={pageUrl}
        mode={settings.browserMode}
        javascriptEnabled={settings.javascriptEnabled}
        canBack={top.index > 0}
        canForward={top.index < top.entries.length - 1}
        bookmarked={isBookmarked(pageUrl)}
        onBack={back}
        onForward={() => {
          setStack((current) => {
            const copy = current.slice();
            const last = copy[copy.length - 1];
            if (last?.id !== "page" || last.index >= last.entries.length - 1) return current;
            copy[copy.length - 1] = { ...last, index: last.index + 1 };
            return copy;
          });
        }}
        onHome={goHome}
        onOpen={openAddress}
        onToggleBookmark={(title) => toggleBookmark(pageUrl, title)}
        onMedia={becomeMedia}
      />
    );
  } else if (top.id === "player") {
    body = (
      <PlayerChrome
        url={top.url}
        title={top.title}
        format={top.format}
        bookmarked={isBookmarked(top.url)}
        hideControls={settings.hideControlsWhilePlaying}
        onBack={back}
        onToggleBookmark={() => toggleBookmark(top.url, top.title)}
      />
    );
  } else if (top.id === "bookmarks") {
    body = (
      <ListScreen
        title="Bookmarks"
        empty="No bookmarks yet. Open a page or video and press the star."
        rows={bookmarks.map((item) => ({ id: item.id, title: item.title, url: item.url }))}
        onBack={back}
        onOpen={openAddress}
        onRemove={removeBookmark}
      />
    );
  } else if (top.id === "history") {
    body = (
      <ListScreen
        title="History"
        empty="Nothing opened yet."
        rows={history.map((item) => ({
          id: item.id,
          title: item.title,
          url: item.url,
          meta: ago(item.visitedAt),
        }))}
        onBack={back}
        onOpen={openAddress}
        onRemove={(id) => {
          const entry = history.find((item) => item.id === id);
          if (!entry) return;
          useTv.setState({ history: useTv.getState().history.filter((item) => item.url !== entry.url) });
        }}
      />
    );
  } else if (top.id === "settings") {
    body = (
      <SettingsScreen
        settings={settings}
        onBack={back}
        onChange={updateSettings}
        onEditHomepage={() => {
          setNotice(null);
          setOverlay({ target: "homepage", draft: settings.homepage });
        }}
        onClearHistory={clearHistory}
        onClearBookmarks={clearBookmarks}
        onClearCache={clearBrowseCache}
        onClearAll={() => {
          clearAll();
          clearBrowseCache();
        }}
      />
    );
  } else {
    body = (
      <ErrorScreen title={top.title} message={top.message} onHome={() => setStack([{ id: "home" }])} />
    );
  }

  return (
    <div className="phone-stage">
      <div id="focus-root" className="phone-shell text-fg">
        {body}
      </div>
    </div>
  );
}

function PlayerChrome(props: {
  url: string;
  title: string;
  format: MediaFormat;
  bookmarked: boolean;
  hideControls: boolean;
  onBack: () => void;
  onToggleBookmark: () => void;
}) {
  const [show, setShow] = useState(true);
  useEffect(() => {
    const onShow = () => setShow(true);
    window.addEventListener("xtream-show-chrome", onShow);
    return () => window.removeEventListener("xtream-show-chrome", onShow);
  }, []);
  return (
    <div id="player-root">
      <Player
        {...props}
        hideControls={props.hideControls}
        key={`${props.url}:${show ? "on" : "on"}`}
      />
    </div>
  );
}
