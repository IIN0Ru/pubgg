import { describe, expect, it } from 'vitest';
import { choicesToParams, normalizeChoices, styleTags } from './choices';

const from = (query: string) => {
  const q = new URLSearchParams(query);
  return normalizeChoices((k) => q.get(k));
};

describe('공유 카드 꾸민 값', () => {
  it('주소 값을 읽고, 모르는 값은 버린다', () => {
    expect(from('mic=o&time=night&aim=chicken,nope&find=duo&role=igl,turret')).toEqual({
      mic: 'o',
      time: 'night',
      aim: ['chicken'],
      find: ['duo'],
      role: ['igl', 'turret'],
    });
    expect(from('mic=loud&time=')).toEqual({ mic: '', time: '', aim: [], find: [], role: [] });
  });

  it('여러 개 고르는 값은 늘 같은 순서로, 겹치면 하나만 (같은 꾸밈이면 같은 주소·같은 카드)', () => {
    expect(from('role=turret,igl,igl').role).toEqual(['igl', 'turret']);
  });

  it('안 고른 건 주소에서 빠진다', () => {
    expect(choicesToParams(from('mic=x&aim=rank,social'))).toEqual({ mic: 'x', aim: 'rank,social' });
  });

  it('카드 태그는 마이크 → 접속 시간대 → 지향 → 구함 → 역할 순', () => {
    expect(styleTags(from('role=front&find=clan&aim=ranked&time=dawn&mic=listen'))).toEqual([
      '듣기만',
      '새벽 접속',
      '랭크게임지향',
      '클랜원 모집',
      '1선',
    ]);
  });
});
