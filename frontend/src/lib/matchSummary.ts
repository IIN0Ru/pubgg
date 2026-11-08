import type { Match } from '../api/types';

export interface MatchSummary {
  games: number;
  avgRank: number;
  wins: number;
  /** 0~100 */
  top10Percent: number;
}

/** 지금 필터에 걸린 경기 요약. 등수가 있는 경기가 없으면 null */
export function summarizeMatches(matches: Match[]): MatchSummary | null {
  const ranks = matches.map((m) => m.rank).filter((r) => r > 0);
  if (ranks.length === 0) return null;
  const sum = ranks.reduce((a, b) => a + b, 0);
  return {
    games: ranks.length,
    avgRank: sum / ranks.length,
    wins: ranks.filter((r) => r === 1).length,
    top10Percent: Math.round((ranks.filter((r) => r <= 10).length * 100) / ranks.length),
  };
}

/** "42판 · 평균 #8.2 · 치킨 2 · TOP 10 40%" */
export function formatSummary(s: MatchSummary): string {
  return `${s.games}판 · 평균 #${s.avgRank.toFixed(1)} · 치킨 ${s.wins} · TOP 10 ${s.top10Percent}%`;
}
