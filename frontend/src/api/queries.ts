import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, fetchPlayer, refreshPlayer } from './client';
import type { PlayerPage, RefreshResult, Shard } from './types';

/** 카드를 계산하는 동안 몇 초마다 다시 물어볼지 (저장된 결과를 읽을 뿐이라 PUBG API 를 부르지 않는다) */
export const CARD_POLL_MS = 3000;

export const playerKey = (shard: Shard, nickname: string) => ['player', shard, nickname.toLowerCase()] as const;

/**
 * 전적. 카드가 pending 이면 끝날 때까지 CARD_POLL_MS 마다 다시 불러온다.
 * 서버가 알려준 오류(없는 플레이어, 요청 한도 등)는 다시 시도해도 같으므로 재시도하지 않는다.
 */
export function usePlayer(shard: Shard, nickname: string) {
  return useQuery<PlayerPage, ApiError>({
    queryKey: playerKey(shard, nickname),
    queryFn: () => fetchPlayer(shard, nickname),
    refetchInterval: (query) => (query.state.data?.cardStatus === 'pending' ? CARD_POLL_MS : false),
    retry: (failureCount, error) => error.code === 'NETWORK' && failureCount < 2,
    staleTime: 30_000,
  });
}

/** 전적 갱신. 끝나면 받은 전적으로 화면 데이터를 바로 바꾼다 */
export function useRefreshPlayer(shard: Shard, nickname: string) {
  const queryClient = useQueryClient();
  return useMutation<RefreshResult, ApiError>({
    mutationFn: () => refreshPlayer(shard, nickname),
    onSuccess: (result) => {
      queryClient.setQueryData(playerKey(shard, nickname), result.player);
    },
  });
}
