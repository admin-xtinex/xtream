export type Engine = "google" | "duckduckgo" | "bing";
export type BrowserMode = "standard" | "desktop";
export type MediaFormat = "hls" | "dash" | "file";

export type SearchHit = {
  title: string;
  url: string;
  snippet: string;
  host: string;
};

export type PageLink = {
  text: string;
  href: string;
};

export type PageBlock = {
  kind: "h" | "p" | "li";
  text: string;
};

export type InspectResult =
  | {
      ok: true;
      kind: "media";
      format: MediaFormat;
      url: string;
      title: string;
    }
  | {
      ok: true;
      kind: "page";
      url: string;
      title: string;
      frameBlocked: boolean;
      insecure: boolean;
      blocks: PageBlock[];
      links: PageLink[];
    }
  | {
      ok: false;
      message: string;
    };

export const APP_VERSION = "1.0.0";
