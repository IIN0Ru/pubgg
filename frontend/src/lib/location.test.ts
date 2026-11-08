import { describe, expect, it } from 'vitest';
import { readSearchTarget } from './location';

describe('readSearchTarget', () => {
  it('플랫폼과 닉네임을 읽는다 (한글 닉네임 포함)', () => {
    expect(readSearchTarget('?platform=steam&nickname=N0-RU')).toEqual({ shard: 'steam', nickname: 'N0-RU' });
    expect(readSearchTarget('?platform=kakao&nickname=%ED%99%8D%EA%B8%B8%EB%8F%99')).toEqual({
      shard: 'kakao',
      nickname: '홍길동',
    });
  });
  it('모르는 플랫폼이나 빈 닉네임은 null', () => {
    expect(readSearchTarget('?platform=xbox&nickname=a')).toBeNull();
    expect(readSearchTarget('?platform=steam&nickname=%20')).toBeNull();
    expect(readSearchTarget('')).toBeNull();
  });
});
