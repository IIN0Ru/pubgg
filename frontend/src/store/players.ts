import { create } from 'zustand';
import type { Shard } from '../api/types';

/**
 * 즐겨찾기와 최근 검색. 로그인이 없어서 브라우저(localStorage)에 저장한다.
 *
 * 상단 검색창의 목록은 기존 static/js/players.js 가 같은 키·같은 형식으로 읽고 쓴다.
 * 그래서 형식을 바꾸지 않는다: [{ platform: "steam", nickname: "N0-RU", label: "스팀" }, ...]
 * (zustand 의 persist 를 쓰면 { state, version } 으로 감싸져서 기존 목록과 어긋난다)
 */
export interface SavedPlayer {
  platform: Shard;
  nickname: string;
  label: string;
}

export const FAVORITES_KEY = 'pubgg.favorites';
export const RECENT_KEY = 'pubgg.recent';
export const MAX_RECENT = 8;

// 사생활 보호 모드 등에서 저장소가 막혀도 화면은 동작해야 해서 모든 접근을 try/catch 로 감싼다
function load(key: string): SavedPlayer[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(key) ?? '[]');
    return Array.isArray(value) ? value.filter(isSavedPlayer) : [];
  } catch {
    return [];
  }
}

function save(key: string, list: SavedPlayer[]): void {
  try {
    localStorage.setItem(key, JSON.stringify(list));
  } catch {
    // 저장이 막힌 환경이면 조용히 무시 (이번 화면에서만 동작)
  }
}

function isSavedPlayer(value: unknown): value is SavedPlayer {
  const p = value as SavedPlayer | null;
  return (
    typeof p === 'object' &&
    p !== null &&
    (p.platform === 'steam' || p.platform === 'kakao') &&
    typeof p.nickname === 'string' &&
    typeof p.label === 'string'
  );
}

export function samePlayer(a: SavedPlayer, b: SavedPlayer): boolean {
  return a.platform === b.platform && a.nickname.toLowerCase() === b.nickname.toLowerCase();
}

const without = (list: SavedPlayer[], player: SavedPlayer) => list.filter((p) => !samePlayer(p, player));

interface PlayerListsState {
  favorites: SavedPlayer[];
  recent: SavedPlayer[];
  isFavorite: (player: SavedPlayer) => boolean;
  /** 즐겨찾기에 있으면 빼고, 없으면 맨 앞에 넣는다 */
  toggleFavorite: (player: SavedPlayer) => void;
  /** 최근 검색 맨 앞에 넣는다 (이미 있으면 끌어올림, 최대 MAX_RECENT 개) */
  remember: (player: SavedPlayer) => void;
  /** 저장소 내용을 다시 읽는다 (다른 탭이나 기존 스크립트가 바꿨을 때) */
  reload: () => void;
}

export const usePlayerLists = create<PlayerListsState>((set, get) => ({
  favorites: load(FAVORITES_KEY),
  recent: load(RECENT_KEY),

  isFavorite: (player) => get().favorites.some((p) => samePlayer(p, player)),

  toggleFavorite: (player) => {
    // 상단 검색창의 목록에서 지웠을 수도 있으니, 바꾸기 전에 저장된 값을 다시 읽는다
    const current = load(FAVORITES_KEY);
    const favorites = current.some((p) => samePlayer(p, player))
      ? without(current, player)
      : [player, ...current];
    save(FAVORITES_KEY, favorites);
    set({ favorites });
  },

  remember: (player) => {
    const recent = [player, ...without(load(RECENT_KEY), player)].slice(0, MAX_RECENT);
    save(RECENT_KEY, recent);
    set({ recent });
  },

  reload: () => set({ favorites: load(FAVORITES_KEY), recent: load(RECENT_KEY) }),
}));

// 다른 탭에서 바꾸면 이 탭도 따라간다
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key === FAVORITES_KEY || e.key === RECENT_KEY) usePlayerLists.getState().reload();
  });
}
