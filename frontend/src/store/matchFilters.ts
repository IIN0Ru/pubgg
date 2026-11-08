import { create } from 'zustand';
import type { Match, MatchType, Perspective } from '../api/types';

/**
 * 경기 목록 화면 설정: 경기 종류 필터, 시점(FPP/TPP) 필터, 지금 몇 경기까지 보여줄지.
 * 서버가 아니라 브라우저에만 있는 상태라서 React Query 가 아닌 Zustand 로 둔다.
 * (서버 데이터 = React Query / 화면 설정 = Zustand 로 나눈다)
 */
export type TypeFilter = 'all' | Extract<MatchType, 'competitive' | 'official'>;
export type PerspectiveFilter = 'all' | Perspective;

/** 처음에, 그리고 "더 보기"를 한 번 누를 때마다 보여줄 경기 수 */
export const PAGE_SIZE = 20;

interface MatchFilterState {
  type: TypeFilter;
  perspective: PerspectiveFilter;
  shown: number;
  setType: (type: TypeFilter) => void;
  setPerspective: (perspective: PerspectiveFilter) => void;
  showMore: () => void;
  reset: () => void;
}

export const useMatchFilters = create<MatchFilterState>((set) => ({
  type: 'all',
  perspective: 'all',
  shown: PAGE_SIZE,
  // 필터를 바꾸면 처음 20경기부터 다시 보여준다
  setType: (type) => set({ type, shown: PAGE_SIZE }),
  setPerspective: (perspective) => set({ perspective, shown: PAGE_SIZE }),
  showMore: () => set((s) => ({ shown: s.shown + PAGE_SIZE })),
  reset: () => set({ type: 'all', perspective: 'all', shown: PAGE_SIZE }),
}));

export function filterMatches(matches: Match[], type: TypeFilter, perspective: PerspectiveFilter): Match[] {
  return matches.filter(
    (m) => (type === 'all' || m.type === type) && (perspective === 'all' || m.perspective === perspective),
  );
}
