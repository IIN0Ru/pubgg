import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useSearchParams } from 'react-router';
import { fetchReplaySummary, type ApiError } from '../api/client';
import type { Match } from '../api/types';
import { PageFrame, useDocumentTitle } from '../components/layout/PageFrame';
import { ReplayScreen } from '../components/replay/ReplayScreen';
import { formatPlayedAt } from '../lib/format';
import { isShard, searchPath } from '../lib/search';

/** 경기 ID: 영문·숫자·하이픈만 (PUBG 경기 ID 는 UUID 모양). 서버 ReplayController 와 같은 규칙 */
const MATCH_ID = /^[A-Za-z0-9-]{8,64}$/;
/** 계정 ID: account. 으로 시작 */
const ACCOUNT_ID = /^account\.[A-Za-z0-9]{1,64}$/;

function Title({ match }: { match: Match }) {
  return (
    <>
      <h1 className="replay__title">
        <span>{match.mapLabel}</span>{' '}
        <span className={match.rank === 1 ? 'replay__rank replay__rank--win' : 'replay__rank'}>#{match.rank}</span>{' '}
        <span className="replay__teams">/ {match.teams}</span>
      </h1>
      <p className="replay__meta">
        {match.typeLabel} · {match.modeLabel} · {formatPlayedAt(match.playedAt)} · 킬 {match.me.kills} · 딜량{' '}
        {match.me.damage}
      </p>
    </>
  );
}

/** /replay?platform=steam&match=…&player=account.…&nickname=… (&t=07:02 장면 링크) */
export function ReplayPage() {
  const [params] = useSearchParams();
  const platform = params.get('platform');
  const matchId = params.get('match') ?? '';
  const player = params.get('player') ?? '';
  const nickname = params.get('nickname')?.trim() ?? '';
  const valid = isShard(platform) && MATCH_ID.test(matchId) && ACCOUNT_ID.test(player);
  useDocumentTitle(nickname ? `${nickname} 리플레이 | 펍지지` : '리플레이 | 펍지지');

  const summary = useQuery<Match, ApiError>({
    queryKey: ['replay-summary', platform, matchId, player],
    queryFn: () => fetchReplaySummary(platform as 'steam' | 'kakao', matchId, player),
    enabled: valid,
    staleTime: Infinity,
    retry: (count, error) => error.code === 'NETWORK' && count < 2,
  });

  // 주소가 이상하면 첫 화면으로 (서버도 처음 열 때 같은 규칙으로 돌려보낸다)
  if (!valid) return <Navigate to="/" replace />;

  return (
    <PageFrame shard={platform} nickname={nickname}>
      {/* 2D 리플레이. 데이터는 ReplayScreen 이 /api/replay 로 받아와서 캔버스에 그린다 */}
      <main className="replay">
        <div className="replay__head">
          {nickname !== '' && (
            <Link className="replay__back" to={searchPath(platform, nickname)}>
              ← <span>{nickname}</span> 전적
            </Link>
          )}
          {summary.data && <Title match={summary.data} />}
        </div>

        {summary.isError && (
          <section className="notice">
            <p className="notice__text">{summary.error.message}</p>
          </section>
        )}

        {summary.isPending && (
          <p className="loading" role="status">
            <span className="loading__spinner" aria-hidden="true" />
            경기 기록을 불러오는 중이에요…
          </p>
        )}

        {summary.isSuccess && (
          <ReplayScreen shard={platform} matchId={matchId} player={player} startParam={params.get('t')} />
        )}
      </main>
    </PageFrame>
  );
}
