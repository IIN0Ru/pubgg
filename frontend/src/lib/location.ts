import type { Shard } from '../api/types';

export interface SearchTarget {
  shard: Shard;
  nickname: string;
}

/** /search?platform=steam&nickname=N0-RU 에서 검색 대상을 읽는다. 값이 이상하면 null */
export function readSearchTarget(search: string): SearchTarget | null {
  const params = new URLSearchParams(search);
  const platform = params.get('platform');
  const nickname = params.get('nickname')?.trim() ?? '';
  if ((platform !== 'steam' && platform !== 'kakao') || nickname === '') return null;
  return { shard: platform, nickname };
}
