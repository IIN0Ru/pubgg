import { useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { ErrorScreen } from '../components/ErrorScreen';
import { PageFrame, useDocumentTitle } from '../components/layout/PageFrame';
import { PlayerScreen } from '../components/PlayerScreen';
import { readRootTarget, readSearchTarget, type SearchTarget } from '../lib/location';

/** 전적 화면 본문. 검색 대상이 이상하면 안내만 */
export function PlayerView({ target }: { target: SearchTarget | null }) {
  useDocumentTitle(target ? `${target.nickname} | 펍지지` : '펍지지 PUB.GG');
  return (
    <PageFrame shard={target?.shard} nickname={target?.nickname}>
      {/* 왼쪽 카드 / 경기 목록 / 보조 정보 세 칸. 배치는 main.css 의 .page 격자 */}
      <main className="page">
        {target === null ? (
          <ErrorScreen
            error={new ApiError('BAD_REQUEST', '닉네임과 플랫폼을 확인해 주세요.', 400)}
            onRetry={() => undefined}
            retrying={false}
          />
        ) : (
          <PlayerScreen key={`${target.shard}:${target.nickname}`} target={target} />
        )}
      </main>
    </PageFrame>
  );
}

/** /search?platform=steam&nickname=… */
export function SearchPage() {
  const [params] = useSearchParams();
  return <PlayerView target={readSearchTarget(params.toString())} />;
}

/**
 * 공유 주소 /s/{id}. 주소에 닉네임이 없어서 서버가 껍데기(#root)에 넣어 준 플레이어를 읽는다.
 * 이 주소는 늘 처음부터 열리고(디스코드·카톡 링크), 화면 안에서 /s/ 로 넘어가는 링크는 없다.
 */
export function SharedPage() {
  const root = document.getElementById('root');
  return <PlayerView target={root ? readRootTarget(root.dataset) : null} />;
}
