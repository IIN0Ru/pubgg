/**
 * 공유 카드(B카드)에 붙이는 성향 태그. 값 이름은 주소(?mic=o&time=night&aim=chicken,rank …)와
 * 서버 저장(CardChoices.java)이 같이 쓴다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
 */

// 성향 태그: 값 → 글자 (순서 = 카드에 붙는 순서)
export const PICKS = {
  mic: { o: '마이크 O', x: '마이크 X', listen: '듣기만' },
  time: { dawn: '새벽 접속', morning: '아침 접속', lunch: '점심 접속', evening: '저녁 접속', night: '밤 접속' },
  aim: { chicken: '치킨지향', rank: '순위지향', social: '친목지향', normal: '일반게임지향', ranked: '랭크게임지향' },
  find: { duo: '듀오 구함', squad: '스쿼드 구함', clan: '클랜원 모집', joinclan: '클랜 구함' },
  role: { igl: 'IGL', front: '1선', second: '2선', turret: '포탑' },
} as const;

export type PickKey = keyof typeof PICKS;
/** 하나만 고르는 것 */
export const SINGLE_PICKS = ['mic'] as const;
/** 여러 개 고르는 것 */
export const MULTI_PICKS = ['time', 'aim', 'find', 'role'] as const;
export type SinglePick = (typeof SINGLE_PICKS)[number];
export type MultiPick = (typeof MULTI_PICKS)[number];

export interface Choices {
  mic: string;
  time: string[];
  aim: string[];
  find: string[];
  role: string[];
}

export const EMPTY_CHOICES: Choices = { mic: '', time: [], aim: [], find: [], role: [] };

const label = (key: PickKey, value: string): string | undefined =>
  (PICKS[key] as Record<string, string>)[value];

/** 주소 값(아무 객체) → 검사한 꾸민 값. 모르는 이름이나 값은 무시된다 */
export function normalizeChoices(get: (key: string) => string | null | undefined): Choices {
  const one = (k: SinglePick) => {
    const v = get(k) ?? '';
    return label(k, v) ? v : '';
  };
  // 목록은 PICKS 순서로 정리하고 겹친 값은 하나만 (같은 꾸밈이면 같은 주소가 되게)
  const list = (k: MultiPick) => {
    const picked = new Set((get(k) ?? '').split(','));
    return Object.keys(PICKS[k]).filter((v) => picked.has(v));
  };
  return { mic: one('mic'), time: list('time'), aim: list('aim'), find: list('find'), role: list('role') };
}

/** 꾸민 값 → 저장·주소용 (안 고른 건 뺌) */
export function choicesToParams(c: Choices): Record<string, string> {
  const out: Record<string, string> = {};
  const set = (k: string, v: string) => {
    if (v) out[k] = v;
  };
  set('mic', c.mic);
  set('time', c.time.join(','));
  set('aim', c.aim.join(','));
  set('find', c.find.join(','));
  set('role', c.role.join(','));
  return out;
}

/** 성향 줄: 마이크 → 접속 시간대 → 지향 → 구함 → 역할 */
export function styleTags(c: Choices): string[] {
  const tags: string[] = [];
  const push = (k: PickKey, v: string) => {
    const text = label(k, v);
    if (text) tags.push(text);
  };
  if (c.mic) push('mic', c.mic);
  c.time.forEach((v) => push('time', v));
  c.aim.forEach((v) => push('aim', v));
  c.find.forEach((v) => push('find', v));
  c.role.forEach((v) => push('role', v));
  return tags;
}
