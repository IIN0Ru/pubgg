import type { ReplayData, Track } from '../lib/replay/types';

export function track(overrides: Partial<Track> & Pick<Track, 'id' | 'name' | 'team'>): Track {
  return {
    t: [100, 110, 120],
    x: [1000, 1100, 1200],
    y: [1000, 1000, 1000],
    v: [0, 0, 0],
    hp: [100, 80, 80],
    diedAt: null,
    at: [],
    aa: [],
    ...overrides,
  };
}

/**
 * 작은 가짜 경기: 우리 팀(1팀) 나·Duo, 적(2팀) Enemy.
 *  - 200초에 Enemy 가 Duo 를 기절, 210초에 나가 Duo 를 살림, 300초에 Enemy 가 Duo 를 잡음
 *  - 250초에 나가 Enemy 를 잡음
 */
export function replayData(overrides: Partial<ReplayData> = {}): ReplayData {
  return {
    matchId: 'match-1',
    map: { name: 'Baltic_Main', label: '에란겔', tiles: '/images/maps/tiles/erangel', levels: 5, size: 8000 },
    duration: 600,
    me: 'account.me',
    myTeam: 1,
    players: [
      track({ id: 'account.duo', name: 'Duo', team: 1, t: [90, 400], x: [900, 900], y: [900, 900], diedAt: 300 }),
      track({ id: 'account.me', name: 'Sample-01', team: 1 }),
      track({ id: 'account.enemy', name: 'Enemy', team: 2, t: [100, 250], x: [5000, 5000], y: [5000, 5000], diedAt: 250 }),
    ],
    zones: [
      { t: 0, safeX: 4000, safeY: 4000, safeR: 4000, nextX: 3000, nextY: 3000, nextR: 2000 },
      { t: 100, safeX: 3000, safeY: 3000, safeR: 2000, nextX: 3000, nextY: 3000, nextR: 1000 },
    ],
    phases: [
      { n: 1, t: 120 },
      { n: 2, t: 360 },
    ],
    events: [
      { t: 95, type: 'landing', x: 1000, y: 1000, actor: 'Sample-01', target: null, ours: true, text: 'Sample-01 착지', cause: null },
      { t: 200, type: 'knock', x: 900, y: 900, actor: 'Enemy', target: 'Duo', ours: true, text: 'Enemy → Duo 기절', cause: null },
      { t: 210, type: 'revive', x: 900, y: 900, actor: 'Sample-01', target: 'Duo', ours: true, text: 'Sample-01 → Duo 부활', cause: null },
      { t: 250, type: 'kill', x: 5000, y: 5000, actor: 'Sample-01', target: 'Enemy', ours: true, text: 'Sample-01 → Enemy 킬', cause: null },
      { t: 300, type: 'kill', x: 900, y: 900, actor: 'Enemy', target: 'Duo', ours: true, text: 'Enemy → Duo 킬', cause: null },
    ],
    hits: [],
    plane: null,
    fights: [
      { n: 1, start: 195, end: 255, x: 1000, y: 1000, kills: 1, knocks: 0, knocked: 1, deaths: 0, enemyTeam: 2, converted: 0, losses: 1, traded: 1, revived: 1 },
    ],
    blasts: [],
    heals: [],
    hurts: [
      { t: 190, victim: 'Duo', attacker: 'Enemy', weapon: 'M416', damage: 60, hits: 3, hp: 40, distance: 80, height: 5, headshot: true },
      { t: 198, victim: 'Duo', attacker: 'Enemy', weapon: 'M416', damage: 40, hits: 1, hp: 0, distance: 80, height: 0, headshot: false },
    ],
    ...overrides,
  };
}
