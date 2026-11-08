import { useState } from 'react';
import type { ApiError } from '../api/client';
import { usePlayer, useRefreshPlayer } from '../api/queries';
import type { SearchTarget } from '../lib/location';
import { IdCard } from './IdCard';
import { MatchList } from './MatchList';

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

  if (player.isPending) {
    return (
      <div className="feed">
        <p className="recent__empty" role="status">
          전적을 불러오는 중이에요…
        </p>
      </div>
    );
  }
  if (player.isError) {
    return (
      <div className="feed">
        <p className="idcard__notice" role="alert">
          {errorText(player.error)}
        </p>
      </div>
    );
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
        <MatchList matches={data.matches} shard={data.platform.shard} nickname={data.nickname} />
      </div>
      <aside className="side side--extra" />
    </>
  );
}
