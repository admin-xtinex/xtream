import type { Engine, MediaFormat } from "@/lib/tv/types";

const DOMAIN =
  /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}(?::\d{2,5})?(?:[/?#][^\s]*)?$/i;

const MEDIA = /\.(mp4|webm|ogg|m3u8|mpd)(?:$|[?#])/i;

export type ResolvedInput =
  | { kind: "empty" }
  | { kind: "url"; url: string }
  | { kind: "search"; query: string };

export function resolveInput(raw: string): ResolvedInput {
  const input = raw.trim();
  if (!input) return { kind: "empty" };
  if (/^https?:\/\//i.test(input)) return { kind: "url", url: input };
  if (!/\s/.test(input) && DOMAIN.test(input)) return { kind: "url", url: `https://${input}` };
  return { kind: "search", query: input };
}

export function mediaFormat(url: string): MediaFormat | null {
  const clean = url.split("#")[0] ?? url;
  if (/\.m3u8(?:$|\?)/i.test(clean)) return "hls";
  if (/\.mpd(?:$|\?)/i.test(clean)) return "dash";
  if (/\.(mp4|webm|ogg)(?:$|\?)/i.test(clean)) return "file";
  return null;
}

export function isDirectMedia(url: string): boolean {
  return MEDIA.test(url.split("#")[0] ?? url);
}

function ipv4Private(ip: string): boolean {
  const parts = ip.split(".").map((n) => Number(n));
  if (parts.length !== 4 || parts.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) {
    return true;
  }
  const a = parts[0] ?? 0;
  const b = parts[1] ?? 0;
  if (a === 0 || a === 10 || a === 127) return true;
  if (a === 169 && b === 254) return true;
  if (a === 172 && b >= 16 && b <= 31) return true;
  if (a === 192 && b === 168) return true;
  if (a === 100 && b >= 64 && b <= 127) return true;
  if (a >= 224) return true;
  return false;
}

export function isSafeClientUrl(raw: string): boolean {
  try {
    const url = new URL(raw);
    if (url.protocol !== "http:" && url.protocol !== "https:") return false;
    if (url.username || url.password) return false;
    const host = url.hostname.toLowerCase().replace(/\.$/, "");
    if (!host) return false;
    if (
      host === "localhost" ||
      host.endsWith(".localhost") ||
      host.endsWith(".local") ||
      host.endsWith(".internal")
    ) {
      return false;
    }
    if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host) && ipv4Private(host)) return false;
    if (host === "::1" || host === "[::1]") return false;
    return true;
  } catch {
    return false;
  }
}

export function engineLabel(engine: Engine): string {
  if (engine === "bing") return "Bing";
  if (engine === "duckduckgo") return "DuckDuckGo";
  return "Google";
}

export function searchPageUrl(engine: Engine, query: string): string {
  const q = encodeURIComponent(query);
  if (engine === "bing") return `https://www.bing.com/search?q=${q}`;
  if (engine === "duckduckgo") return `https://duckduckgo.com/?q=${q}`;
  return `https://www.google.com/search?q=${q}`;
}

export function hostOf(raw: string): string {
  try {
    return new URL(raw).hostname.replace(/^www\./, "");
  } catch {
    return raw;
  }
}

export function titleFromUrl(raw: string): string {
  try {
    const url = new URL(raw);
    const file = decodeURIComponent(url.pathname.split("/").filter(Boolean).pop() ?? "");
    if (file) {
      return file
        .replace(/\.(mp4|webm|ogg|m3u8|mpd)$/i, "")
        .replace(/[-_]+/g, " ")
        .trim();
    }
    return hostOf(raw);
  } catch {
    return raw;
  }
}

export const FILMS = [
  {
    n: "01",
    title: "Big Buck Bunny",
    note: "Open movie · MP4",
    url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
  },
  {
    n: "02",
    title: "Sintel",
    note: "Open movie · MP4",
    url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
  },
  {
    n: "03",
    title: "Tears of Steel",
    note: "Open movie · MP4",
    url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
  },
  {
    n: "04",
    title: "HLS sample",
    note: "Mux test stream",
    url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
  },
] as const;

export const STARTER_PAGES = [
  { title: "Example Domain", note: "example.com", url: "https://example.com" },
  { title: "The first website", note: "info.cern.ch", url: "https://info.cern.ch" },
] as const;
