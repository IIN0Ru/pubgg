import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useSearchParams } from 'react-router';
import { fetchCard, type ApiError } from '../api/client';
import type { CardPage as CardPageData } from '../api/types';
import { CardEditor } from '../components/card/CardEditor';
import { PageFrame, useDocumentTitle } from '../components/layout/PageFrame';
import { isShard, searchPath } from '../lib/search';

/** /card?platform=steam&nickname=… (&mic=o&time=night … 꾸민 값) */
export function CardPage() {
  const [params] = useSearchParams();
  const platform = params.get('platform');
  const nickname = params.get('nickname')?.trim() ?? '';
  const valid = isShard(platform) && nickname !== '';
  useDocumentTitle(`${nickname} 공유 카드 | 펍지지`);

  const query = useQuery<CardPageData, ApiError>({
    // 대소문자만 다른 닉네임은 같은 플레이어 (서버가 정확한 닉네임으로 돌려준다)
    queryKey: ['card', platform, nickname.toLowerCase()],
    queryFn: () => fetchCard(platform as 'steam' | 'kakao', nickname),
    enabled: valid,
    staleTime: 30_000,
    retry: (count, error) => error.code === 'NETWORK' && count < 2,
  });

  // 주소가 이상하면 첫 화면으로 (서버도 처음 열 때 같은 규칙으로 돌려보낸다)
  if (!valid) return <Navigate to="/" replace />;

  const data = query.data;
  const exact = data?.nickname ?? nickname;

  return (
    <PageFrame shard={platform} nickname={exact}>
      <main className="pcard">
        <Link className="pcard__back" to={searchPath(platform, exact)}>
          ← 전적으로 돌아가기
        </Link>

        {query.isPending && (
          <p className="loading" role="status">
            <span className="loading__spinner" aria-hidden="true" />
            카드를 만드는 중이에요…
          </p>
        )}
        {query.isError && <p className="pcard__notice">{query.error.message}</p>}
        {data && data.card === null && (
          <p className="pcard__notice">
            최근 30경기 안에 경쟁전·일반 경기 기록이 없어서 플레이어 카드를 만들지 못했어요.
          </p>
        )}
        {data?.card && <CardEditor key={data.nickname} card={data.card} shard={platform} />}
      </main>
    </PageFrame>
  );
}
