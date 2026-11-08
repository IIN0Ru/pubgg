import { usePlayer, useRefreshPlayer } from './api/queries';
import type { ApiError } from './api/client';
import type { SearchTarget } from './lib/location';
import { formatCountdown, formatKda, formatRankPoint, formatRelativeTime, resultBadge } from './lib/format';

/**
 * 1주차: API 와 화면이 실제로 이어지는지 확인하는 최소 화면.
 * 2주차에 A카드·경기 목록·필터를 컴포넌트로 나눠 이 자리를 채운다.
 */
export function App({ target }: { target: SearchTarget | null }) {
  if (target === null) {
    return <p role="alert">닉네임과 플랫폼을 확인해 주세요.</p>;
  }
  return <PlayerView target={target} />;
}

function errorText(error: ApiError): string {
  if (error.code === 'RATE_LIMITED' && error.retryAfterSec) {
    return `${error.message} (${error.retryAfterSec}초 뒤)`;
  }
  return error.message;
}

function PlayerView({ target }: { target: SearchTarget }) {
  const { shard, nickname } = target;
  const player = usePlayer(shard, nickname);
  const refresh = useRefreshPlayer(shard, nickname);

  if (player.isPending) {
    return <p role="status">전적을 불러오는 중이에요…</p>;
  }
  if (player.isError) {
    return <p role="alert">{errorText(player.error)}</p>;
  }

  const data = player.data;
  const wait = data.refreshAvailableInSec;
  return (
    <section>
      <h1>{data.nickname}</h1>
      <p>
        {data.platform.label}
        {data.season ? ` · ${data.season.tierLabel}` : ''}
        {data.season?.placed ? ` · ${formatRankPoint(data.season.rankPoint)}` : ''}
      </p>
      <p role="status">
        {data.cardStatus === 'pending' && '플레이 카드를 계산하는 중이에요…'}
        {data.cardStatus === 'ready' && data.card && `최근 ${data.card.matches}경기 KDA ${formatKda(data.card.kda)}`}
        {data.cardStatus === 'none' && '카드를 만들 경기 기록이 없어요.'}
      </p>
      <p>
        갱신 {formatRelativeTime(data.updatedAt)}{' '}
        <button type="button" disabled={refresh.isPending || wait > 0} onClick={() => refresh.mutate()}>
          {wait > 0 ? `${formatCountdown(wait)} 뒤에 갱신 가능` : '전적 갱신'}
        </button>
      </p>
      {refresh.isError && <p role="alert">{errorText(refresh.error)}</p>}
      <ul>
        {data.matches.map((m) => (
          <li key={m.matchId}>
            {m.mapLabel} · {m.modeLabel} · #{m.rank} {resultBadge(m.rank)} · 킬 {m.me.kills} · 딜 {m.me.damage}
          </li>
        ))}
      </ul>
    </section>
  );
}
