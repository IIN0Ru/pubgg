import { beforeEach, describe, expect, it } from 'vitest';
import type { Match } from '../api/types';
import { filterMatches, PAGE_SIZE, useMatchFilters } from './matchFilters';

function match(id: string, type: Match['type'], perspective: Match['perspective']): Match {
  return { matchId: id, type, perspective } as Match;
}

const matches = [
  match('a', 'competitive', 'fpp'),
  match('b', 'competitive', 'tpp'),
  match('c', 'official', 'fpp'),
  match('d', 'other', 'tpp'),
];

describe('filterMatches', () => {
  it('all 이면 전부', () => expect(filterMatches(matches, 'all', 'all')).toHaveLength(4));
  it('경기 종류로 거른다', () =>
    expect(filterMatches(matches, 'competitive', 'all').map((m) => m.matchId)).toEqual(['a', 'b']));
  it('종류와 시점을 같이 건다', () =>
    expect(filterMatches(matches, 'competitive', 'fpp').map((m) => m.matchId)).toEqual(['a']));
  it('일반 필터에는 커스텀 같은 other 가 안 섞인다', () =>
    expect(filterMatches(matches, 'official', 'all').map((m) => m.matchId)).toEqual(['c']));
});

describe('useMatchFilters', () => {
  beforeEach(() => useMatchFilters.getState().reset());

  it('값을 바꾸고 되돌린다', () => {
    useMatchFilters.getState().setType('competitive');
    useMatchFilters.getState().setPerspective('tpp');
    expect(useMatchFilters.getState()).toMatchObject({ type: 'competitive', perspective: 'tpp' });
    useMatchFilters.getState().reset();
    expect(useMatchFilters.getState()).toMatchObject({ type: 'all', perspective: 'all', shown: PAGE_SIZE });
  });

  it('더 보기는 20경기씩 늘리고, 필터를 바꾸면 처음으로 돌아간다', () => {
    useMatchFilters.getState().showMore();
    useMatchFilters.getState().showMore();
    expect(useMatchFilters.getState().shown).toBe(PAGE_SIZE * 3);
    useMatchFilters.getState().setType('official');
    expect(useMatchFilters.getState().shown).toBe(PAGE_SIZE);
    useMatchFilters.getState().showMore();
    useMatchFilters.getState().setPerspective('fpp');
    expect(useMatchFilters.getState().shown).toBe(PAGE_SIZE);
  });
});
