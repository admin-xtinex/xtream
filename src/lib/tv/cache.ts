import { inspectUrl, searchWeb } from "@/lib/tv/browse.functions";
import type { BrowserMode, InspectResult, SearchHit } from "@/lib/tv/types";

const pages = new Map<string, InspectResult>();
const searches = new Map<string, SearchHit[]>();

export async function inspectCached(url: string, mode: BrowserMode): Promise<InspectResult> {
  const key = `${mode}:${url}`;
  const cached = pages.get(key);
  if (cached) return cached;
  const result = await inspectUrl({ data: { url, mode } });
  if (result.ok) pages.set(key, result);
  return result;
}

export async function searchCached(query: string): Promise<SearchHit[]> {
  const key = query.trim().toLowerCase();
  const cached = searches.get(key);
  if (cached) return cached;
  const hits = await searchWeb({ data: { query } });
  searches.set(key, hits);
  return hits;
}

export function clearBrowseCache(): void {
  pages.clear();
  searches.clear();
}
