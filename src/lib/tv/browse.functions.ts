import { createServerFn } from "@tanstack/react-start";
import type { BrowserMode, InspectResult, SearchHit } from "@/lib/tv/types";

function asMode(value: unknown): BrowserMode {
  return value === "desktop" ? "desktop" : "standard";
}

export const searchWeb = createServerFn({ method: "POST" })
  .validator((data: unknown) => {
    if (!data || typeof data !== "object" || !("query" in data) || typeof data.query !== "string") {
      throw new Error("Enter something to search for.");
    }
    const query = data.query.trim();
    if (!query || query.length > 200) throw new Error("Enter something to search for.");
    return { query };
  })
  .handler(async ({ data }): Promise<SearchHit[]> => {
    const { runSearch } = await import("./browse.server.ts");
    return runSearch(data.query);
  });

export const inspectUrl = createServerFn({ method: "POST" })
  .validator((data: unknown) => {
    if (!data || typeof data !== "object" || !("url" in data) || typeof data.url !== "string") {
      throw new Error("That address is not valid.");
    }
    const url = data.url.trim();
    if (!url || url.length > 2000) throw new Error("That address is not valid.");
    const mode = "mode" in data ? asMode(data.mode) : "standard";
    return { url, mode };
  })
  .handler(async ({ data }): Promise<InspectResult> => {
    const { inspectPage } = await import("./browse.server.ts");
    return inspectPage(data.url, data.mode);
  });
