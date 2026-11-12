import type { Shard } from '../api/types';

export interface SearchTarget {
  shard: Shard;
  nickname: string;
}

/**
 * 서버가 껍데기에 넣어 준 검색 대상(<main id="root" data-platform data-nickname>).
 * /s/{id} 는 주소에 닉네임이 없어서 이 값으로 알아낸다. 없거나 이상하면 null
 */
export function readRootTarget(data: { platform?: string; nickname?: string }): SearchTarget | null {
  const nickname = data.nickname?.trim() ?? '';
  if ((data.platform !== 'steam' && data.platform !== 'kakao') || nickname === '') return null;
  return { shard: data.platform, nickname };
}

/** /search?platform=steam&nickname=Sample-01 에서 검색 대상을 읽는다. 값이 이상하면 null */
export function readSearchTarget(search: string): SearchTarget | null {
  const params = new URLSearchParams(search);
  const platform = params.get('platform');
  const nickname = params.get('nickname')?.trim() ?? '';
  if ((platform !== 'steam' && platform !== 'kakao') || nickname === '') return null;
  return { shard: platform, nickname };
}
