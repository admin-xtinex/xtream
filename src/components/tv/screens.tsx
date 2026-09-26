import { useEffect, useState } from "react";
import { clsx } from "clsx";
import {
  Bookmark,
  BookOpen,
  Check,
  Compass,
  ExternalLink,
  Flame,
  Globe,
  History as HistoryIcon,
  House,
  Laptop,
  Lock,
  Monitor,
  Play,
  PlaySquare,
  Radio,
  RefreshCw,
  Rocket,
  RotateCw,
  Search,
  Settings as SettingsIcon,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Star,
  Trash2,
  Tv,
  X,
  Zap,
} from "lucide-react";
import { inspectCached, searchCached } from "@/lib/tv/cache";
import type { Bookmark as BookmarkItem, HistoryEntry, Settings as SettingsModel } from "@/lib/tv/store";
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

export interface FeaturedChannel {
  title: string;
  url: string;
  category: "Streaming" | "Knowledge" | "Space" | "Media" | "Community";
  description: string;
  iconName: string;
  gradient: string;
}

const FEATURED_CHANNELS: FeaturedChannel[] = [
  {
    title: "OgoMovies",
    url: "https://ogomovies2.com.pk/",
    category: "Streaming",
    description: "Watch latest movies and trending web series",
    iconName: "play",
    gradient: "from-amber-600 to-yellow-600",
  },
  {
    title: "YouTube",
    url: "https://www.youtube.com",
    category: "Streaming",
    description: "Videos, music, and livestreams for TV & mobile",
    iconName: "play",
    gradient: "from-red-600 to-rose-700",
  },
  {
    title: "Wikipedia",
    url: "https://www.wikipedia.org",
    category: "Knowledge",
    description: "The free, multilingual encyclopedia",
    iconName: "book",
    gradient: "from-slate-700 to-zinc-900",
  },
  {
    title: "Internet Archive",
    url: "https://archive.org",
    category: "Knowledge",
    description: "Millions of free books, movies, audio, and software",
    iconName: "globe",
    gradient: "from-blue-700 to-indigo-900",
  },
  {
    title: "NASA TV",
    url: "https://www.nasa.gov",
    category: "Space",
    description: "Live rocket launches, solar system missions, and deep space",
    iconName: "rocket",
    gradient: "from-sky-600 to-blue-800",
  },
  {
    title: "Twitch",
    url: "https://www.twitch.tv",
    category: "Streaming",
    description: "Live gameplay, esports broadcasts, and creator streams",
    iconName: "radio",
    gradient: "from-purple-700 to-violet-900",
  },
  {
    title: "Reddit",
    url: "https://www.reddit.com",
    category: "Community",
    description: "Trending conversations, news, and entertainment communities",
    iconName: "compass",
    gradient: "from-orange-600 to-amber-700",
  },
  {
    title: "Open Library",
    url: "https://openlibrary.org",
    category: "Knowledge",
    description: "Borrow and read digitized classics online",
    iconName: "book",
    gradient: "from-emerald-700 to-teal-900",
  },
  {
    title: "DuckDuckGo",
    url: "https://duckduckgo.com",
    category: "Media",
    description: "Privacy-focused search without user tracking",
    iconName: "search",
    gradient: "from-amber-600 to-orange-700",
  },
];

function ChannelIcon({ name }: { name: string }) {
  switch (name) {
    case "play":
      return <PlaySquare className="size-6 text-white" />;
    case "book":
      return <BookOpen className="size-6 text-white" />;
    case "rocket":
      return <Rocket className="size-6 text-white" />;
    case "radio":
      return <Radio className="size-6 text-white" />;
    case "compass":
      return <Compass className="size-6 text-white" />;
    case "search":
      return <Search className="size-6 text-white" />;
    default:
      return <Globe className="size-6 text-white" />;
  }
}

export function HomeScreen({
  engineName,
  history,
  bookmarksCount = 0,
  onOpen,
  onBookmarks,
  onHistory,
  onSettings,
}: {
  engineName: string;
  history: HistoryEntry[];
  bookmarksCount?: number;
  onOpen: (url: string) => void;
  onBookmarks: () => void;
  onHistory: () => void;
  onSettings: () => void;
}) {
  const [draft, setDraft] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string>("All");
  const recent = history.slice(0, 10);

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

  const filteredChannels =
    selectedCategory === "All"
      ? FEATURED_CHANNELS
      : FEATURED_CHANNELS.filter((c) => c.category === selectedCategory);

  return (
    <Screen>
      <div className="px-5 py-6 md:px-12 md:py-8 max-w-6xl mx-auto flex flex-col gap-8">
        {/* Navigation & Status Header */}
        <header className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <img
              src="/xtream-logo.png"
              alt="Xtream"
              className="h-14 sm:h-20 w-auto object-contain drop-shadow-[0_4px_16px_rgba(62,203,255,0.25)]"
            />
            <div className="hidden sm:block">
              <span className="text-xs font-bold tracking-widest text-amber uppercase">
                Living-Room & Mobile Web
              </span>
              <p className="text-xs text-muted">Direct video streaming & tracker-free browsing</p>
            </div>
          </div>

          {/* Quick Navigation Action Pills */}
          <nav className="flex items-center gap-2" aria-label="Main Navigation">
            <TvButton
              onClick={onBookmarks}
              variant="glass"
              className="flex items-center gap-2 px-3.5 py-2.5 rounded-xl text-xs font-semibold"
            >
              <Bookmark className="size-4 text-amber" />
              <span>Bookmarks</span>
              {bookmarksCount > 0 && (
                <span className="rounded-full bg-amber/20 text-amber text-[10px] font-bold px-1.5 py-0.2">
                  {bookmarksCount}
                </span>
              )}
            </TvButton>

            <TvButton
              onClick={onHistory}
              variant="glass"
              className="flex items-center gap-2 px-3.5 py-2.5 rounded-xl text-xs font-semibold"
            >
              <HistoryIcon className="size-4 text-magenta" />
              <span>History</span>
              {history.length > 0 && (
                <span className="rounded-full bg-magenta/20 text-magenta text-[10px] font-bold px-1.5 py-0.2">
                  {history.length}
                </span>
              )}
            </TvButton>

            <TvButton
              onClick={onSettings}
              variant="glass"
              className="flex items-center gap-2 px-3.5 py-2.5 rounded-xl text-xs font-semibold"
              title="Settings"
            >
              <SettingsIcon className="size-4 text-muted group-hover:text-fg" />
              <span className="hidden sm:inline">Settings</span>
            </TvButton>
          </nav>
        </header>

        {/* Hero Search & URL Input Capsule */}
        <section className="relative">
          <div className="absolute -inset-1 rounded-3xl bg-gradient-to-r from-amber/30 via-magenta/20 to-blue-600/30 blur-xl opacity-50" />
          <form
            onSubmit={(e) => {
              e.preventDefault();
              submit();
            }}
            className="relative flex items-center gap-2 rounded-2xl glass-panel p-2 shadow-2xl border border-line"
          >
            <div className="flex items-center gap-2 pl-3 text-muted">
              <Search className="size-5 text-amber" />
              <span className="hidden sm:inline-block rounded-md bg-surface-2 px-2 py-0.5 text-[11px] font-mono text-muted uppercase">
                {engineName}
              </span>
            </div>

            <label htmlFor="home-address" className="sr-only">
              Search or enter address
            </label>
            <input
              id="home-address"
              value={draft}
              onChange={(e) => {
                setDraft(e.target.value);
                setNotice(null);
              }}
              placeholder={`Search ${engineName} or enter https:// address...`}
              enterKeyHint="go"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              className="w-full bg-transparent px-3 py-3.5 text-base sm:text-lg text-fg outline-none placeholder:text-muted/60"
            />

            {draft && (
              <button
                type="button"
                data-tv=""
                onClick={() => setDraft("")}
                className="p-2 text-muted hover:text-fg transition"
                aria-label="Clear input"
              >
                <X className="size-4" />
              </button>
            )}

            <TvButton
              primary
              onClick={submit}
              className="shrink-0 px-6 sm:px-8 py-3.5 rounded-xl font-bold text-sm tracking-wide"
            >
              GO
            </TvButton>
          </form>

          {notice && (
            <p className="mt-2.5 px-3 text-xs font-semibold text-danger animate-in fade-in">
              {notice}
            </p>
          )}

          {/* Quick Trending Suggestions Bar */}
          <div className="mt-3 flex flex-wrap items-center gap-2 px-1">
            <span className="text-[11px] font-bold text-muted uppercase flex items-center gap-1">
              <Flame className="size-3.5 text-amber" /> Quick:
            </span>
            {[
              { label: "YouTube", url: "https://www.youtube.com" },
              { label: "Twitch", url: "https://www.twitch.tv" },
              { label: "Wikipedia", url: "https://www.wikipedia.org" },
              { label: "Internet Archive", url: "https://archive.org" },
              { label: "Reddit", url: "https://www.reddit.com" },
              { label: "NASA", url: "https://www.nasa.gov" },
            ].map((shortcut) => (
              <button
                key={shortcut.label}
                type="button"
                data-tv=""
                onClick={() => onOpen(shortcut.url)}
                className="rounded-lg bg-surface/60 hover:bg-surface-2 border border-line/50 px-2.5 py-1 text-xs text-muted hover:text-fg transition"
              >
                {shortcut.label}
              </button>
            ))}
          </div>
        </section>

        {/* Recent Activity Carousel (if any) */}
        {recent.length > 0 && (
          <section className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <SectionLabel badge={recent.length} icon={<HistoryIcon className="size-4" />}>
                Recent History
              </SectionLabel>
              <TvButton
                onClick={onHistory}
                variant="ghost"
                className="text-xs text-amber font-semibold hover:underline"
              >
                View all
              </TvButton>
            </div>

            <div className="flex gap-3 overflow-x-auto pb-2 pt-1 scroll-smooth">
              {recent.map((item) => (
                <TvButton
                  key={item.id}
                  onClick={() => onOpen(item.url)}
                  className="flex min-h-[76px] w-48 sm:w-56 shrink-0 flex-col justify-between rounded-xl glass-card p-3 text-left transition hover:scale-[1.02]"
                >
                  <div className="min-w-0 w-full">
                    <span className="block truncate text-sm font-semibold text-fg">
                      {item.title}
                    </span>
                    <span className="block truncate text-xs text-amber/80 font-mono mt-0.5">
                      {hostOf(item.url)}
                    </span>
                  </div>
                  <span className="text-[10px] text-muted self-end mt-1">
                    {ago(item.visitedAt)}
                  </span>
                </TvButton>
              ))}
            </div>
          </section>
        )}

        {/* Featured Streaming & Web Hubs */}
        <section className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <SectionLabel icon={<Sparkles className="size-4" />}>
              Suggested Streaming & Portals
            </SectionLabel>

            {/* Category filter pills */}
            <div className="flex items-center gap-1.5 overflow-x-auto p-1 bg-surface-2/60 rounded-xl border border-line/50">
              {["All", "Streaming", "Knowledge", "Space", "Community"].map((cat) => (
                <button
                  key={cat}
                  type="button"
                  data-tv=""
                  onClick={() => setSelectedCategory(cat)}
                  className={clsx(
                    "px-3 py-1 rounded-lg text-xs font-semibold transition",
                    selectedCategory === cat
                      ? "bg-amber text-amber-ink font-bold shadow-sm"
                      : "text-muted hover:text-fg hover:bg-surface/50",
                  )}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
            {filteredChannels.map((channel) => (
              <TvButton
                key={channel.url}
                onClick={() => onOpen(channel.url)}
                className="group flex flex-col justify-between rounded-2xl glass-card p-4 text-left border border-line/60 transition-all duration-200 hover:border-amber/60 hover:shadow-xl"
              >
                <div>
                  <div className="flex items-center justify-between gap-2 mb-3">
                    <div
                      className={clsx(
                        "size-11 rounded-xl flex items-center justify-center bg-gradient-to-br shadow-md",
                        channel.gradient,
                      )}
                    >
                      <ChannelIcon name={channel.iconName} />
                    </div>
                    <span className="rounded-full bg-surface-2/90 border border-line/60 px-2 py-0.5 text-[10px] font-semibold text-muted uppercase">
                      {channel.category}
                    </span>
                  </div>

                  <h3 className="text-base font-bold text-fg group-hover:text-amber transition">
                    {channel.title}
                  </h3>
                  <p className="mt-1 text-xs text-muted line-clamp-2 leading-relaxed">
                    {channel.description}
                  </p>
                </div>

                <div className="mt-4 pt-2.5 border-t border-line/40 flex items-center justify-between text-xs">
                  <span className="text-[11px] font-mono text-muted/70 truncate max-w-[150px]">
                    {hostOf(channel.url)}
                  </span>
                  <span className="text-amber text-xs font-semibold flex items-center gap-1 group-hover:translate-x-0.5 transition">
                    Launch <ExternalLink className="size-3" />
                  </span>
                </div>
              </TvButton>
            ))}
          </div>
        </section>

        {/* Feature Badges Footer */}
        <section className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-4 border-t border-line/40">
          <div className="flex items-center gap-2.5 rounded-xl bg-surface/50 border border-line/40 p-3">
            <ShieldCheck className="size-5 text-emerald-400 shrink-0" />
            <div className="min-w-0">
              <p className="text-xs font-bold text-fg">Ad-Shield Active</p>
              <p className="text-[10px] text-muted truncate">Popup & video ad filtering</p>
            </div>
          </div>

          <div className="flex items-center gap-2.5 rounded-xl bg-surface/50 border border-line/40 p-3">
            <Play className="size-5 text-amber shrink-0" />
            <div className="min-w-0">
              <p className="text-xs font-bold text-fg">Media Sniffer</p>
              <p className="text-[10px] text-muted truncate">MP4, HLS, & MKV detection</p>
            </div>
          </div>

          <div className="flex items-center gap-2.5 rounded-xl bg-surface/50 border border-line/40 p-3">
            <Tv className="size-5 text-magenta shrink-0" />
            <div className="min-w-0">
              <p className="text-xs font-bold text-fg">D-Pad Navigation</p>
              <p className="text-[10px] text-muted truncate">Optimized for TV remotes</p>
            </div>
          </div>

          <div className="flex items-center gap-2.5 rounded-xl bg-surface/50 border border-line/40 p-3">
            <Zap className="size-5 text-yellow-400 shrink-0" />
            <div className="min-w-0">
              <p className="text-xs font-bold text-fg">Turbo WebView</p>
              <p className="text-[10px] text-muted truncate">Hardware accelerated</p>
            </div>
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
      <div className="px-5 py-6 md:px-12 md:py-8 max-w-4xl mx-auto flex flex-col gap-6">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <BackButton onClick={onBack} primary={hits === null || hits.length === 0} />
            <div className="min-w-0">
              <p className="text-xs font-bold tracking-widest text-amber uppercase">Search Query</p>
              <h1 className="truncate font-display text-2xl sm:text-3xl text-fg">{query}</h1>
            </div>
          </div>

          <TvButton
            onClick={() => onOpen(searchPageUrl(engine, query))}
            variant="glass"
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold shrink-0"
          >
            <span>Open on {engineLabel(engine)}</span>
            <ExternalLink className="size-3.5 text-amber" />
          </TvButton>
        </div>

        {hits === null ? (
          <div className="flex flex-col items-center justify-center py-20 gap-4" aria-live="polite">
            <div className="size-12 rounded-full border-2 border-line border-t-amber animate-spin" />
            <p className="text-sm font-semibold tracking-wider text-muted uppercase">Searching DuckDuckGo…</p>
          </div>
        ) : error ? (
          <div className="rounded-2xl glass-card p-6 border border-danger/40 max-w-lg">
            <h2 className="font-display text-2xl text-fg">Search did not finish</h2>
            <p className="mt-2 text-sm text-muted">{error}</p>
            <TvButton
              primary
              onClick={() => setAttempt((n) => n + 1)}
              className="mt-5 px-6 py-2.5 rounded-xl text-sm font-semibold"
            >
              Retry
            </TvButton>
          </div>
        ) : hits.length === 0 ? (
          <div className="rounded-2xl glass-card p-8 text-center max-w-md mx-auto">
            <Search className="size-10 text-muted mx-auto mb-3" />
            <p className="text-lg font-semibold text-fg">No results found</p>
            <p className="text-sm text-muted mt-1">Try another search or open directly on {engineLabel(engine)}.</p>
            <TvButton
              primary
              onClick={() => onOpen(searchPageUrl(engine, query))}
              className="mt-5 px-6 py-2.5 rounded-xl text-sm font-semibold"
            >
              Search on {engineLabel(engine)}
            </TvButton>
          </div>
        ) : (
          <div className="grid gap-3">
            {hits.map((hit, index) => (
              <TvButton
                key={hit.url}
                data-result={index === 0 ? "" : undefined}
                onClick={() => onOpen(hit.url)}
                className="flex w-full flex-col gap-1.5 rounded-xl glass-card p-4 text-left hover:border-amber/50 transition"
              >
                <div className="flex items-center gap-2">
                  <Globe className="size-4 text-amber shrink-0" />
                  <span className="text-xs font-mono text-amber">{hit.host}</span>
                </div>
                <h3 className="text-base sm:text-lg font-semibold text-fg">{hit.title}</h3>
                {hit.snippet ? (
                  <p className="text-xs sm:text-sm text-muted leading-relaxed line-clamp-2">{hit.snippet}</p>
                ) : null}
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
    <div className="flex h-full flex-col bg-bg text-fg overflow-hidden">
      {/* Sleek Browser Omnibar */}
      <header className="shrink-0 border-b border-line/60 bg-surface/80 backdrop-blur-xl px-3 py-2.5 sm:px-6">
        <div className="flex items-center gap-2">
          <BackButton onClick={onBack} primary />

          <TvButton
            disabled={!canForward}
            onClick={onForward}
            aria-label="Forward"
            className="grid size-11 place-items-center rounded-xl bg-surface text-fg disabled:text-muted/40 disabled:pointer-events-none"
          >
            <RotateCw className="size-4 -scale-x-100" />
          </TvButton>

          <TvButton
            onClick={() => setAttempt((n) => n + 1)}
            aria-label="Reload"
            className="grid size-11 place-items-center rounded-xl bg-surface text-fg"
          >
            <RefreshCw className={clsx("size-4", loading && "animate-spin text-amber")} />
          </TvButton>

          <TvButton
            onClick={onHome}
            aria-label="Home"
            className="grid size-11 place-items-center rounded-xl bg-surface text-fg"
          >
            <House className="size-4" />
          </TvButton>

          {/* Omnibar URL Pill */}
          <div className="min-w-0 flex-1 flex items-center gap-2 rounded-xl bg-surface-2/80 px-3 py-2 border border-line/50">
            <Lock className="size-3.5 text-emerald-400 shrink-0" />
            <span className="truncate text-xs font-mono text-muted">{page?.url ?? url}</span>
          </div>

          {/* Bookmark Star Button */}
          <TvButton
            aria-label={bookmarked ? "Remove bookmark" : "Save bookmark"}
            aria-pressed={bookmarked}
            onClick={() => onToggleBookmark(title)}
            className={clsx(
              "grid size-11 shrink-0 place-items-center rounded-xl transition",
              bookmarked
                ? "bg-amber text-amber-ink shadow-[0_0_12px_rgba(62,203,255,0.4)]"
                : "bg-surface text-fg hover:text-amber",
            )}
          >
            <Star className="size-4" fill={bookmarked ? "currentColor" : "none"} />
          </TvButton>
        </div>

        {/* Loading Progress Bar */}
        <div className="mt-2 h-1 overflow-hidden rounded-full bg-surface-2/60">
          {loading && (
            <div className="h-full w-2/5 rounded-full bg-gradient-to-r from-amber to-magenta animate-pulse" />
          )}
        </div>
      </header>

      {/* Main Content: Live Web Frame OR Clean Reading View */}
      {live && page && !page.frameBlocked ? (
        <iframe
          title={page.title || "Live page"}
          src={page.url}
          className="min-h-0 w-full flex-1 bg-white"
          sandbox={
            javascriptEnabled
              ? "allow-scripts allow-forms allow-popups allow-presentation allow-same-origin"
              : "allow-forms"
          }
          allow="autoplay; fullscreen; encrypted-media"
          referrerPolicy="no-referrer-when-downgrade"
        />
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-6 md:px-12 md:py-10">
          {loading ? (
            <div className="flex h-full min-h-[50dvh] flex-col items-center justify-center gap-4" aria-live="polite">
              <div className="relative grid size-16 place-items-center">
                <span className="absolute inset-0 rounded-full border border-line" />
                <span className="xtream-spin absolute inset-0 rounded-full border-2 border-transparent border-t-amber border-r-magenta" />
                <span className="size-2 rounded-full bg-amber" />
              </div>
              <p className="text-xs font-bold tracking-widest text-muted uppercase">Rendering page</p>
            </div>
          ) : failure ? (
            <div className="max-w-xl mx-auto rounded-2xl glass-card p-8 border border-danger/40 mt-10">
              <p className="text-xs font-bold tracking-widest text-danger uppercase">Connection Error</p>
              <h1 className="mt-2 font-display text-3xl text-fg">{failure.message}</h1>
              <p className="mt-3 text-sm text-muted">Check the URL, verify your internet, or retry.</p>
              <div className="mt-6 flex flex-wrap gap-3">
                <TvButton
                  primary
                  onClick={() => setAttempt((n) => n + 1)}
                  className="px-6 py-2.5 rounded-xl text-sm font-semibold"
                >
                  Retry
                </TvButton>
                <TvButton onClick={onHome} className="px-6 py-2.5 rounded-xl text-sm font-semibold">
                  Home
                </TvButton>
              </div>
            </div>
          ) : page ? (
            <article className="mx-auto max-w-3xl flex flex-col gap-6">
              {/* Reading Header & Controls */}
              <div className="flex flex-wrap items-center justify-between gap-3 pb-4 border-b border-line/40">
                <div className="flex items-center gap-2">
                  {page.insecure ? (
                    <span className="rounded-lg bg-danger/20 border border-danger/40 px-2.5 py-1 text-xs text-danger font-semibold">
                      Not secure
                    </span>
                  ) : (
                    <span className="rounded-lg bg-emerald-950/40 border border-emerald-800/40 px-2.5 py-1 text-xs text-emerald-400 font-semibold flex items-center gap-1">
                      <Lock className="size-3" /> Secure
                    </span>
                  )}
                  <span className="rounded-lg bg-surface-2 px-2.5 py-1 text-xs text-muted font-mono">
                    {hostOf(page.url)}
                  </span>
                </div>

                {!page.frameBlocked && (
                  <TvButton
                    onClick={() => setLive(true)}
                    variant="glass"
                    className="flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold"
                  >
                    <span>Switch to Live Web</span>
                    <ExternalLink className="size-3.5 text-amber" />
                  </TvButton>
                )}
              </div>

              <div>
                <h1 className="font-display text-3xl sm:text-5xl text-fg font-extrabold tracking-tight">
                  {page.title}
                </h1>
                <p className="mt-2 text-xs font-mono text-amber">{page.url}</p>
              </div>

              {/* Text Blocks */}
              <div className="grid gap-4 mt-2">
                {page.blocks.map((block, index) =>
                  block.kind === "h" ? (
                    <h2
                      key={`${block.text}-${index}`}
                      className="font-display text-xl sm:text-2xl text-fg font-bold mt-4"
                    >
                      {block.text}
                    </h2>
                  ) : (
                    <p
                      key={`${block.text}-${index}`}
                      className="text-base sm:text-lg leading-relaxed text-fg/90"
                    >
                      {block.kind === "li" ? `• ${block.text}` : block.text}
                    </p>
                  ),
                )}
              </div>

              {/* Embedded Links Section */}
              {page.links.length > 0 && (
                <section className="mt-8 pt-6 border-t border-line/40">
                  <SectionLabel icon={<Globe className="size-4" />} badge={page.links.length}>
                    Page Links
                  </SectionLabel>
                  <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    {page.links.map((link) => (
                      <TvButton
                        key={link.href}
                        onClick={() => onOpen(link.href)}
                        className="flex flex-col justify-between rounded-xl glass-card p-3 text-left hover:border-amber/50"
                      >
                        <span className="block text-sm font-semibold text-fg line-clamp-1">{link.text}</span>
                        <span className="block truncate text-xs text-muted/80 font-mono mt-1">
                          {hostOf(link.href)}
                        </span>
                      </TvButton>
                    ))}
                  </div>
                </section>
              )}
            </article>
          ) : null}
        </div>
      )}

      {live && (
        <footer className="shrink-0 border-t border-line/60 bg-surface/90 px-4 py-2.5 flex items-center justify-between">
          <p className="text-xs text-muted">Viewing live web frame</p>
          <TvButton
            onClick={() => setLive(false)}
            variant="glass"
            className="px-4 py-2 rounded-xl text-xs font-semibold"
          >
            Back to Reading View
          </TvButton>
        </footer>
      )}
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
  onClearAll,
}: {
  title: string;
  empty: string;
  rows: { id: string; title: string; url: string; meta?: string }[];
  onBack: () => void;
  onOpen: (url: string) => void;
  onRemove: (id: string) => void;
  onClearAll?: () => void;
}) {
  const [filter, setFilter] = useState("");
  const filtered = rows.filter(
    (r) =>
      r.title.toLowerCase().includes(filter.toLowerCase()) ||
      r.url.toLowerCase().includes(filter.toLowerCase()),
  );

  return (
    <Screen>
      <div className="px-5 py-6 md:px-12 md:py-8 max-w-4xl mx-auto flex flex-col gap-6">
        <header className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <BackButton onClick={onBack} primary />
            <div>
              <h1 className="font-display text-2xl sm:text-3xl text-fg font-bold">{title}</h1>
              <p className="text-xs text-muted">
                {rows.length} {rows.length === 1 ? "entry" : "entries"} saved on this device
              </p>
            </div>
          </div>

          {rows.length > 0 && onClearAll && (
            <TvButton
              variant="danger"
              onClick={onClearAll}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold"
            >
              <Trash2 className="size-3.5" />
              <span>Clear All</span>
            </TvButton>
          )}
        </header>

        {rows.length > 4 && (
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted" />
            <input
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder={`Filter ${title.toLowerCase()}...`}
              className="w-full rounded-xl glass-panel pl-9 pr-4 py-2.5 text-sm text-fg outline-none placeholder:text-muted/60"
            />
          </div>
        )}

        {rows.length === 0 ? (
          <div className="rounded-2xl glass-card p-10 text-center max-w-md mx-auto my-10">
            <Bookmark className="size-10 text-muted mx-auto mb-3" />
            <p className="text-lg font-semibold text-fg">{empty}</p>
            <p className="text-xs text-muted mt-1">Open pages or videos to add items to your collection.</p>
          </div>
        ) : filtered.length === 0 ? (
          <p className="text-center text-sm text-muted py-8">No results matching "{filter}".</p>
        ) : (
          <div className="grid gap-2.5">
            {filtered.map((row) => (
              <div
                key={row.id}
                className="flex items-center justify-between gap-3 rounded-xl glass-card p-3.5 hover:border-amber/50 transition group"
              >
                <button
                  type="button"
                  data-tv=""
                  onClick={() => onOpen(row.url)}
                  className="min-w-0 flex-1 text-left select-none"
                >
                  <span className="block truncate text-base font-semibold text-fg group-hover:text-amber transition">
                    {row.title}
                  </span>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="truncate text-xs font-mono text-muted">{hostOf(row.url)}</span>
                    {row.meta && <span className="text-[10px] text-muted/70">• {row.meta}</span>}
                  </div>
                </button>

                <div className="flex items-center gap-1.5 shrink-0">
                  <TvButton
                    onClick={() => onOpen(row.url)}
                    variant="glass"
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold"
                  >
                    Open
                  </TvButton>
                  <button
                    type="button"
                    data-tv=""
                    onClick={() => onRemove(row.id)}
                    aria-label={`Remove ${row.title}`}
                    className="p-2 rounded-lg text-muted hover:text-danger hover:bg-danger/15 transition"
                  >
                    <Trash2 className="size-4" />
                  </button>
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
    setNote(kind === "cache" ? "Readable-page cache cleared." : "Data cleared successfully.");
  }

  return (
    <Screen>
      <div className="px-5 py-6 md:px-12 md:py-8 max-w-4xl mx-auto flex flex-col gap-6">
        <header className="flex items-center gap-3">
          <BackButton onClick={onBack} primary />
          <div>
            <h1 className="font-display text-2xl sm:text-3xl text-fg font-bold">Settings</h1>
            <p className="text-xs text-muted">Configure browser mode, engines, and privacy</p>
          </div>
        </header>

        {note && (
          <div className="rounded-xl bg-amber/15 border border-amber/40 px-4 py-2.5 text-xs text-amber font-semibold animate-in fade-in">
            {note}
          </div>
        )}

        {/* Section 1: Engine & Mode */}
        <section className="rounded-2xl glass-card p-5 flex flex-col gap-5">
          <SectionLabel icon={<Globe className="size-4" />}>Browser & Engine</SectionLabel>

          {/* Homepage */}
          <TvButton
            onClick={onEditHomepage}
            className="flex items-center justify-between gap-3 rounded-xl bg-surface/80 p-3.5 text-left border border-line/50 hover:border-amber/50"
          >
            <div>
              <span className="block text-sm font-semibold text-fg">Default Homepage</span>
              <span className="block truncate text-xs text-muted font-mono mt-0.5">
                {settings.homepage || "Xtream Living-Room Home"}
              </span>
            </div>
            <span className="text-xs font-bold text-amber">Edit</span>
          </TvButton>

          {/* Search Engine Selection */}
          <div>
            <label className="block text-xs font-semibold text-muted uppercase mb-2">Search Engine</label>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              {(["google", "duckduckgo", "bing"] as const).map((engine) => (
                <button
                  key={engine}
                  type="button"
                  data-tv=""
                  onClick={() => onChange({ searchEngine: engine })}
                  className={clsx(
                    "flex items-center justify-center py-2.5 px-3 rounded-xl text-xs font-bold transition border",
                    settings.searchEngine === engine
                      ? "bg-amber text-amber-ink border-amber shadow-sm"
                      : "bg-surface/60 text-muted hover:text-fg border-line/60 hover:bg-surface",
                  )}
                >
                  {engineLabel(engine)}
                </button>
              ))}
            </div>
          </div>

          {/* Mode Switcher */}
          <div>
            <label className="block text-xs font-semibold text-muted uppercase mb-2">
              Device User-Agent Mode
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <button
                type="button"
                data-tv=""
                onClick={() => onChange({ browserMode: "standard" })}
                className={clsx(
                  "flex items-center justify-center gap-2 py-3 px-3 rounded-xl text-xs font-bold transition border",
                  settings.browserMode === "standard"
                    ? "bg-amber text-amber-ink border-amber shadow-sm"
                    : "bg-surface/60 text-muted hover:text-fg border-line/60 hover:bg-surface",
                )}
              >
                <Tv className="size-4" />
                <span>Standard (Android TV)</span>
              </button>

              <button
                type="button"
                data-tv=""
                onClick={() => onChange({ browserMode: "desktop" })}
                className={clsx(
                  "flex items-center justify-center gap-2 py-3 px-3 rounded-xl text-xs font-bold transition border",
                  settings.browserMode === "desktop"
                    ? "bg-amber text-amber-ink border-amber shadow-sm"
                    : "bg-surface/60 text-muted hover:text-fg border-line/60 hover:bg-surface",
                )}
              >
                <Laptop className="size-4" />
                <span>Desktop Chrome</span>
              </button>
            </div>
          </div>
        </section>

        {/* Section 2: Display & Device Presentation */}
        <section className="rounded-2xl glass-card p-5 flex flex-col gap-4">
          <SectionLabel icon={<Tv className="size-4" />}>Display & Viewport</SectionLabel>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
            {[
              { id: "tv", label: "16:9 TV Mode", icon: Tv },
              { id: "mobile", label: "Mobile Phone", icon: Globe },
              { id: "auto", label: "Auto Responsive", icon: Monitor },
            ].map((item) => (
              <button
                key={item.id}
                type="button"
                data-tv=""
                onClick={() => onChange({ deviceMode: item.id as any })}
                className={clsx(
                  "flex items-center justify-center gap-2 py-3 px-3 rounded-xl text-xs font-bold transition border",
                  settings.deviceMode === item.id
                    ? "bg-amber text-amber-ink border-amber shadow-sm"
                    : "bg-surface/60 text-muted hover:text-fg border-line/60 hover:bg-surface",
                )}
              >
                <item.icon className="size-4" />
                <span>{item.label}</span>
              </button>
            ))}
          </div>
        </section>

        {/* Section 3: Privacy & Security */}
        <section className="rounded-2xl glass-card p-5 flex flex-col gap-4">
          <SectionLabel icon={<Shield className="size-4" />}>Privacy & Content Shield</SectionLabel>

          <TvButton
            onClick={() => onChange({ javascriptEnabled: !settings.javascriptEnabled })}
            className="flex items-center justify-between rounded-xl bg-surface/80 p-3.5 border border-line/50 hover:border-amber/50"
          >
            <div>
              <span className="block text-sm font-semibold text-fg">JavaScript on Live Pages</span>
              <span className="block text-xs text-muted mt-0.5">Toggle script execution in embedded frames</span>
            </div>
            <span
              className={clsx(
                "px-2.5 py-1 rounded-lg text-xs font-bold",
                settings.javascriptEnabled
                  ? "bg-emerald-950/60 text-emerald-400 border border-emerald-800/40"
                  : "bg-surface-2 text-muted",
              )}
            >
              {settings.javascriptEnabled ? "Enabled" : "Disabled"}
            </span>
          </TvButton>

          <TvButton
            onClick={() => onChange({ hideControlsWhilePlaying: !settings.hideControlsWhilePlaying })}
            className="flex items-center justify-between rounded-xl bg-surface/80 p-3.5 border border-line/50 hover:border-amber/50"
          >
            <div>
              <span className="block text-sm font-semibold text-fg">Auto-Hide Player Controls</span>
              <span className="block text-xs text-muted mt-0.5">Fade controls during video playback</span>
            </div>
            <span
              className={clsx(
                "px-2.5 py-1 rounded-lg text-xs font-bold",
                settings.hideControlsWhilePlaying
                  ? "bg-amber/20 text-amber border border-amber/40"
                  : "bg-surface-2 text-muted",
              )}
            >
              {settings.hideControlsWhilePlaying ? "On" : "Off"}
            </span>
          </TvButton>

          {/* Storage & Clear Actions */}
          <div className="pt-2">
            <p className="text-xs font-bold text-muted uppercase mb-2">Storage & Data</p>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              <ClearButton
                label="Clear History"
                pending={pending === "history"}
                onClick={() => confirm("history")}
              />
              <ClearButton
                label="Clear Bookmarks"
                pending={pending === "bookmarks"}
                onClick={() => confirm("bookmarks")}
              />
              <ClearButton
                label="Clear Cache"
                pending={pending === "cache"}
                onClick={() => confirm("cache")}
              />
              <ClearButton
                label="Reset All"
                pending={pending === "all"}
                onClick={() => confirm("all")}
              />
            </div>
          </div>
        </section>

        {/* Section 4: About */}
        <section className="rounded-2xl glass-card p-5 text-xs text-muted flex flex-col gap-2">
          <div className="flex items-center gap-3 text-fg font-bold text-sm">
            <img src="/xtream-icon.png" alt="" className="size-8 rounded-xl shadow-md" />
            <span>Xtream Browser {APP_VERSION}</span>
          </div>
          <p>Full HTML5 video stream extraction (MP4, HLS, WebM, MKV). Protected DRM is refused by design.</p>
          <p className="text-[11px] font-mono text-muted/80">
            TV Sideloadable Build: app.xtream.tv • Mobile Sideloadable Build: app.xtream.mobile
          </p>
        </section>
      </div>
    </Screen>
  );
}

function ClearButton({
  label,
  pending,
  onClick,
}: {
  label: string;
  pending: boolean;
  onClick: () => void;
}) {
  return (
    <TvButton
      onClick={onClick}
      variant={pending ? "danger" : "surface"}
      className="py-2.5 px-3 rounded-xl text-xs font-semibold text-center"
    >
      {pending ? "Confirm?" : label}
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
      <div className="flex min-h-full flex-col items-center justify-center p-6 text-center max-w-md mx-auto">
        <div className="size-16 rounded-2xl bg-danger/15 border border-danger/40 flex items-center justify-center text-danger mb-4">
          <ShieldAlert className="size-8" />
        </div>
        <h1 className="font-display text-2xl sm:text-3xl font-bold text-fg">{title}</h1>
        <p className="mt-2 text-sm text-muted leading-relaxed">{message}</p>
        <TvButton
          primary
          onClick={onHome}
          className="mt-6 px-8 py-3 rounded-xl font-bold text-sm tracking-wide"
        >
          Return to Home
        </TvButton>
      </div>
    </Screen>
  );
}
