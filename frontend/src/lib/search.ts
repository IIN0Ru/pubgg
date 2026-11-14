import type { Shard } from '../api/types';

/** 플랫폼 목록 (서버 Platform.java 와 같은 순서·이름). 첫 번째(스팀)가 기본 선택 */
export const PLATFORMS: ReadonlyArray<{ shard: Shard; label: string }> = [
  { shard: 'steam', label: '스팀' },
  { shard: 'kakao', label: '카카오' },
];

export const NICKNAME_MAX = 24;

export function isShard(value: string | null | undefined): value is Shard {
  return value === 'steam' || value === 'kakao';
}

export function platformLabel(shard: Shard): string {
  return PLATFORMS.find((p) => p.shard === shard)?.label ?? shard;
}

/** 전적 화면 주소 */
export function searchPath(shard: Shard, nickname: string): string {
  return `/search?${new URLSearchParams({ platform: shard, nickname }).toString()}`;
}

/** 공유 카드 꾸미기 주소 */
export function cardPath(shard: Shard, nickname: string): string {
  return `/card?${new URLSearchParams({ platform: shard, nickname }).toString()}`;
}

/** 2D 리플레이 주소 */
export function replayPath(shard: Shard, matchId: string, accountId: string, nickname: string): string {
  return `/replay?${new URLSearchParams({ platform: shard, match: matchId, player: accountId, nickname }).toString()}`;
}
