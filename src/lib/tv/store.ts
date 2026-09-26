import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { BrowserMode, Engine } from "@/lib/tv/types";

export type Bookmark = {
  id: string;
  title: string;
  url: string;
  createdAt: number;
};

export type HistoryEntry = {
  id: string;
  title: string;
  url: string;
  visitedAt: number;
};

export type DeviceMode = "tv" | "mobile" | "auto";
export type MobileOrientation = "portrait" | "landscape";

export type Settings = {
  homepage: string;
  searchEngine: Engine;
  browserMode: BrowserMode;
  javascriptEnabled: boolean;
  hideControlsWhilePlaying: boolean;
  deviceMode: DeviceMode;
  mobileOrientation: MobileOrientation;
  virtualRemote: boolean;
};

type Persisted = {
  bookmarks: Bookmark[];
  history: HistoryEntry[];
  settings: Settings;
};

type Store = Persisted & {
  toggleBookmark: (url: string, title: string) => void;
  isBookmarked: (url: string) => boolean;
  removeBookmark: (id: string) => void;
  visit: (url: string, title: string) => void;
  updateSettings: (patch: Partial<Settings>) => void;
  clearHistory: () => void;
  clearBookmarks: () => void;
  clearAll: () => void;
};

export const defaultSettings: Settings = {
  homepage: "",
  searchEngine: "google",
  browserMode: "standard",
  javascriptEnabled: true,
  hideControlsWhilePlaying: true,
  deviceMode: "auto",
  mobileOrientation: "portrait",
  virtualRemote: false,
};

export const defaultBookmarks: Bookmark[] = [
  {
    id: "default-ogomovies",
    title: "OgoMovies",
    url: "https://ogomovies2.com.pk/",
    createdAt: 1710000000000,
  },
];

export const useTv = create<Store>()(
  persist(
    (set, get) => ({
      bookmarks: defaultBookmarks,
      history: [],
      settings: defaultSettings,
      isBookmarked: (url) => get().bookmarks.some((item) => item.url === url),
      toggleBookmark: (url, title) => {
        const existing = get().bookmarks.find((item) => item.url === url);
        if (existing) {
          set({ bookmarks: get().bookmarks.filter((item) => item.url !== url) });
          return;
        }
        const next: Bookmark = {
          id: crypto.randomUUID(),
          title: title || url,
          url,
          createdAt: Date.now(),
        };
        set({ bookmarks: [next, ...get().bookmarks].slice(0, 80) });
      },
      removeBookmark: (id) => set({ bookmarks: get().bookmarks.filter((item) => item.id !== id) }),
      visit: (url, title) => {
        const rest = get().history.filter((item) => item.url !== url);
        const next: HistoryEntry = {
          id: crypto.randomUUID(),
          url,
          title: title || url,
          visitedAt: Date.now(),
        };
        set({ history: [next, ...rest].slice(0, 60) });
      },
      updateSettings: (patch) => set({ settings: { ...get().settings, ...patch } }),
      clearHistory: () => set({ history: [] }),
      clearBookmarks: () => set({ bookmarks: [] }),
      clearAll: () => set({ history: [], bookmarks: [], settings: defaultSettings }),
    }),
    {
      name: "xtream.v1",
      skipHydration: true,
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        bookmarks: state.bookmarks,
        history: state.history,
        settings: state.settings,
      }),
      merge: (persisted, current) => {
        const saved = (persisted ?? {}) as Partial<Persisted>;
        const loadedBookmarks = saved.bookmarks ?? [];
        const hasOgo = loadedBookmarks.some((b) => b.url.includes("ogomovies2.com.pk"));
        return {
          ...current,
          bookmarks: loadedBookmarks.length === 0 ? defaultBookmarks : (hasOgo ? loadedBookmarks : [...defaultBookmarks, ...loadedBookmarks]),
          history: saved.history ?? [],
          settings: { ...defaultSettings, ...saved.settings },
        };
      },
    },
  ),
);
