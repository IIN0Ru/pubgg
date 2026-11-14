import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import type { ApiError } from '../api/client';

/** 오류 종류별 제목·안내. 서버가 code 로 알려준 것을 화면이 나눠 보여준다 */
export type ErrorKind = 'not-found' | 'rate-limited' | 'upstream' | 'bad-request';

export function errorKind(error: ApiError): ErrorKind {
  switch (error.code) {
    case 'PLAYER_NOT_FOUND':
      return 'not-found';
    case 'RATE_LIMITED':
      return 'rate-limited';
    case 'BAD_REQUEST':
      return 'bad-request';
    default:
      return 'upstream'; // UPSTREAM_ERROR, NETWORK
  }
}

const TITLES: Record<ErrorKind, string> = {
  'not-found': '플레이어를 찾지 못했어요',
  'rate-limited': '잠시만 기다려 주세요',
  upstream: '전적을 불러오지 못했어요',
  'bad-request': '검색 값을 확인해 주세요',
};

/** 0 이 될 때까지 1초마다 줄어드는 남은 초 */
function useCountdown(seconds: number): number {
  const [left, setLeft] = useState(seconds);
  useEffect(() => {
    setLeft(seconds);
    if (seconds <= 0) return;
    const timer = setInterval(() => setLeft((n) => (n <= 1 ? (clearInterval(timer), 0) : n - 1)), 1000);
    return () => clearInterval(timer);
  }, [seconds]);
  return left;
}

interface Props {
  error: ApiError;
  /** 다시 시도 (같은 주소를 다시 부른다) */
  onRetry: () => void;
  retrying: boolean;
}

/**
 * 검색이 실패했을 때 한 화면.
 *  - 없는 플레이어 / 잘못된 값: 다시 시도해도 같으니 "첫 화면으로"만 준다
 *  - 요청 한도: 남은 초를 세고, 0 이 되면 "다시 시도" 를 누를 수 있다
 *  - 서버·연결 오류: 바로 "다시 시도"
 */
export function ErrorScreen({ error, onRetry, retrying }: Props) {
  const kind = errorKind(error);
  const left = useCountdown(kind === 'rate-limited' ? (error.retryAfterSec ?? 0) : 0);
  const canRetry = kind === 'upstream' || kind === 'rate-limited';
  const waiting = kind === 'rate-limited' && left > 0;

  return (
    <div className="statescreen">
      <section className="notice errorbox" role="alert" data-error-kind={kind}>
        <h1 className="errorbox__title">{TITLES[kind]}</h1>
        {/* 없는 플레이어는 서버 문구가 제목과 같은 말("…찾지 못했어요")이라 확인 방법만 보여준다 */}
        {kind === 'not-found' ? (
          <p className="notice__text">닉네임의 대소문자와 플랫폼(스팀/카카오)을 확인해 주세요.</p>
        ) : (
          <p className="notice__text">{error.message}</p>
        )}
        {kind === 'rate-limited' && (
          <p className="notice__text errorbox__hint" role="status">
            {waiting ? `${left}초 뒤에 다시 시도할 수 있어요.` : '이제 다시 시도할 수 있어요.'}
          </p>
        )}
        <div className="errorbox__actions">
          {canRetry && (
            <button type="button" className="errorbox__button" onClick={onRetry} disabled={waiting || retrying}>
              {retrying ? '불러오는 중…' : '다시 시도'}
            </button>
          )}
          {/* 다시 시도가 없는 오류(없는 플레이어)는 첫 화면이 눈에 띄는 버튼, 있으면 보조 버튼 */}
          <Link className={canRetry ? 'errorbox__button errorbox__button--ghost' : 'errorbox__button'} to="/">
            첫 화면으로
          </Link>
        </div>
      </section>
    </div>
  );
}
