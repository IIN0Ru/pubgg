import type { Card, Match, MatchPlayer, PlayerPage, Season, ShareCard } from '../api/types';

export function player(overrides: Partial<MatchPlayer> = {}): MatchPlayer {
  return {
    name: 'N0-RU',
    accountId: 'account.me',
    kills: 3,
    knocks: 2,
    assists: 1,
    damage: 412,
    survivedSeconds: 1500,
    headshotKills: 1,
    longestKill: 87,
    revives: 0,
    deathType: 'alive',
    deathTypeLabel: '끝까지 생존',
    ...overrides,
  };
}

export function match(overrides: Partial<Match> = {}): Match {
  return {
    matchId: 'match-1',
    playedAt: new Date(Date.now() - 3 * 3600_000).toISOString(),
    type: 'competitive',
    typeLabel: '경쟁',
    perspective: 'fpp',
    gameMode: 'squad-fpp',
    modeLabel: '스쿼드 FPP',
    mapName: 'Baltic_Main',
    mapLabel: '에란겔',
    rank: 1,
    teams: 25,
    replayAvailable: true,
    me: player(),
    teammates: [player({ name: 'Duo', accountId: 'account.duo', kills: 1 })],
    ...overrides,
  };
}

export const shareCard: ShareCard = {
  nickname: 'N0-RU',
  clan: '',
  tierFamily: 'Platinum',
  tierIcon: 'Platinum-3',
  tierText: 'PLATINUM III',
  kda: '3.42',
  avgDamage: 412,
  mainGun: null,
  subGun: null,
  combatTags: ['센터 운영'],
  matches: 10,
  scope: 'competitive',
};

export function page(overrides: Partial<PlayerPage> = {}): PlayerPage {
  return {
    nickname: 'N0-RU',
    platform: { shard: 'steam', label: '스팀' },
    updatedAt: new Date().toISOString(),
    refreshAvailableInSec: 0,
    cardStatus: 'none',
    season: null,
    card: null,
    shareCard: null,
    seasonComparison: [],
    matches: [],
    teammates: [],
    clan: null,
    ...overrides,
  };
}

export const season: Season = {
  gameMode: 'squad-fpp',
  modeLabel: '스쿼드 FPP',
  tier: 'Platinum',
  subTier: '3',
  tierLabel: '플래티넘 3',
  placed: true,
  rankPoint: 2450,
  rounds: 120,
  kda: 2.76,
  avgDamage: 380,
  avgRank: 5.2,
  top10Percent: 41,
  smallSample: false,
};

export function card(overrides: Partial<Card> = {}): Card {
  return {
    scope: 'competitive',
    matches: 10,
    analyzedMatches: 10,
    perspective: 'FPP',
    kills: 30,
    assists: 5,
    deaths: 9,
    kda: 3.42,
    avgDamage: 412,
    styleTags: ['센터 운영'],
    mainWeapons: [
      { name: 'M416', typeLabel: '5.56 AR', sharePercent: 48 },
      { name: 'Mini 14', typeLabel: 'DMR', sharePercent: 30 },
    ],
    mainThrowable: { name: '수류탄', perMatch: 1.14 },
    avgThrowables: 1.2,
    zone: {
      edgePercent: 38,
      verdict: '센터 운영',
      matchDots: [
        { x: 90, y: 70 },
        { x: 130, y: 120 },
      ],
      marker: { x: 142, y: 78 },
    },
    zoneLuck: { percent: 55, verdict: '보통', matchPercents: [40, 70], bars: [40, 70] },
    farming: {
      farmStyle: '대도시파밍',
      mainCity: '포친키',
      bigCityFarms: 7,
      farmedMatches: 10,
      phase3Matches: 8,
      avgHeals: 7.46,
      avgThrowables: 3.2,
      avgAmmo: 214,
    },
    ...overrides,
  };
}
