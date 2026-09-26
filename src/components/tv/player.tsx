import { useEffect, useRef, useState } from "react";
import {
  FastForward,
  Maximize2,
  Minimize2,
  Pause,
  Play,
  RotateCcw,
  RotateCw,
  Star,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";
import { TvButton } from "@/components/tv/ui";
import type { MediaFormat } from "@/lib/tv/types";

type Props = {
  url: string;
  title: string;
  format: MediaFormat;
  bookmarked: boolean;
  hideControls: boolean;
  onBack: () => void;
  onToggleBookmark: () => void;
};

function clock(total: number): string {
  if (!Number.isFinite(total) || total < 0) return "0:00";
  const s = Math.floor(total);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const ss = String(sec).padStart(2, "0");
  if (h > 0) return `${h}:${String(m).padStart(2, "0")}:${ss}`;
  return `${m}:${ss}`;
}

export function Player({
  url,
  title,
  format,
  bookmarked,
  hideControls,
  onBack,
  onToggleBookmark,
}: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const seekRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [fatal, setFatal] = useState<string | null>(format === "dash" ? "dash" : null);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [muted, setMuted] = useState(false);
  const [playbackRate, setPlaybackRate] = useState(1);
  const [buffering, setBuffering] = useState(false);
  const [chromeOn, setChromeOn] = useState(true);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    setFatal(format === "dash" ? "dash" : null);
    setTime(0);
    setDuration(0);
    setPlaying(false);
    setMuted(false);
    setPlaybackRate(1);
    setBuffering(false);
    setChromeOn(true);
    setAttempt(0);
  }, [url, format]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || format === "dash") return;
    let dead = false;
    let hls: { destroy: () => void } | null = null;

    const onError = () => {
      if (!dead) setFatal("play");
    };
    const onTime = () => setTime(video.currentTime || 0);
    const onMeta = () => setDuration(video.duration || 0);
    const onPlay = () => {
      setPlaying(true);
      setBuffering(false);
    };
    const onPause = () => setPlaying(false);
    const onWaiting = () => setBuffering(true);
    const onCanPlay = () => setBuffering(false);
    const onVolume = () => setMuted(video.muted);

    video.addEventListener("error", onError);
    video.addEventListener("timeupdate", onTime);
    video.addEventListener("durationchange", onMeta);
    video.addEventListener("loadedmetadata", onMeta);
    video.addEventListener("play", onPlay);
    video.addEventListener("pause", onPause);
    video.addEventListener("waiting", onWaiting);
    video.addEventListener("stalled", onWaiting);
    video.addEventListener("canplay", onCanPlay);
    video.addEventListener("seeked", onCanPlay);
    video.addEventListener("volumechange", onVolume);

    const start = async () => {
      const nativeHls = video.canPlayType("application/vnd.apple.mpegurl");
      if (format === "hls" && nativeHls === "") {
        const { default: Hls } = await import("hls.js");
        if (dead) return;
        if (!Hls.isSupported()) {
          setFatal("hls");
          return;
        }
        const instance = new Hls({ enableWorker: false, maxBufferLength: 30 });
        hls = instance;
        instance.loadSource(url);
        instance.attachMedia(video);
        instance.on(Hls.Events.ERROR, (_event, data) => {
          if (dead || !data.fatal) return;
          if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
            setBuffering(true);
            instance.startLoad();
            return;
          }
          if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
            instance.recoverMediaError();
            return;
          }
          setFatal("play");
        });
        instance.on(Hls.Events.MANIFEST_PARSED, () => {
          if (!dead) void video.play().catch(() => setPlaying(false));
        });
        return;
      }
      video.src = url;
      void video.play().catch(() => setPlaying(false));
    };
    void start();

    return () => {
      dead = true;
      video.removeEventListener("error", onError);
      video.removeEventListener("timeupdate", onTime);
      video.removeEventListener("durationchange", onMeta);
      video.removeEventListener("loadedmetadata", onMeta);
      video.removeEventListener("play", onPlay);
      video.removeEventListener("pause", onPause);
      video.removeEventListener("waiting", onWaiting);
      video.removeEventListener("stalled", onWaiting);
      video.removeEventListener("canplay", onCanPlay);
      video.removeEventListener("seeked", onCanPlay);
      video.removeEventListener("volumechange", onVolume);
      hls?.destroy();
      video.removeAttribute("src");
      video.load();
    };
  }, [url, format, attempt]);

  useEffect(() => {
    const el = seekRef.current;
    if (!el) return;
    const onNudge = (event: Event) => {
      const video = videoRef.current;
      const delta = (event as CustomEvent<number>).detail;
      if (!video || !Number.isFinite(video.duration)) return;
      video.currentTime = Math.min(video.duration, Math.max(0, video.currentTime + delta));
    };
    el.addEventListener("xtream-nudge", onNudge);
    return () => el.removeEventListener("xtream-nudge", onNudge);
  }, [chromeOn, fatal]);

  useEffect(() => {
    const onShow = () => {
      setChromeOn(true);
      window.requestAnimationFrame(() => {
        document.querySelector<HTMLElement>("[data-player-play]")?.focus();
      });
    };
    window.addEventListener("xtream-show-chrome", onShow);
    return () => window.removeEventListener("xtream-show-chrome", onShow);
  }, []);

  useEffect(() => {
    if (!hideControls || !playing || !chromeOn) return;
    const id = window.setTimeout(() => setChromeOn(false), 4500);
    return () => window.clearTimeout(id);
  }, [hideControls, playing, chromeOn]);

  useEffect(() => {
    const onFullscreenChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener("fullscreenchange", onFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", onFullscreenChange);
  }, []);

  function toggle() {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) void video.play().catch(() => setPlaying(false));
    else video.pause();
  }

  function toggleMute() {
    const video = videoRef.current;
    if (!video) return;
    video.muted = !video.muted;
    setMuted(video.muted);
  }

  function cycleSpeed() {
    const video = videoRef.current;
    if (!video) return;
    const rates = [1, 1.25, 1.5, 2, 0.75];
    const nextIdx = (rates.indexOf(playbackRate) + 1) % rates.length;
    const nextRate = rates[nextIdx];
    video.playbackRate = nextRate;
    setPlaybackRate(nextRate);
  }

  function toggleFullscreen() {
    if (!document.fullscreenElement) {
      void containerRef.current?.requestFullscreen?.();
    } else {
      void document.exitFullscreen?.();
    }
  }

  function handleSeekClick(e: React.MouseEvent<HTMLDivElement>) {
    const video = videoRef.current;
    const rect = e.currentTarget.getBoundingClientRect();
    if (!video || !Number.isFinite(video.duration) || rect.width === 0) return;
    const clickX = Math.max(0, Math.min(rect.width, e.clientX - rect.left));
    const targetPct = clickX / rect.width;
    video.currentTime = targetPct * video.duration;
  }

  const seekable = Number.isFinite(duration) && duration > 0;
  const pct = seekable ? Math.min(100, (time / duration) * 100) : 0;

  if (fatal) {
    const message =
      fatal === "dash"
        ? "This stream uses MPEG-DASH, which is not supported in this lightweight engine."
        : fatal === "hls"
          ? "This browser cannot decode that HLS manifest."
          : "This video cannot be played directly on this device.";
    return (
      <div className="flex h-full flex-col items-center justify-center gap-6 bg-bg-deep px-6 text-center">
        <p className="text-xs font-bold tracking-widest text-amber uppercase">Stream Refused</p>
        <h1 className="max-w-xl font-display text-3xl sm:text-4xl text-fg font-bold">{message}</h1>
        <p className="max-w-md text-sm text-muted">
          Xtream plays direct HTML5 and HLS video files. DRM protected and token-expired media stay closed.
        </p>
        <div className="flex flex-wrap items-center justify-center gap-3">
          {fatal !== "dash" ? (
            <TvButton
              primary
              onClick={() => {
                setFatal(null);
                setBuffering(true);
                setAttempt((n) => n + 1);
              }}
              className="px-8 py-3 rounded-xl font-bold text-sm"
            >
              Retry Stream
            </TvButton>
          ) : null}
          <TvButton
            onClick={onBack}
            className="px-8 py-3 rounded-xl font-bold text-sm"
          >
            Return to Browser
          </TvButton>
        </div>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      id={chromeOn ? undefined : "player-chrome-hidden"}
      className="relative h-full w-full bg-black select-none overflow-hidden group cursor-pointer"
      onMouseMove={() => setChromeOn(true)}
      onClick={() => setChromeOn((on) => !on)}
    >
      <video
        ref={videoRef}
        className="h-full w-full bg-black object-contain"
        playsInline
        preload="metadata"
      />
      {buffering ? (
        <div className="pointer-events-none absolute inset-x-0 top-4 z-20 flex justify-center">
          <span className="rounded-full bg-black/80 px-3 py-1 text-xs font-semibold text-fg">
            Buffering…
          </span>
        </div>
      ) : null}

      {/* Chrome Overlay */}
      {chromeOn ? (
        <div
          className="absolute inset-0 flex flex-col justify-between z-30 transition-opacity duration-300"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Top Bar with Title, Format Badge, Bookmark, and Close */}
          <div className="flex items-center justify-between gap-4 bg-gradient-to-b from-black/90 via-black/60 to-transparent p-5 sm:p-7">
            <div className="min-w-0 flex items-center gap-3">
              <TvButton
                onClick={onBack}
                aria-label="Back"
                className="grid size-11 shrink-0 place-items-center rounded-2xl glass-panel text-fg hover:border-amber/50"
              >
                <X className="size-5" />
              </TvButton>

              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-[10px] font-bold tracking-widest text-amber uppercase">
                    Cinema Player
                  </span>
                  <span className="rounded-full bg-amber/20 border border-amber/40 px-2 py-0.2 text-[10px] font-bold text-amber uppercase">
                    {format.toUpperCase()}
                  </span>
                </div>
                <h1 className="truncate font-display text-xl sm:text-2xl text-fg font-bold mt-0.5 max-w-xl">
                  {title}
                </h1>
              </div>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              <TvButton
                aria-label={bookmarked ? "Remove bookmark" : "Save bookmark"}
                aria-pressed={bookmarked}
                onClick={onToggleBookmark}
                className="grid size-11 place-items-center rounded-2xl glass-panel text-fg hover:border-amber/50"
              >
                <Star className="size-5" fill={bookmarked ? "currentColor" : "none"} />
              </TvButton>
            </div>
          </div>

          {/* Large Center Play / Pause Pulsing Button when Paused */}
          {!playing && (
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <button
                type="button"
                onClick={toggle}
                className="pointer-events-auto grid size-20 place-items-center rounded-full bg-gradient-to-br from-amber to-blue-600 text-amber-ink shadow-[0_0_40px_rgba(62,203,255,0.6)] hover:scale-105 active:scale-95 transition"
                aria-label="Play"
              >
                <Play className="size-9 fill-current ml-1" />
              </button>
            </div>
          )}

          {/* Bottom Player Controls & Scrub Bar */}
          <div className="bg-gradient-to-t from-black/95 via-black/75 to-transparent px-5 pt-8 pb-6 sm:px-8">
            {/* Interactive Scrub Bar */}
            <div
              ref={seekRef}
              data-tv=""
              data-tv-seek=""
              role="slider"
              tabIndex={0}
              aria-label="Seek"
              aria-valuemin={0}
              aria-valuemax={seekable ? Math.round(duration) : 0}
              aria-valuenow={Math.round(time)}
              onClick={handleSeekClick}
              className="tv group/bar relative flex h-7 items-center cursor-pointer"
            >
              <div className="relative h-2 w-full rounded-full bg-surface-2/80 overflow-hidden group-hover/bar:h-3 transition-all duration-150">
                <div
                  className="absolute inset-y-0 left-0 rounded-full bg-gradient-to-r from-amber to-magenta shadow-[0_0_12px_#3ecbff]"
                  style={{ width: `${pct}%` }}
                />
              </div>
              {/* Scrub Thumb Handle */}
              <div
                className="absolute size-4 rounded-full bg-white shadow-lg pointer-events-none -translate-x-1/2 group-hover/bar:scale-125 transition"
                style={{ left: `${pct}%` }}
              />
            </div>

            {/* Action Buttons Row */}
            <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-xs">
              <div className="flex items-center gap-2">
                <TvButton
                  data-player-play=""
                  onClick={toggle}
                  aria-label={playing ? "Pause" : "Play"}
                  className="grid size-11 place-items-center rounded-xl bg-amber text-amber-ink font-bold shadow-md"
                >
                  {playing ? <Pause className="size-5" /> : <Play className="size-5 fill-current" />}
                </TvButton>

                <TvButton
                  onClick={() => {
                    const video = videoRef.current;
                    if (video) video.currentTime = Math.max(0, video.currentTime - 10);
                  }}
                  aria-label="Rewind 10 seconds"
                  className="flex items-center gap-1.5 px-3 py-2.5 rounded-xl bg-surface/80 text-fg hover:border-amber/50 font-semibold"
                >
                  <RotateCcw className="size-4 text-amber" />
                  <span>10s</span>
                </TvButton>

                <TvButton
                  onClick={() => {
                    const video = videoRef.current;
                    if (!video || !Number.isFinite(video.duration)) return;
                    video.currentTime = Math.min(video.duration, video.currentTime + 10);
                  }}
                  aria-label="Forward 10 seconds"
                  className="flex items-center gap-1.5 px-3 py-2.5 rounded-xl bg-surface/80 text-fg hover:border-amber/50 font-semibold"
                >
                  <RotateCw className="size-4 text-amber" />
                  <span>10s</span>
                </TvButton>

                <TvButton
                  onClick={toggleMute}
                  aria-label={muted ? "Unmute" : "Mute"}
                  className="grid size-11 place-items-center rounded-xl bg-surface/80 text-fg hover:border-amber/50"
                >
                  {muted ? <VolumeX className="size-5 text-danger" /> : <Volume2 className="size-5 text-amber" />}
                </TvButton>

                {/* Speed toggle */}
                <TvButton
                  onClick={cycleSpeed}
                  aria-label="Speed"
                  className="flex items-center gap-1 px-3 py-2.5 rounded-xl bg-surface/80 text-fg hover:border-amber/50 font-semibold"
                >
                  <FastForward className="size-3.5 text-magenta" />
                  <span>{playbackRate}x</span>
                </TvButton>
              </div>

              {/* Time display & Fullscreen */}
              <div className="flex items-center gap-3">
                <span className="font-mono text-xs sm:text-sm text-fg/90 tabular-nums">
                  <span className="text-amber font-semibold">{clock(time)}</span>
                  {seekable && <span className="text-muted"> / {clock(duration)}</span>}
                </span>

                <TvButton
                  onClick={toggleFullscreen}
                  aria-label="Toggle Fullscreen"
                  className="grid size-11 place-items-center rounded-xl bg-surface/80 text-fg hover:border-amber/50"
                >
                  {isFullscreen ? <Minimize2 className="size-4" /> : <Maximize2 className="size-4" />}
                </TvButton>
              </div>
            </div>
          </div>
        </div>
      ) : (
        /* Subtle bottom mini-progress line when controls are hidden */
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-1 bg-surface-2/60">
          <div className="h-full bg-gradient-to-r from-amber to-magenta" style={{ width: `${pct}%` }} />
        </div>
      )}
    </div>
  );
}
