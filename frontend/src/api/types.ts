// Spring 의 PlayerPageResponse (src/main/java/gg/pub/web/api/PlayerPageResponse.java) 와 같은 모양.
// 한쪽을 바꾸면 다른 쪽도 바꾼다. 서버 쪽은 PlayerApiControllerTest 가 이 모양을 지켜 준다.

export type Shard = 'steam' | 'kakao';

/** ready: 카드 있음 / pending: 계산 중 (같은 주소를 다시 부르면 됨) / none: 만들 기록이 없음 */
export type CardStatus = 'ready' | 'pending' | 'none';

export interface Platform {
  shard: Shard;
  label: string;
}

export interface Season {
  gameMode: string;
  modeLabel: string;
  tier: string | null;
  subTier: string | null;
  /** "플래티넘 3", 배치 전이면 "배치 중" */
  tierLabel: string;
  placed: boolean;
  rankPoint: number;
  rounds: number;
  kda: number;
  avgDamage: number;
  avgRank: number;
  top10Percent: number;
  smallSample: boolean;
}

export interface Comparison {
  label: string;
  recent: string;
  season: string;
  /** 최근이 시즌보다 높으면 1, 낮으면 -1, 같으면 0 */
  trend: -1 | 0 | 1;
}

export interface Weapon {
  name: string;
  typeLabel: string;
  sharePercent: number;
}

export interface Throwable {
  name: string;
  perMatch: number;
}

export interface Dot {
  x: number;
  y: number;
}

export interface Zone {
  /** 0 = 중심, 100 = 경계 */
  edgePercent: number;
  verdict: string;
  /** 220x220 미니맵 위 좌표 (안전구역 중심 110,110 · 반지름 92) */
  matchDots: Dot[];
  marker: Dot;
}

export interface ZoneLuck {
  /** 100 = 중심, 0 = 경계·밖 */
  percent: number;
  verdict: string;
  bars: number[];
}

export interface Farming {
  farmStyle: string | null;
  mainCity: string | null;
  bigCityFarms: number;
  farmedMatches: number;
  phase3Matches: number;
  avgHeals: number;
  avgThrowables: number;
  avgAmmo: number;
}

export interface Card {
  /** competitive: 경쟁전 / normal: 일반 (경쟁전이 한 판도 없을 때만) */
  scope: 'competitive' | 'normal';
  matches: number;
  analyzedMatches: number;
  perspective: string;
  kills: number;
  assists: number;
  deaths: number;
  kda: number;
  avgDamage: number;
  styleTags: string[];
  mainWeapons: Weapon[];
  mainThrowable: Throwable | null;
  avgThrowables: number;
  zone: Zone | null;
  zoneLuck: ZoneLuck | null;
  farming: Farming | null;
}

/** A카드 캔버스(720x960)에 그릴 값. 기존 static/js/card-profile.js 가 그대로 받는다 */
export interface ShareCard {
  nickname: string;
  clan: string;
  tierFamily: string;
  tierIcon: string;
  tierText: string;
  kda: string;
  avgDamage: number;
  mainGun: Gun | null;
  subGun: Gun | null;
  combatTags: string[];
  matches: number;
  scope: 'competitive' | 'normal';
}

export interface Gun {
  name: string;
  type: string;
  image: string;
  share: number;
}

export interface MatchPlayer {
  name: string;
  accountId: string;
  kills: number;
  knocks: number;
  assists: number;
  damage: number;
  survivedSeconds: number;
  headshotKills: number;
  longestKill: number;
  revives: number;
  deathType: string;
  deathTypeLabel: string;
}

export type MatchType = 'competitive' | 'official' | 'other';
export type Perspective = 'fpp' | 'tpp';

export interface Match {
  matchId: string;
  /** ISO-8601 */
  playedAt: string;
  type: MatchType;
  typeLabel: string;
  perspective: Perspective;
  gameMode: string;
  modeLabel: string;
  mapName: string;
  mapLabel: string;
  rank: number;
  /** 2D 리플레이를 볼 수 있는 경기 (경쟁전) */
  replayAvailable: boolean;
  me: MatchPlayer;
  teammates: MatchPlayer[];
}

export interface Teammate {
  name: string;
  games: number;
  wins: number;
  avgRank: number;
}

export interface Clan {
  name: string;
  tag: string | null;
  /** "[HLMT] 헬멧연구소" */
  label: string;
}

export interface PlayerPage {
  nickname: string;
  platform: Platform;
  /** ISO-8601. 마지막으로 PUBG API 에서 불러온 시각 */
  updatedAt: string;
  /** 다시 갱신할 수 있을 때까지 남은 초 (0 이면 지금 가능) */
  refreshAvailableInSec: number;
  cardStatus: CardStatus;
  season: Season | null;
  card: Card | null;
  shareCard: ShareCard | null;
  seasonComparison: Comparison[];
  matches: Match[];
  teammates: Teammate[];
  clan: Clan | null;
}

export interface RefreshResult {
  /** 실제로 새로 불러왔으면 true, 2분이 안 지나 저장본을 그대로 받았으면 false */
  refreshed: boolean;
  player: PlayerPage;
}

/** 서버가 실패했을 때 돌려주는 본문 (ApiError.java) */
export type ApiErrorCode = 'BAD_REQUEST' | 'PLAYER_NOT_FOUND' | 'RATE_LIMITED' | 'UPSTREAM_ERROR';

export interface ApiErrorBody {
  code: ApiErrorCode;
  message: string;
  retryAfterSec?: number;
}
