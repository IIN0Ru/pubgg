/*
 * 2D 리플레이 계산 (그리기·화면과 상관없는 부분).
 * 원래 static/js/replay.js 안에 있던 위치·자기장·시야·교전 계산을 떼어 낸 것. 계산 방식은 그대로다.
 * 화면(React)과 캔버스(engine.ts)가 같이 쓰고, 테스트는 replay/model.test.ts.
 */
import type { Fight, Hurt, ReplayData, ReplayEvent, Track, ZoneState } from './types';

export const FAR_FADE = 1.3; // 범위의 1.3배 안은 흐리게 먼저 보여준다 (갑자기 튀어나오지 않게)
export const FIGHT_LEAD_SECONDS = 30; // 우리와 싸울 적은 첫 기절·명중 30초 전부터 거리와 상관없이 보여준다
export const FIGHT_TAIL_SECONDS = 10; // 마지막으로 주고받은 뒤 10초까지
export const DEATH_WINDOW = 30; // 죽기 전 30초

/** 우리 팀 한 명 (나 먼저, 번호는 1부터) */
export interface Member {
  track: Track;
  n: number;
  me: boolean;
}

export interface Model {
  R: ReplayData;
  me: string;
  team: Member[];
  enemies: Track[];
  byName: Map<string, Track>;
  /** 계정 ID → [[기절 시각, 일어나거나 죽은 시각], ...] */
  knockedSpans: Map<string, Array<[number, number]>>;
  /** 우리 팀이 당한 순간 (기절, 또는 기절 없이 바로 사망) */
  deaths: ReplayEvent[];
  /** 우리와 주고받은 적 닉네임 → [처음 주고받은 시각, 마지막 시각] */
  enemyWindows: Map<string, [number, number]>;
}

export function prepareModel(R: ReplayData, me: string): Model {
  const byName = new Map<string, Track>();
  R.players.forEach((p) => byName.set(p.name, p));
  const mine = R.players.filter((p) => p.team === R.myTeam);
  mine.sort((a, b) => (a.id === me ? -1 : 0) - (b.id === me ? -1 : 0) || a.name.localeCompare(b.name));
  const team = mine.map((p, i) => ({ track: p, n: i + 1, me: p.id === me }));
  const enemies = R.players.filter((p) => p.team !== R.myTeam);

  // 기절 구간: 기절 → (부활 또는 죽음 또는 끝)
  const knockedSpans = new Map<string, Array<[number, number]>>();
  R.events.forEach((e) => {
    const target = e.target ? byName.get(e.target) : undefined;
    if (!target) return;
    let spans = knockedSpans.get(target.id);
    if (!spans) {
      spans = [];
      knockedSpans.set(target.id, spans);
    }
    if (e.type === 'knock') spans.push([e.t, R.duration]);
    const last = spans[spans.length - 1];
    if ((e.type === 'revive' || e.type === 'kill') && last && last[1] === R.duration) last[1] = e.t;
  });

  // 우리 팀이 당한 순간
  const deaths: ReplayEvent[] = [];
  const knockedAt = new Map<string, number | null>();
  R.events.forEach((e) => {
    const target = e.target ? byName.get(e.target) : undefined;
    if (!target || target.team !== R.myTeam || !e.target) return;
    if (e.type === 'revive') {
      knockedAt.set(e.target, null);
      return;
    }
    if (e.type === 'knock' || (e.type === 'kill' && (knockedAt.get(e.target) ?? null) === null)) deaths.push(e);
    if (e.type === 'knock') knockedAt.set(e.target, e.t);
  });

  // 우리 팀 기절·킬·명중에 이름이 나온 적
  const enemyWindows = new Map<string, [number, number]>();
  const add = (name: string | null, when: number) => {
    if (!name) return;
    const p = byName.get(name);
    if (!p || p.team === R.myTeam) return;
    const w = enemyWindows.get(name);
    if (w) {
      w[0] = Math.min(w[0], when);
      w[1] = Math.max(w[1], when);
    } else {
      enemyWindows.set(name, [when, when]);
    }
  };
  R.events.forEach((e) => {
    if (!e.ours) return;
    add(e.actor, e.t);
    add(e.target, e.t);
  });
  (R.hits ?? []).forEach((h) => {
    add(h.attacker, h.t);
    add(h.victim, h.t);
  });

  return { R, me, team, enemies, byName, knockedSpans, deaths, enemyWindows };
}

// ---------- 위치 ----------

/** arr[i] <= t 인 마지막 i (없으면 -1) */
export function indexAt(arr: number[], t: number): number {
  let lo = 0;
  let hi = arr.length - 1;
  let ans = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (arr[mid]! <= t) {
      ans = mid;
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }
  return ans;
}

export interface Pos {
  x: number;
  y: number;
  dead: boolean;
  knocked: boolean;
  /** 0 = 걸어서, 1 = 차량, 2 = 보트 */
  vehicle: number;
  heading: number;
  hp: number | null;
  /** m/초 */
  speed: number;
  /** 위치 기록 번호 */
  i: number;
}

export function isKnocked(model: Model, p: Track, t: number): boolean {
  const spans = model.knockedSpans.get(p.id);
  return !!spans && spans.some(([from, to]) => t >= from && t < to);
}

/** t 초에 이 사람이 있는 곳. 아직 기록이 없으면 null. 위치는 약 10초마다 있어서 그 사이는 직선으로 잇는다 */
export function posAt(model: Model, p: Track, t: number): Pos | null {
  const first = p.t[0];
  if (first === undefined || t < first) return null;
  const dead = p.diedAt !== null && p.diedAt !== undefined && t >= p.diedAt;
  const tt = dead ? (p.diedAt as number) : t;
  const i = indexAt(p.t, tt);
  if (i < 0) return null;
  let x = p.x[i]!;
  let y = p.y[i]!;
  let heading = 0;
  let speed = 0;
  const hasNext = i < p.t.length - 1;
  if (hasNext && !dead) {
    const k = (tt - p.t[i]!) / (p.t[i + 1]! - p.t[i]!);
    heading = Math.atan2(p.y[i + 1]! - y, p.x[i + 1]! - x);
    speed = Math.hypot(p.x[i + 1]! - x, p.y[i + 1]! - y) / Math.max(1, p.t[i + 1]! - p.t[i]!);
    x += (p.x[i + 1]! - x) * k;
    y += (p.y[i + 1]! - y) * k;
  } else if (i > 0) {
    heading = Math.atan2(y - p.y[i - 1]!, x - p.x[i - 1]!);
  }
  // 탄 것: 지금 기록 기준. 다음 기록에서 차에 타면 그 사이의 절반이 지난 뒤부터 차량으로 (10초 간격이라 대략)
  let v = p.v ? (p.v[i] ?? 0) : 0;
  if (!v && p.v && i < p.v.length - 1 && !dead) {
    v = p.v[i + 1] && tt - p.t[i]! > (p.t[i + 1]! - p.t[i]!) / 2 ? p.v[i + 1]! : 0;
  }
  // 체력: 맞거나 회복한 순간 바로 바뀌므로 사이를 잇지 않고 마지막 기록 값
  const hp = p.hp ? (p.hp[i] ?? null) : null;
  return { x, y, dead, knocked: !dead && isKnocked(model, p, t), vehicle: dead ? 0 : v, heading, hp, speed, i };
}

export interface Zone {
  safeX: number;
  safeY: number;
  safeR: number;
  nextX: number;
  nextY: number;
  nextR: number;
}

/** t 초의 자기장: 지금 경계(파란 원)는 앞뒤 기록 사이를 이어서 줄어들고, 다음 원(흰 원)은 마지막 기록 */
export function zoneAt(R: ReplayData, t: number): Zone | null {
  const z: ZoneState[] = R.zones;
  if (!z.length) return null;
  const i = indexAt(
    z.map((s) => s.t),
    t,
  );
  if (i < 0) return null;
  const a = z[i]!;
  const b = z[Math.min(i + 1, z.length - 1)]!;
  const k = b.t > a.t ? Math.min(1, (t - a.t) / (b.t - a.t)) : 0;
  return {
    safeX: a.safeX + (b.safeX - a.safeX) * k,
    safeY: a.safeY + (b.safeY - a.safeY) * k,
    safeR: a.safeR + (b.safeR - a.safeR) * k,
    nextX: a.nextX,
    nextY: a.nextY,
    nextR: a.nextR,
  };
}

/** t 초가 몇 페이즈인지 (자기장 전이면 0) */
export function phaseAt(R: ReplayData, t: number): number {
  let n = 0;
  R.phases.forEach((p) => {
    if (p.t <= t) n = p.n;
  });
  return n;
}

export function phaseText(R: ReplayData, t: number): string {
  const n = phaseAt(R, t);
  return n ? `${n}페이즈` : '자기장 전';
}

/** 동선 전체 보기에서 고른 페이즈의 시간 범위 (0 = 전체) */
export function phaseRange(R: ReplayData, phase: number): [number, number] {
  const p = phase ? R.phases[phase - 1] : undefined;
  if (!p) return [0, R.duration];
  const next = R.phases[phase];
  return [p.t, next ? next.t : R.duration];
}

/** 처음 보여줄 시각: 우리 팀 착지 직전. 단, 우리 팀 위치 기록이 처음 생기는 때보다 앞서지 않게 (빈 지도 방지) */
export function firstMoment(model: Model): number {
  const landing = model.R.events.find((e) => e.ours && e.type === 'landing');
  let firstSeen = Infinity;
  model.team.forEach((m) => {
    const t0 = m.track.t[0];
    if (t0 !== undefined) firstSeen = Math.min(firstSeen, t0);
  });
  let t = landing ? landing.t - 5 : 0;
  if (firstSeen !== Infinity) t = Math.max(t, firstSeen);
  return Math.max(0, t);
}

/** 주소의 ?t=07:02 (또는 초) → 그 장면부터. 없거나 이상하면 null */
export function parseTimeParam(v: string | null, duration: number): number | null {
  if (!v) return null;
  const m = /^(\d{1,3}):(\d{2})$/.exec(v);
  const t = m ? Number(m[1]) * 60 + Number(m[2]) : /^\d+$/.test(v) ? Number(v) : NaN;
  return Number.isFinite(t) ? Math.max(0, Math.min(duration, t)) : null;
}

/** 초 → "07:02" */
export function clock(t: number): string {
  const s = Math.max(0, Math.floor(t));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

/** 타임라인 위 위치 (%) */
export function pct(R: ReplayData, t: number): number {
  return R.duration ? (t / R.duration) * 100 : 0;
}

// ---------- 적 표시 ----------

/** 우리 팀과 얼마나 가까운지: 1 = 범위 안, 0.35 = 범위의 1.3배 안, 0 = 멀다. 전체 보기(Infinity)면 늘 1 */
export function nearness(x: number, y: number, teamPos: Array<{ x: number; y: number }>, nearMeters: number): number {
  if (nearMeters === Infinity) return 1;
  let best = Infinity;
  for (const p of teamPos) best = Math.min(best, Math.hypot(p.x - x, p.y - y));
  if (best <= nearMeters) return 1;
  if (best <= nearMeters * FAR_FADE) return 0.35;
  return 0;
}

/**
 * 지금 우리 팀과 싸우는 중이거나 곧 싸울 적.
 * 그 적과 처음 주고받기 30초 전부터 마지막 10초 뒤까지 보여준다.
 * (위치 기록이 10초 간격이라 거리 기준만 쓰면 이미 쏘고 있을 때 나타나서)
 */
export function involvedEnemies(model: Model, t: number): Set<string> {
  const names = new Set<string>();
  model.enemyWindows.forEach(([from, to], name) => {
    if (t >= from - FIGHT_LEAD_SECONDS && t <= to + FIGHT_TAIL_SECONDS) names.add(name);
  });
  return names;
}

// ---------- 추정 시야 ----------
/*
 * 경기 기록에는 보는 방향이 없어서 추정한다.
 *  - 최근 3초 안에 총을 맞혔으면 그 방향 (쏜 사람 → 맞은 사람). 확실하니 좁고 진하게
 *  - 아니면 움직이는 방향 (10초 간격 위치 사이). 넓고 흐리게
 *  - 서 있으면(초당 0.8m 미만) 20초 안에 쏜 방향, 그것도 없으면 마지막으로 움직였던 방향
 */
const AIM_HOLD = 3;
const AIM_REMEMBER = 20;
const STILL_SPEED = 0.8;

export interface Vision {
  /** 방향 (라디안) */
  a: number;
  /** 쏜 방향이면 true (좁고 진하게) */
  shoot: boolean;
}

export function visionAt(p: Track, pos: Pos, t: number): Vision | null {
  const ai = p.at && p.at.length ? indexAt(p.at, t + 0.5) : -1;
  const aim = ai >= 0 && p.aa ? ((p.aa[ai] ?? 0) * Math.PI) / 180 : null;
  const since = ai >= 0 && p.at ? t - p.at[ai]! : Infinity;
  if (aim !== null && since <= AIM_HOLD) return { a: aim, shoot: true };
  if (pos.speed >= STILL_SPEED) return { a: pos.heading, shoot: false };
  if (aim !== null && since <= AIM_REMEMBER) return { a: aim, shoot: false };
  for (let j = Math.min(pos.i, p.t.length - 2); j >= 0 && p.t[pos.i]! - p.t[j]! <= 60; j--) {
    const dx = p.x[j + 1]! - p.x[j]!;
    const dy = p.y[j + 1]! - p.y[j]!;
    if (Math.hypot(dx, dy) >= 5) return { a: Math.atan2(dy, dx), shoot: false };
  }
  return null; // 처음부터 서 있었으면 그리지 않음
}

// ---------- 오른쪽 칸 ----------

export type FeedType = ReplayEvent['type'] | 'phase';
export type FeedGroup = 'move' | 'fight';

export interface FeedItem {
  t: number;
  type: FeedType;
  text: string;
  /** 우리 팀이 한 기절·킬 (노랑 배지) */
  strong: boolean;
  group: FeedGroup;
}

export const TYPE_LABEL: Partial<Record<FeedType, string>> = {
  landing: '착지',
  knock: '기절',
  kill: '킬',
  revive: '부활',
  ride: '차량',
  leave: '하차',
  phase: '자기장',
};

const TYPE_GROUP: Partial<Record<FeedType, FeedGroup>> = { knock: 'fight', kill: 'fight', revive: 'fight' };

/** 경기 흐름: 우리 팀 사건 + 페이즈 시작, 시각 순 */
export function feedItems(model: Model): FeedItem[] {
  const { R, byName } = model;
  const items: FeedItem[] = R.events
    .filter((e) => e.ours)
    .map((e) => {
      const actor = e.actor ? byName.get(e.actor) : undefined;
      const strong = (e.type === 'kill' || e.type === 'knock') && !!actor && actor.team === R.myTeam;
      return { t: e.t, type: e.type, text: e.text, strong, group: TYPE_GROUP[e.type] ?? 'move' };
    });
  R.phases.forEach((p) => items.push({ t: p.t, type: 'phase', text: `${p.n}페이즈 시작`, strong: false, group: 'move' }));
  // 같은 시각이면 원래 순서 그대로 (Array.sort 는 안정 정렬)
  return items.sort((a, b) => a.t - b.t);
}

export interface MemberStatus {
  n: number;
  name: string;
  me: boolean;
  dead: boolean;
  /** 비행기 / 사망 / 기절 / 생존 */
  status: string;
  kills: number;
}

/** 우리 팀 칸: t 초의 상태와 그때까지 킬 */
export function teamStatus(model: Model, t: number): { members: MemberStatus[]; alive: number } {
  let alive = 0;
  const members = model.team.map((m) => {
    const pos = posAt(model, m.track, t);
    const status = !pos ? '비행기' : pos.dead ? '사망' : pos.knocked ? '기절' : '생존';
    if (pos && !pos.dead) alive++;
    const kills = model.R.events.filter((e) => e.type === 'kill' && e.t <= t && e.actor === m.track.name).length;
    return { n: m.n, name: m.track.name, me: m.me, dead: !!pos?.dead, status, kills };
  });
  return { members, alive };
}

export interface DeathCard {
  event: ReplayEvent;
  /** 기절 / 사망 */
  label: string;
  hurts: Hurt[];
  /** "기절 전 30초 · 받은 피해 120 · 적 2명 · 8초 동안" */
  summary: string;
  /** "그 장면 보기"를 누르면 갈 시각 */
  goTo: number;
}

/** 죽기 전 30초: t 초까지 우리 팀이 마지막으로 당한 순간과 그 전에 받은 피해. 없으면 null */
export function deathAt(model: Model, t: number): DeathCard | null {
  let d: ReplayEvent | null = null;
  for (const e of model.deaths) {
    if (e.t > t) break;
    d = e;
  }
  if (!d) return null;
  const event = d;
  const hurts = (model.R.hurts ?? []).filter(
    (h) => h.victim === event.target && h.t >= event.t - DEATH_WINDOW && h.t <= event.t,
  );
  const label = event.type === 'knock' ? '기절' : '사망';
  let total = 0;
  const who = new Set<string>();
  hurts.forEach((h) => {
    total += h.damage;
    const a = h.attacker ? model.byName.get(h.attacker) : undefined;
    if (a && a.team !== model.R.myTeam && h.attacker) who.add(h.attacker);
  });
  const first = hurts[0];
  const summary = first
    ? `${label} 전 30초 · 받은 피해 ${total}${who.size ? ` · 적 ${who.size}명` : ''} · ${Math.max(1, event.t - first.t)}초 동안`
    : '기록된 피해가 없어요.';
  return { event, label, hurts, summary, goTo: Math.max(0, (first ? first.t : event.t) - 3) };
}

/** 받은 피해 한 줄의 덧붙임: "5m 높은 곳에서 · 헤드샷 · 3번" */
export function hurtNotes(h: Hurt): string[] {
  const notes: string[] = [];
  if (h.attacker && Math.abs(h.height) >= 3) notes.push(`${Math.abs(h.height)}m ${h.height > 0 ? '높은' : '낮은'} 곳에서`);
  if (h.headshot) notes.push('헤드샷');
  if (h.hits > 1) notes.push(`${h.hits}번`);
  return notes;
}

export interface FightTotals {
  knocks: number;
  converted: number;
  losses: number;
  traded: number;
  knocked: number;
  revived: number;
}

/** 교전 지표: 이 판 우리 팀 합계 */
export function fightTotals(fights: Fight[]): FightTotals {
  const sum = (key: keyof FightTotals) => fights.reduce((a, f) => a + (f[key] || 0), 0);
  return {
    knocks: sum('knocks'),
    converted: sum('converted'),
    losses: sum('losses'),
    traded: sum('traded'),
    knocked: sum('knocked'),
    revived: sum('revived'),
  };
}

/** 교전 한 줄의 지표: "기절→킬 2/3 · 복수 1/1" */
export function fightParts(f: Fight): string[] {
  const parts: string[] = [];
  if (f.knocks) parts.push(`기절→킬 ${f.converted}/${f.knocks}`);
  if (f.losses) parts.push(`복수 ${f.traded}/${f.losses}`);
  if (f.knocked) parts.push(`살림 ${f.revived}/${f.knocked}`);
  return parts;
}

/** 교전 결과: 킬이 많으면 승, 죽은 게 많으면 패, 같으면 빈칸 */
export function fightResult(f: Fight): '승' | '패' | '' {
  return f.kills > f.deaths ? '승' : f.deaths > f.kills ? '패' : '';
}

/** 동선 전체 보기의 교전 한 줄: "2킬 · 기절시킴 3" */
export function fightSummary(f: Fight): string {
  const result: string[] = [];
  if (f.kills) result.push(`${f.kills}킬`);
  if (f.knocks) result.push(`기절시킴 ${f.knocks}`);
  if (f.knocked) result.push(`기절당함 ${f.knocked}`);
  if (f.deaths) result.push(`사망 ${f.deaths}`);
  return result.join(' · ') || '피해만 오감';
}

/** 적 표시 범위 안내: 500m 안 / 1km 안 / 모든 적 */
export function rangeLabel(nearMeters: number): string {
  if (nearMeters === Infinity) return '모든 적';
  return `${nearMeters >= 1000 ? `${nearMeters / 1000}km` : `${nearMeters}m`} 안`;
}
