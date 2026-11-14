import { describe, expect, it } from 'vitest';
import { replayData, track } from '../../test/replayFixture';
import {
  clock,
  deathAt,
  feedItems,
  fightTotals,
  firstMoment,
  involvedEnemies,
  nearness,
  parseTimeParam,
  phaseAt,
  phaseRange,
  posAt,
  prepareModel,
  rangeLabel,
  teamStatus,
  visionAt,
  zoneAt,
} from './model';

const model = () => prepareModel(replayData(), 'account.me');

describe('리플레이 계산', () => {
  it('우리 팀은 나를 1번으로, 나머지는 이름 순', () => {
    const m = model();
    expect(m.team.map((t) => [t.n, t.track.name, t.me])).toEqual([
      [1, 'Sample-01', true],
      [2, 'Duo', false],
    ]);
    expect(m.enemies.map((e) => e.name)).toEqual(['Enemy']);
  });

  it('위치는 기록 사이를 직선으로 잇고, 기록 전이면 없고, 죽은 뒤에는 그 자리', () => {
    const m = model();
    const me = m.byName.get('Sample-01')!;
    expect(posAt(m, me, 99)).toBeNull();
    const mid = posAt(m, me, 105)!;
    expect(mid.x).toBeCloseTo(1050);
    expect(mid.speed).toBeCloseTo(10);
    const duo = m.byName.get('Duo')!;
    const dead = posAt(m, duo, 500)!;
    expect(dead.dead).toBe(true);
    expect(dead.vehicle).toBe(0);
  });

  it('기절 구간은 기절부터 부활·죽음까지', () => {
    const m = model();
    const duo = m.byName.get('Duo')!;
    expect(posAt(m, duo, 205)!.knocked).toBe(true);
    expect(posAt(m, duo, 215)!.knocked).toBe(false);
  });

  it('자기장 경계는 기록 사이에서 줄어들고, 다음 원은 마지막 기록 그대로', () => {
    const z = zoneAt(replayData(), 50)!;
    expect(z.safeR).toBe(3000);
    expect(z.nextR).toBe(2000);
    expect(zoneAt(replayData({ zones: [] }), 50)).toBeNull();
  });

  it('페이즈와 동선 전체 보기 범위', () => {
    const R = replayData();
    expect(phaseAt(R, 100)).toBe(0);
    expect(phaseAt(R, 400)).toBe(2);
    expect(phaseRange(R, 0)).toEqual([0, 600]);
    expect(phaseRange(R, 1)).toEqual([120, 360]);
    expect(phaseRange(R, 2)).toEqual([360, 600]);
  });

  it('처음 시각은 착지 5초 전이지만 우리 팀 기록이 생기기 전으로는 가지 않는다', () => {
    expect(firstMoment(model())).toBe(90);
  });

  it('장면 링크 시각: 07:02 또는 초, 이상하면 null, 경기 길이 안으로', () => {
    expect(parseTimeParam('07:02', 600)).toBe(422);
    expect(parseTimeParam('30', 600)).toBe(30);
    expect(parseTimeParam('99:00', 600)).toBe(600);
    expect(parseTimeParam('abc', 600)).toBeNull();
    expect(parseTimeParam(null, 600)).toBeNull();
    expect(clock(422.9)).toBe('07:02');
  });

  it('적 표시: 범위 안 1, 조금 넘으면 흐리게 0.35, 멀면 0, 전체면 늘 1', () => {
    const team = [{ x: 0, y: 0 }];
    expect(nearness(400, 0, team, 500)).toBe(1);
    expect(nearness(600, 0, team, 500)).toBe(0.35);
    expect(nearness(900, 0, team, 500)).toBe(0);
    expect(nearness(9000, 0, team, Infinity)).toBe(1);
    expect(rangeLabel(1000)).toBe('1km 안');
    expect(rangeLabel(Infinity)).toBe('모든 적');
  });

  it('우리와 싸운 적은 처음 주고받기 30초 전부터 마지막 10초 뒤까지 거리와 상관없이 보여준다', () => {
    const m = model();
    expect(involvedEnemies(m, 160).has('Enemy')).toBe(false);
    expect(involvedEnemies(m, 175).has('Enemy')).toBe(true);
    expect(involvedEnemies(m, 310).has('Enemy')).toBe(true);
    expect(involvedEnemies(m, 311).has('Enemy')).toBe(false);
  });

  it('시야: 3초 안에 쐈으면 쏜 방향, 아니면 움직이는 방향', () => {
    const shooter = track({ id: 'a', name: 'A', team: 1, at: [104], aa: [90] });
    const m = prepareModel(replayData({ players: [shooter] }), 'a');
    const pos = posAt(m, shooter, 105)!;
    expect(visionAt(shooter, pos, 105)).toEqual({ a: Math.PI / 2, shoot: true });
    expect(visionAt(shooter, pos, 115)).toEqual({ a: 0, shoot: false });
  });

  it('경기 흐름: 우리 팀 사건 + 페이즈 시작, 우리가 한 킬은 강조', () => {
    const items = feedItems(model());
    expect(items.map((i) => i.type)).toEqual(['landing', 'phase', 'knock', 'revive', 'kill', 'kill', 'phase']);
    expect(items.find((i) => i.t === 250)!.strong).toBe(true);
    expect(items.find((i) => i.t === 300)!.strong).toBe(false);
    expect(items.find((i) => i.type === 'knock')!.group).toBe('fight');
  });

  it('우리 팀 칸: 상태와 그때까지 킬', () => {
    const m = model();
    expect(teamStatus(m, 80).members.map((x) => x.status)).toEqual(['비행기', '비행기']);
    const later = teamStatus(m, 320);
    expect(later.members.map((x) => [x.status, x.kills])).toEqual([
      ['생존', 1],
      ['사망', 0],
    ]);
    expect(later.alive).toBe(1);
  });

  it('죽기 전 30초: 마지막으로 당한 순간과 그 전 피해', () => {
    const m = model();
    expect(deathAt(m, 150)).toBeNull();
    const knock = deathAt(m, 205)!;
    expect(knock.label).toBe('기절');
    expect(knock.summary).toBe('기절 전 30초 · 받은 피해 100 · 적 1명 · 10초 동안');
    expect(knock.goTo).toBe(187);
    // 살린 뒤 기절 없이 바로 죽은 것도 당한 순간
    expect(deathAt(m, 300)!.label).toBe('사망');
  });

  it('교전 합계', () => {
    expect(fightTotals(replayData().fights)).toEqual({ knocks: 0, converted: 0, losses: 1, traded: 1, knocked: 1, revived: 1 });
  });
});
