import { useEffect, useRef, useState } from "react";
import { Pause, Play, RotateCcw, RotateCw, Star, Volume2, VolumeX } from "lucide-react";
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
  const [fatal, setFatal] = useState<string | null>(format === "dash" ? "dash" : null);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [muted, setMuted] = useState(false);
  const [chromeOn, setChromeOn] = useState(true);

  useEffect(() => {
    setFatal(format === "dash" ? "dash" : null);
    setTime(0);
    setDuration(0);
    setPlaying(false);
    setChromeOn(true);
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
    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    video.addEventListener("error", onError);
    video.addEventListener("timeupdate", onTime);
    video.addEventListener("durationchange", onMeta);
    video.addEventListener("play", onPlay);
    video.addEventListener("pause", onPause);

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
          if (!data.fatal || dead) return;
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
      video.removeEventListener("play", onPlay);
      video.removeEventListener("pause", onPause);
      hls?.destroy();
      video.removeAttribute("src");
      video.load();
    };
  }, [url, format]);

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
    const id = window.setTimeout(() => setChromeOn(false), 4000);
    return () => window.clearTimeout(id);
  }, [hideControls, playing, chromeOn]);

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

  const seekable = Number.isFinite(duration) && duration > 0;
  const pct = seekable ? Math.min(100, (time / duration) * 100) : 0;

  if (fatal) {
    const message =
      fatal === "dash"
        ? "This stream uses MPEG-DASH, which this player does not support."
        : fatal === "hls"
          ? "This browser cannot play that HLS stream."
          : "This video cannot be played on this device.";
    return (
      <div className="flex h-full flex-col items-center justify-center gap-6 bg-bg-deep px-6 text-center">
        <p className="text-sm font-semibold tracking-widest text-amber uppercase">Video</p>
        <h1 className="max-w-xl font-display text-4xl text-fg">{message}</h1>
        <p className="max-w-md text-base text-muted">
          Xtream plays ordinary HTML5 video. It does not bypass sign-in, DRM, or regional limits.
        </p>
        <div className="flex gap-3">
          <TvButton
            primary
            onClick={onBack}
            className="min-h-12 bg-amber px-6 font-semibold text-amber-ink"
          >
            Back
          </TvButton>
        </div>
      </div>
    );
  }

  return (
    <div
      id={chromeOn ? undefined : "player-chrome-hidden"}
      className="relative h-full bg-bg-deep"
    >
      <video
        ref={videoRef}
        className="h-full w-full bg-bg-deep object-contain"
        playsInline
        preload="metadata"
        onClick={() => setChromeOn((on) => !on)}
      />
      {chromeOn ? (
        <div className="absolute inset-0 flex flex-col justify-between">
          <div className="flex items-start justify-between gap-4 bg-bg-deep/80 px-5 pt-5 pb-8 md:px-8">
            <div className="min-w-0">
              <p className="text-sm font-semibold tracking-widest text-amber uppercase">Now playing</p>
              <h1 className="mt-1 truncate font-display text-3xl text-fg md:text-4xl">{title}</h1>
            </div>
            <TvButton
              aria-label={bookmarked ? "Remove bookmark" : "Save bookmark"}
              aria-pressed={bookmarked}
              onClick={onToggleBookmark}
              className="grid size-12 shrink-0 place-items-center rounded-full bg-surface text-fg"
            >
              <Star className="size-5" fill={bookmarked ? "currentColor" : "none"} />
            </TvButton>
          </div>
          <div className="bg-bg-deep/90 px-5 pt-6 pb-5 md:px-8" onClick={(event) => event.stopPropagation()}>
            {!playing ? (
              <div className="mb-4 flex justify-center">
                <TvButton
                  primary
                  data-player-play=""
                  onClick={toggle}
                  aria-label="Play"
                  className="grid size-16 place-items-center rounded-full bg-amber text-amber-ink"
                >
                  <Play className="size-7" fill="currentColor" />
                </TvButton>
              </div>
            ) : null}
            <div className="flex flex-wrap items-center gap-2">
              <TvButton
                data-player-play=""
                onClick={toggle}
                aria-label={playing ? "Pause" : "Play"}
                className="grid size-12 place-items-center rounded-full bg-surface text-fg"
              >
                {playing ? <Pause className="size-5" /> : <Play className="size-5" fill="currentColor" />}
              </TvButton>
              <TvButton
                onClick={() => {
                  const video = videoRef.current;
                  if (video) video.currentTime = Math.max(0, video.currentTime - 10);
                }}
                aria-label="Back 10 seconds"
                className="flex min-h-12 items-center gap-2 rounded-full bg-surface px-4 text-fg"
              >
                <RotateCcw className="size-4" />
                10s
              </TvButton>
              <TvButton
                onClick={() => {
                  const video = videoRef.current;
                  if (!video || !Number.isFinite(video.duration)) return;
                  video.currentTime = Math.min(video.duration, video.currentTime + 10);
                }}
                aria-label="Forward 10 seconds"
                className="flex min-h-12 items-center gap-2 rounded-full bg-surface px-4 text-fg"
              >
                <RotateCw className="size-4" />
                10s
              </TvButton>
              <TvButton
                onClick={toggleMute}
                aria-label={muted ? "Unmute" : "Mute"}
                className="grid size-12 place-items-center rounded-full bg-surface text-fg"
              >
                {muted ? <VolumeX className="size-5" /> : <Volume2 className="size-5" />}
              </TvButton>
              <span className="ml-auto text-sm text-fg tabular-nums">
                {clock(time)}
                {seekable ? ` / ${clock(duration)}` : ""}
              </span>
              <TvButton
                onClick={onBack}
                className="min-h-12 rounded-full bg-surface px-4 font-semibold text-fg"
              >
                Exit
              </TvButton>
            </div>
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
              className="tv mt-4 flex h-12 items-center"
            >
              <span className="relative h-2 w-full rounded-full bg-surface-2">
                <span className="absolute inset-y-0 left-0 rounded-full bg-amber" style={{ width: `${pct}%` }} />
              </span>
            </div>
            <p className="mt-2 text-sm text-muted">Left and right on the bar seek. Back leaves the video.</p>
          </div>
        </div>
      ) : (
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-1 bg-surface">
          <div className="h-full bg-amber" style={{ width: `${pct}%` }} />
        </div>
      )}
    </div>
  );
}
