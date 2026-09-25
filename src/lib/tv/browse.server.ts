import { isIP } from "node:net";
import { lookup } from "node:dns/promises";
import type { BrowserMode, InspectResult, PageBlock, PageLink, SearchHit } from "@/lib/tv/types";
import { hostOf, mediaFormat, titleFromUrl } from "@/lib/tv/url";

const DESKTOP_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
const TV_UA =
  "Mozilla/5.0 (Linux; Android 14; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

class BrowseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "BrowseError";
  }
}

function friendly(err: unknown): string {
  if (err instanceof BrowseError) return err.message;
  if (err instanceof Error && (err.name === "TimeoutError" || err.name === "AbortError")) {
    return "This page took too long to respond.";
  }
  return "Unable to load this page. Check the connection and try again.";
}

function ipv4Private(ip: string): boolean {
  const parts = ip.split(".").map((n) => Number(n));
  if (parts.length !== 4 || parts.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) return true;
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

function ipPrivate(ip: string): boolean {
  const kind = isIP(ip);
  if (kind === 4) return ipv4Private(ip);
  if (kind === 6) {
    const n = ip.toLowerCase();
    if (n === "::1" || n === "::") return true;
    if (n.startsWith("fe80:") || n.startsWith("fc") || n.startsWith("fd")) return true;
    const mapped = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/.exec(n);
    if (mapped?.[1]) return ipv4Private(mapped[1]);
    return false;
  }
  return true;
}

async function assertPublicUrl(raw: string): Promise<URL> {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new BrowseError("That address is not valid.");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new BrowseError("Only http and https addresses can be opened.");
  }
  if (url.username || url.password) {
    throw new BrowseError("Addresses with embedded passwords are blocked.");
  }
  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  if (!host) throw new BrowseError("That address is not valid.");
  if (
    host === "localhost" ||
    host.endsWith(".localhost") ||
    host.endsWith(".local") ||
    host.endsWith(".internal")
  ) {
    throw new BrowseError("Local network addresses are blocked.");
  }
  if (isIP(host)) {
    if (ipPrivate(host)) throw new BrowseError("Local network addresses are blocked.");
    return url;
  }
  let records: { address: string }[];
  try {
    records = await lookup(host, { all: true, verbatim: true });
  } catch {
    throw new BrowseError("That address could not be found. Check the spelling.");
  }
  if (records.length === 0 || records.some((record) => ipPrivate(record.address))) {
    throw new BrowseError("Local network addresses are blocked.");
  }
  return url;
}

const NAMED: Record<string, string> = {
  amp: "&",
  lt: "<",
  gt: ">",
  quot: '"',
  apos: "'",
  nbsp: " ",
};

function decodeEntities(value: string): string {
  return value.replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, (full, body: string) => {
    if (body.startsWith("#")) {
      const hex = body[1] === "x" || body[1] === "X";
      const n = hex ? Number.parseInt(body.slice(2), 16) : Number.parseInt(body.slice(1), 10);
      if (!Number.isFinite(n) || n < 0 || n > 0x10ffff) return full;
      try {
        return String.fromCodePoint(n);
      } catch {
        return full;
      }
    }
    return NAMED[body.toLowerCase()] ?? full;
  });
}

function stripTags(value: string): string {
  return decodeEntities(value.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ")).trim();
}

function uaFor(mode: BrowserMode): string {
  return mode === "desktop" ? DESKTOP_UA : TV_UA;
}

async function fetchSafe(start: URL, ua: string): Promise<{ res: Response; finalUrl: URL }> {
  let current = start;
  for (let hop = 0; hop < 5; hop += 1) {
    const res = await fetch(current, {
      method: "GET",
      redirect: "manual",
      headers: {
        "User-Agent": ua,
        Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,video/*;q=0.8,*/*;q=0.7",
        "Accept-Language": "en",
      },
      signal: AbortSignal.timeout(9000),
    });
    if (res.status >= 300 && res.status < 400) {
      const loc = res.headers.get("location");
      await res.body?.cancel();
      if (!loc) throw new BrowseError("The site redirected without a destination.");
      current = await assertPublicUrl(new URL(loc, current).toString());
      continue;
    }
    return { res, finalUrl: current };
  }
  throw new BrowseError("This page redirected too many times.");
}

function frameBlocked(res: Response): boolean {
  const xfo = (res.headers.get("x-frame-options") ?? "").toLowerCase();
  if (xfo.includes("deny") || xfo.includes("sameorigin")) return true;
  const csp = (res.headers.get("content-security-policy") ?? "").toLowerCase();
  const match = /frame-ancestors\s+([^;]+)/.exec(csp);
  if (!match?.[1]) return false;
  const src = match[1];
  if (src.includes("'none'")) return true;
  if (src.includes("*")) return false;
  if (src.includes("'self'") && !src.includes("http")) return true;
  return false;
}

function formatFromType(contentType: string): "hls" | "dash" | "file" | null {
  const c = contentType.toLowerCase();
  if (c.includes("mpegurl")) return "hls";
  if (c.includes("dash+xml")) return "dash";
  if (c.startsWith("video/")) return "file";
  return null;
}

async function readLimited(res: Response, max: number): Promise<string> {
  const reader = res.body?.getReader();
  if (!reader) return "";
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (total < max) {
    const { done, value } = await reader.read();
    if (done || !value) break;
    chunks.push(value);
    total += value.byteLength;
  }
  await reader.cancel().catch(() => undefined);
  const size = Math.min(total, max);
  const buf = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    const take = Math.min(chunk.byteLength, size - offset);
    if (take <= 0) break;
    buf.set(chunk.subarray(0, take), offset);
    offset += take;
  }
  const charset =
    /charset=([^;]+)/i.exec(res.headers.get("content-type") ?? "")?.[1]?.trim().replace(/["']/g, "") ||
    "utf-8";
  try {
    return new TextDecoder(charset).decode(buf);
  } catch {
    return new TextDecoder("utf-8").decode(buf);
  }
}

function extractBlocks(html: string): PageBlock[] {
  const cleaned = html
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<noscript[\s\S]*?<\/noscript>/gi, " ");
  const blocks: PageBlock[] = [];
  const re = /<(p|h1|h2|h3|li)\b[^>]*>([\s\S]*?)<\/\1>/gi;
  let match: RegExpExecArray | null;
  while ((match = re.exec(cleaned)) && blocks.length < 36) {
    const text = stripTags(match[2] ?? "");
    if (text.length < 2) continue;
    const tag = (match[1] ?? "p").toLowerCase();
    const kind: PageBlock["kind"] = tag === "li" ? "li" : tag.startsWith("h") ? "h" : "p";
    blocks.push({ kind, text: text.slice(0, kind === "p" ? 700 : 220) });
  }
  if (blocks.length === 0) {
    const text = stripTags(cleaned).slice(0, 1200);
    if (text) blocks.push({ kind: "p", text });
  }
  return blocks;
}

function extractLinks(html: string, base: URL): PageLink[] {
  const out: PageLink[] = [];
  const seen = new Set<string>();
  const re = /<a\b[^>]*href\s*=\s*("([^"]*)"|'([^']*)')[^>]*>([\s\S]*?)<\/a>/gi;
  let match: RegExpExecArray | null;
  while ((match = re.exec(html)) && out.length < 24) {
    const hrefRaw = (match[2] || match[3] || "").trim();
    const text = stripTags(match[4] ?? "").slice(0, 140);
    if (!text || text.length < 2) continue;
    if (/^(javascript:|mailto:|tel:|#)/i.test(hrefRaw)) continue;
    let abs: URL;
    try {
      abs = new URL(hrefRaw, base);
    } catch {
      continue;
    }
    if (abs.protocol !== "http:" && abs.protocol !== "https:") continue;
    abs.hash = "";
    const href = abs.toString();
    if (seen.has(href)) continue;
    seen.add(href);
    out.push({ text, href });
  }
  return out;
}

function statusMessage(status: number): string {
  if (status === 404) return "This page was not found.";
  if (status === 401 || status === 403) return "This site refused the request.";
  if (status >= 500) return "The site had a problem. Try again.";
  return "Unable to load this page.";
}

export async function inspectPage(raw: string, mode: BrowserMode): Promise<InspectResult> {
  try {
    const safe = await assertPublicUrl(raw);
    const hinted = mediaFormat(safe.toString());
    if (hinted) {
      return {
        ok: true,
        kind: "media",
        format: hinted,
        url: safe.toString(),
        title: titleFromUrl(safe.toString()),
      };
    }
    const { res, finalUrl } = await fetchSafe(safe, uaFor(mode));
    const contentType = res.headers.get("content-type") ?? "";
    const fromType = formatFromType(contentType);
    const fromUrl = mediaFormat(finalUrl.toString());
    const format = fromType ?? fromUrl;
    if (format) {
      await res.body?.cancel();
      return {
        ok: true,
        kind: "media",
        format,
        url: finalUrl.toString(),
        title: titleFromUrl(finalUrl.toString()),
      };
    }
    if (!res.ok && !contentType.toLowerCase().includes("html")) {
      await res.body?.cancel();
      return { ok: false, message: statusMessage(res.status) };
    }
    const html = await readLimited(res, 700_000);
    const title = (() => {
      const found = /<title[^>]*>([\s\S]*?)<\/title>/i.exec(html);
      return found ? stripTags(found[1] ?? "").slice(0, 180) : "";
    })();
    return {
      ok: true,
      kind: "page",
      url: finalUrl.toString(),
      title: title || hostOf(finalUrl.toString()),
      frameBlocked: frameBlocked(res),
      insecure: finalUrl.protocol === "http:",
      blocks: extractBlocks(html),
      links: extractLinks(html, finalUrl),
    };
  } catch (err) {
    return { ok: false, message: friendly(err) };
  }
}

function unwrapDdg(href: string): string {
  const raw = href.replace(/&/g, "&");
  const withProto = raw.startsWith("//") ? `https:${raw}` : raw;
  try {
    const url = new URL(withProto);
    return url.searchParams.get("uddg") || url.toString();
  } catch {
    return withProto;
  }
}

function obviouslyUnsafe(raw: string): boolean {
  try {
    const url = new URL(raw);
    if (url.protocol !== "http:" && url.protocol !== "https:") return true;
    if (url.username || url.password) return true;
    const host = url.hostname.toLowerCase();
    if (host === "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return true;
    if (isIP(host) && ipPrivate(host)) return true;
    return false;
  } catch {
    return true;
  }
}

function parseDuckDuckGo(html: string): SearchHit[] {
  const hits: SearchHit[] = [];
  const seen = new Set<string>();
  const re = /<a\b([^>]*\bclass="[^"]*\bresult__a\b[^"]*"[^>]*)>([\s\S]*?)<\/a>/gi;
  let match: RegExpExecArray | null;
  while ((match = re.exec(html)) && hits.length < 10) {
    const attrs = match[1] ?? "";
    const hrefMatch = /href="([^"]+)"/i.exec(attrs);
    if (!hrefMatch?.[1]) continue;
    const url = unwrapDdg(hrefMatch[1]);
    if (obviouslyUnsafe(url) || seen.has(url)) continue;
    const title = stripTags(match[2] ?? "");
    if (!title) continue;
    const windowHtml = html.slice(match.index, match.index + 2200);
    const snippetMatch = /class="[^"]*\bresult__snippet\b[^"]*"[^>]*>([\s\S]*?)<\/(?:a|td|span|div)>/i.exec(
      windowHtml,
    );
    let snippet = snippetMatch ? stripTags(snippetMatch[1] ?? "").slice(0, 240) : "";
    if (snippet.toLowerCase() === title.toLowerCase()) snippet = "";
    seen.add(url);
    hits.push({ title, url, snippet, host: hostOf(url) });
  }
  return hits;
}

export async function runSearch(query: string): Promise<SearchHit[]> {
  const trimmed = query.trim();
  if (!trimmed) return [];
  const url = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(trimmed)}`;
  let res: Response;
  try {
    res = await fetch(url, {
      headers: {
        "User-Agent": DESKTOP_UA,
        Accept: "text/html",
        "Accept-Language": "en",
      },
      signal: AbortSignal.timeout(10000),
    });
  } catch (err) {
    if (err instanceof Error && (err.name === "TimeoutError" || err.name === "AbortError")) {
      throw new BrowseError("Search took too long. Try again.");
    }
    throw new BrowseError("Search is unavailable right now.");
  }
  if (!res.ok) throw new BrowseError("Search is unavailable right now.");
  return parseDuckDuckGo(await res.text());
}
