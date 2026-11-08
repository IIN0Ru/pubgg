import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FAVORITES_KEY, MAX_RECENT, RECENT_KEY, usePlayerLists, type SavedPlayer } from './players';

const me: SavedPlayer = { platform: 'steam', nickname: 'N0-RU', label: '스팀' };
const duo: SavedPlayer = { platform: 'kakao', nickname: 'Duo', label: '카카오' };

beforeEach(() => {
  localStorage.clear();
  usePlayerLists.getState().reload();
});

describe('즐겨찾기', () => {
  it('누르면 맨 앞에 추가되고, 다시 누르면 빠진다', () => {
    usePlayerLists.getState().toggleFavorite(me);
    usePlayerLists.getState().toggleFavorite(duo);
    expect(usePlayerLists.getState().favorites).toEqual([duo, me]);
    usePlayerLists.getState().toggleFavorite(duo);
    expect(usePlayerLists.getState().favorites).toEqual([me]);
  });

  it('기존 players.js 와 같은 형식(배열)으로 같은 키에 저장한다', () => {
    usePlayerLists.getState().toggleFavorite(me);
    expect(JSON.parse(localStorage.getItem(FAVORITES_KEY) ?? 'null')).toEqual([me]);
  });

  it('닉네임 대소문자는 같은 사람으로 본다', () => {
    usePlayerLists.getState().toggleFavorite(me);
    usePlayerLists.getState().toggleFavorite({ ...me, nickname: 'n0-ru' });
    expect(usePlayerLists.getState().favorites).toEqual([]);
  });

  it('상단 검색창 목록에서 이미 지운 것은 다시 켜지 않고 저장된 값을 기준으로 바꾼다', () => {
    usePlayerLists.getState().toggleFavorite(me);
    localStorage.setItem(FAVORITES_KEY, '[]'); // 기존 스크립트가 지움
    usePlayerLists.getState().toggleFavorite(me);
    expect(usePlayerLists.getState().favorites).toEqual([me]);
  });

  it('기존 스크립트가 저장한 값을 읽는다', () => {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify([me, duo]));
    usePlayerLists.getState().reload();
    expect(usePlayerLists.getState().favorites).toEqual([me, duo]);
    expect(usePlayerLists.getState().isFavorite(duo)).toBe(true);
  });

  it('깨진 저장값은 빈 목록으로 본다', () => {
    localStorage.setItem(FAVORITES_KEY, '{not json');
    localStorage.setItem(RECENT_KEY, JSON.stringify([{ platform: 'xbox', nickname: 1 }, me]));
    usePlayerLists.getState().reload();
    expect(usePlayerLists.getState().favorites).toEqual([]);
    expect(usePlayerLists.getState().recent).toEqual([me]);
  });

  it('저장소가 막혀 있어도 오류 없이 이번 화면에서는 동작한다', () => {
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => usePlayerLists.getState().toggleFavorite(me)).not.toThrow();
    expect(usePlayerLists.getState().favorites).toEqual([me]);
    spy.mockRestore();
  });
});

describe('최근 검색', () => {
  it('최근 것이 맨 앞, 다시 검색하면 끌어올리고 중복은 없다', () => {
    usePlayerLists.getState().remember(me);
    usePlayerLists.getState().remember(duo);
    usePlayerLists.getState().remember(me);
    expect(usePlayerLists.getState().recent).toEqual([me, duo]);
  });

  it(`최대 ${MAX_RECENT}개만 남긴다`, () => {
    for (let i = 0; i < MAX_RECENT + 3; i++) {
      usePlayerLists.getState().remember({ ...me, nickname: `p${i}` });
    }
    const { recent } = usePlayerLists.getState();
    expect(recent).toHaveLength(MAX_RECENT);
    expect(recent[0]?.nickname).toBe(`p${MAX_RECENT + 2}`);
  });
});
