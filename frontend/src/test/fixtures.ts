import type { Match, MatchPlayer, PlayerPage, ShareCard } from '../api/types';

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
