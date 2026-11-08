import { create } from 'zustand';
import type { Match, MatchType, Perspective } from '../api/types';

/**
 * 경기 목록 필터. 서버가 아니라 브라우저에만 있는 상태라서 React Query 가 아닌 Zustand 로 둔다.
 * (서버 데이터 = React Query / 화면 설정 = Zustand 로 나눈다)
 */
export type TypeFilter = 'all' | Extract<MatchType, 'competitive' | 'official'>;
export type PerspectiveFilter = 'all' | Perspective;

interface MatchFilterState {
  type: TypeFilter;
  perspective: PerspectiveFilter;
  setType: (type: TypeFilter) => void;
  setPerspective: (perspective: PerspectiveFilter) => void;
  reset: () => void;
}

export const useMatchFilters = create<MatchFilterState>((set) => ({
  type: 'all',
  perspective: 'all',
  setType: (type) => set({ type }),
  setPerspective: (perspective) => set({ perspective }),
  reset: () => set({ type: 'all', perspective: 'all' }),
}));

export function filterMatches(matches: Match[], type: TypeFilter, perspective: PerspectiveFilter): Match[] {
  return matches.filter(
    (m) => (type === 'all' || m.type === type) && (perspective === 'all' || m.perspective === perspective),
  );
}
