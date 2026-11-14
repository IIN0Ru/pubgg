// 서버 Replay.java (src/main/java/gg/pub/domain/replay/Replay.java) 와 같은 모양.
// 좌표는 m 단위 정수 (지도 왼쪽 위 0,0), 시각은 경기 시작부터 초.

export interface ReplayMap {
  name: string;
  label: string;
  /** 지도 조각 폴더 주소 (/images/maps/tiles/erangel) */
  tiles: string;
  /** 조각 단계 수 (0 ~ levels-1) */
  levels: number;
  /** 지도 한 변 (m) */
  size: number;
}

/** 한 사람의 이동 기록. 같은 순서의 t[i], x[i], y[i] 가 한 점 */
export interface Track {
  id: string;
  name: string;
  team: number;
  t: number[];
  x: number[];
  y: number[];
  /** 그 순간 탄 것: 0 = 걸어서, 1 = 차량, 2 = 보트 */
  v: number[] | null;
  hp: number[] | null;
  diedAt: number | null;
  /** 총을 맞힌 시각들, 그때 쏜 방향(도) */
  at: number[] | null;
  aa: number[] | null;
}

export interface ZoneState {
  t: number;
  safeX: number;
  safeY: number;
  safeR: number;
  nextX: number;
  nextY: number;
  nextR: number;
}

export interface Phase {
  n: number;
  t: number;
}

export type EventType = 'landing' | 'knock' | 'kill' | 'revive' | 'ride' | 'leave' | 'package';

export interface ReplayEvent {
  t: number;
  type: EventType;
  x: number;
  y: number;
  actor: string | null;
  target: string | null;
  ours: boolean;
  text: string;
  cause: string | null;
}

export interface Hit {
  t: number;
  ax: number;
  ay: number;
  vx: number;
  vy: number;
  ours: boolean;
  attacker: string;
  victim: string;
}

export interface Plane {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface Fight {
  n: number;
  start: number;
  end: number;
  x: number;
  y: number;
  kills: number;
  knocks: number;
  knocked: number;
  deaths: number;
  enemyTeam: number;
  converted: number;
  losses: number;
  traded: number;
  revived: number;
}

export type BlastKind = 'grenade' | 'molotov' | 'c4' | 'sticky' | 'mortar' | 'panzer';

export interface Blast {
  t: number;
  kind: BlastKind;
  fromX: number;
  fromY: number;
  x: number;
  y: number;
  attacker: string;
  result: 'kill' | 'knock' | 'hit';
  victims: number;
}

export interface Heal {
  t: number;
  name: string;
  x: number;
  y: number;
  item: string;
}

export interface Hurt {
  t: number;
  victim: string;
  attacker: string | null;
  weapon: string;
  damage: number;
  hits: number;
  hp: number;
  distance: number;
  height: number;
  headshot: boolean;
}

export interface ReplayData {
  matchId: string;
  map: ReplayMap;
  duration: number;
  me: string;
  myTeam: number;
  players: Track[];
  zones: ZoneState[];
  phases: Phase[];
  events: ReplayEvent[];
  hits: Hit[] | null;
  plane: Plane | null;
  fights: Fight[];
  blasts: Blast[] | null;
  heals: Heal[] | null;
  hurts: Hurt[] | null;
}
