import { useEffect, useState } from 'react';
import type { ApiError } from '../api/client';
import { usePlayer, useRefreshPlayer } from '../api/queries';
import type { SearchTarget } from '../lib/location';
import { DetailsPanel } from './DetailsPanel';
import { ErrorScreen } from './ErrorScreen';
import { IdCard } from './IdCard';
import { useMatchFilters } from '../store/matchFilters';
import { usePlayerLists } from '../store/players';
import { MatchList } from './MatchList';
import { CompareBox, TeammatesBox } from './SideBoxes';

export function errorText(error: ApiError): string {
  if (error.code === 'RATE_LIMITED' && error.retryAfterSec) {
    return `${error.message} (${error.retryAfterSec}초 뒤)`;
  }
  return error.message;
}

/** 검색 결과 한 화면: 왼쪽 카드 / 가운데 경기 목록 / (3주차) 보조 정보. 세 칸은 main.css 의 .page 격자가 배치한다 */
export function PlayerScreen({ target }: { target: SearchTarget }) {
  const { shard, nickname } = target;
  const player = usePlayer(shard, nickname);
  const refresh = useRefreshPlayer(shard, nickname);
  const [notice, setNotice] = useState<string | null>(null);
  const remember = usePlayerLists((s) => s.remember);
  const resetFilters = useMatchFilters((s) => s.reset);

  // 다른 플레이어를 열면 필터와 "더 보기"를 처음 상태로 되돌린다
  useEffect(() => {
    resetFilters();
  }, [shard, nickname, resetFilters]);

  // 전적을 받아오면 최근 검색에 기록한다 (서버가 알려준 정확한 닉네임·플랫폼 이름으로)
  const loaded = player.data;
  useEffect(() => {
    if (loaded) remember({ platform: loaded.platform.shard, nickname: loaded.nickname, label: loaded.platform.label });
  }, [loaded?.nickname, loaded?.platform.shard, loaded?.platform.label, remember]);

  if (player.isPending) {
    return (
      <div className="statescreen">
        <p className="loading" role="status">
          <span className="loading__spinner" aria-hidden="true" />
          전적을 불러오는 중이에요…
        </p>
      </div>
    );
  }
  if (player.isError) {
    return <ErrorScreen error={player.error} onRetry={() => void player.refetch()} retrying={player.isFetching} />;
  }

  const data = player.data;
  const onRefresh = () => {
    setNotice(null);
    refresh.mutate(undefined, {
      onSuccess: (result) =>
        setNotice(result.refreshed ? null : '방금 갱신했어요. 잠시 뒤에 다시 갱신할 수 있어요.'),
    });
  };

  return (
    <>
      <aside className="side side--card">
        <IdCard
          player={data}
          refreshing={refresh.isPending}
          refreshMessage={refresh.isError ? errorText(refresh.error) : notice}
          onRefresh={onRefresh}
        />
      </aside>
      <div className="feed">
        {data.card && <DetailsPanel card={data.card} />}
        <MatchList matches={data.matches} shard={data.platform.shard} nickname={data.nickname} />
      </div>
      <aside className="side side--extra">
        {data.card && data.season && data.seasonComparison.length > 0 && (
          <CompareBox rows={data.seasonComparison} card={data.card} season={data.season} />
        )}
        {data.matches.length > 0 && <TeammatesBox teammates={data.teammates} shard={data.platform.shard} />}
      </aside>
    </>
  );
}
