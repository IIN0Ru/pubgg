import type { SearchTarget } from './lib/location';
import { PlayerScreen } from './components/PlayerScreen';

/** 주소(?platform=&nickname=)에서 읽은 검색 대상으로 전적 화면을 그린다 */
export function App({ target }: { target: SearchTarget | null }) {
  if (target === null) {
    return (
      <div className="feed">
        <p className="idcard__notice" role="alert">
          닉네임과 플랫폼을 확인해 주세요.
        </p>
      </div>
    );
  }
  return <PlayerScreen target={target} />;
}
