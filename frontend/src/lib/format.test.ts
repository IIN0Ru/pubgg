import { describe, expect, it } from 'vitest';
import {
  formatCountdown,
  formatKda,
  formatPlayedAt,
  formatRankPoint,
  formatRelativeTime,
  formatSurvived,
  matchRankClass,
  resultBadge,
} from './format';

describe('formatRelativeTime', () => {
  const now = Date.parse('2026-11-08T12:00:00Z');
  it.each([
    ['2026-11-08T11:59:30Z', '방금 전'],
    ['2026-11-08T11:57:00Z', '3분 전'],
    ['2026-11-08T09:00:00Z', '3시간 전'],
    ['2026-11-06T11:00:00Z', '2일 전'],
  ])('%s -> %s', (iso, expected) => {
    expect(formatRelativeTime(iso, now)).toBe(expected);
  });
});

describe('숫자 표시', () => {
  it('RP 는 천 단위 쉼표', () => expect(formatRankPoint(2450)).toBe('2,450 RP'));
  it('KDA 는 소수 둘째 자리', () => expect(formatKda(3.4167)).toBe('3.42'));
  it('생존 시간', () => expect(formatSurvived(1500)).toBe('25분 0초'));
  it('갱신 대기 시간', () => {
    expect(formatCountdown(95)).toBe('1:35');
    expect(formatCountdown(-3)).toBe('0:00');
  });
});

describe('resultBadge', () => {
  it('1등은 치킨, 2~10등은 TOP 10, 그 밖은 없음', () => {
    expect(resultBadge(1)).toBe('치킨');
    expect(resultBadge(2)).toBe('TOP 10');
    expect(resultBadge(10)).toBe('TOP 10');
    expect(resultBadge(11)).toBe('');
    expect(resultBadge(0)).toBe('');
  });
});

describe('formatPlayedAt', () => {
  it('한국 시간으로 보여준다', () => {
    // 2026-10-19 12:34 UTC = 21:34 KST
    expect(formatPlayedAt('2026-10-19T12:34:00Z')).toBe('10월 19일 21:34');
  });
});

describe('matchRankClass', () => {
  it('등수별 띠 클래스', () => {
    expect([1, 2, 3, 4, 10, 11].map(matchRankClass)).toEqual([
      'match--win',
      'match--second',
      'match--third',
      'match--top10',
      'match--top10',
      '',
    ]);
  });
});
