import type { SearchTarget } from './lib/location';
import { ApiError } from './api/client';
import { ErrorScreen } from './components/ErrorScreen';
import { PlayerScreen } from './components/PlayerScreen';

/** 주소(?platform=&nickname=)에서 읽은 검색 대상으로 전적 화면을 그린다 */
export function App({ target }: { target: SearchTarget | null }) {
  if (target === null) {
    return (
      <ErrorScreen
        error={new ApiError('BAD_REQUEST', '닉네임과 플랫폼을 확인해 주세요.', 400)}
        onRetry={() => undefined}
        retrying={false}
      />
    );
  }
  return <PlayerScreen target={target} />;
}
