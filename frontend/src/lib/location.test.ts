import { describe, expect, it } from 'vitest';
import { readRootTarget, readSearchTarget } from './location';

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

describe('readRootTarget', () => {
  it('서버가 껍데기에 넣어 준 값으로 검색 대상을 만든다 (/s/{id} 는 주소에 닉네임이 없다)', () => {
    expect(readRootTarget({ platform: 'kakao', nickname: ' N0-RU ' })).toEqual({ shard: 'kakao', nickname: 'N0-RU' });
  });

  it('값이 없거나 이상하면 null (그러면 주소의 ?platform=&nickname= 을 본다)', () => {
    expect(readRootTarget({})).toBeNull();
    expect(readRootTarget({ platform: 'xbox', nickname: 'a' })).toBeNull();
    expect(readRootTarget({ platform: 'steam', nickname: '  ' })).toBeNull();
  });
});
