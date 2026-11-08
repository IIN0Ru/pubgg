import { describe, expect, it } from 'vitest';
import { formatSummary, summarizeMatches } from './matchSummary';
import { match } from '../test/fixtures';

describe('summarizeMatches', () => {
  it('판수·평균 등수·치킨 수·TOP 10 비율을 계산한다', () => {
    const s = summarizeMatches([1, 5, 10, 20].map((rank, i) => match({ matchId: `m${i}`, rank })));
    expect(s).toEqual({ games: 4, avgRank: 9, wins: 1, top10Percent: 75 });
    expect(formatSummary(s!)).toBe('4판 · 평균 #9.0 · 치킨 1 · TOP 10 75%');
  });

  it('등수 기록이 없으면 null (0등은 기록 없음으로 본다)', () => {
    expect(summarizeMatches([])).toBeNull();
    expect(summarizeMatches([match({ rank: 0 })])).toBeNull();
  });

  it('TOP 10 비율은 반올림한다', () => {
    const s = summarizeMatches([1, 2, 30].map((rank, i) => match({ matchId: `m${i}`, rank })));
    expect(s?.top10Percent).toBe(67);
  });
});
