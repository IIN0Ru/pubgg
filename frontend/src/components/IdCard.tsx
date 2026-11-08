import { useEffect, useState } from 'react';
import type { PlayerPage } from '../api/types';
import { formatCountdown, formatRankPoint, formatRelativeTime } from '../lib/format';
import { PlayerCanvas } from './PlayerCanvas';

interface Props {
  player: PlayerPage;
  refreshing: boolean;
  refreshMessage: string | null;
  onRefresh: () => void;
}

/** 왼쪽 위 칸: 플레이어 카드(A카드) + 카드 아래 한 줄(플랫폼 · RP · 갱신) + 공유 버튼 */
export function IdCard({ player, refreshing, refreshMessage, onRefresh }: Props) {
  const cardHref = `/card?${new URLSearchParams({ platform: player.platform.shard, nickname: player.nickname }).toString()}`;
  const wait = useCountdown(player.refreshAvailableInSec, player.updatedAt);
  const season = player.season;

  const bar = (
    <>
      <span className="refresh__ago">갱신 {formatRelativeTime(player.updatedAt)}</span>
      {refreshing && (
        <span className="refresh__ago" role="status">
          · 갱신 중…
        </span>
      )}
      <button
        className="refresh__button"
        type="button"
        aria-label="전적 갱신"
        title={wait > 0 ? `${formatCountdown(wait)} 뒤에 다시 갱신할 수 있어요` : '전적 갱신'}
        disabled={refreshing || wait > 0}
        onClick={onRefresh}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M20 11a8 8 0 1 0-2.3 5.7" />
          <path d="M20 4v7h-7" />
        </svg>
      </button>
    </>
  );

  if (player.shareCard !== null) {
    return (
      <section className="idcard idcard--player">
        <h1 className="sr-only">{player.nickname}</h1>
        <a className="idcard__card" href={cardHref} aria-label="공유 카드 만들기">
          <PlayerCanvas card={player.shareCard} />
        </a>

        <div className="idcard__bar">
          <span className="idcard__platform">{player.platform.label}</span>
          {season?.placed && <span className="idcard__rp">{formatRankPoint(season.rankPoint)}</span>}
          <div className="refresh">{bar}</div>
        </div>
        {refreshMessage !== null && (
          <p className="refresh__message" role="status">
            {refreshMessage}
          </p>
        )}

        <div className="idcard__actions">
          <a className="idcard__make" href={cardHref}>
            공유 카드 만들기
          </a>
          <CopyLinkButton />
        </div>
      </section>
    );
  }

  // 카드가 아직 없다: 계산 중이거나, 만들 경기 기록이 없다
  return (
    <section className="idcard">
      <div className="idcard__meta">
        <span className="idcard__platform">{player.platform.label}</span>
      </div>
      <div className="idcard__head">
        <h1 className="idcard__name">{player.nickname}</h1>
      </div>
      {season !== null && (
        <p className="rank">
          <strong className="rank__tier">{season.tierLabel}</strong>
          {season.placed && <span className="rank__rp">{formatRankPoint(season.rankPoint)}</span>}
          <span className="rank__scope">
            이번 시즌 {season.modeLabel} {season.rounds}판
          </span>
        </p>
      )}
      <div className="refresh">{bar}</div>
      {refreshMessage !== null && (
        <p className="refresh__message" role="status">
          {refreshMessage}
        </p>
      )}

      {player.cardStatus === 'pending' && (
        <p className="idcard__notice">
          플레이 카드를 분석하는 중이에요. 전적은 먼저 볼 수 있고, 끝나면 이 화면이 알아서 바뀌어요.
        </p>
      )}
      {player.cardStatus === 'none' && (
        <p className="idcard__notice">최근 30경기 안에 경쟁전·일반 경기 기록이 없어서 플레이 카드를 만들지 못했어요.</p>
      )}
      <CopyLinkButton />
    </section>
  );
}

/** 갱신 가능까지 남은 초를 1초마다 줄여서 보여준다. 서버가 새 값을 주면(갱신 후) 그 값으로 다시 시작 */
function useCountdown(secondsFromServer: number, receivedKey: string): number {
  const [left, setLeft] = useState(secondsFromServer);
  useEffect(() => {
    setLeft(secondsFromServer);
    if (secondsFromServer <= 0) return;
    const timer = setInterval(() => setLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(timer);
  }, [secondsFromServer, receivedKey]);
  return left;
}

function CopyLinkButton() {
  const [label, setLabel] = useState('공유 링크 복사');
  const copy = async () => {
    if (!navigator.clipboard) {
      setLabel('주소창의 링크를 복사해 주세요');
      return;
    }
    try {
      await navigator.clipboard.writeText(window.location.href);
      setLabel('링크를 복사했어요');
      setTimeout(() => setLabel('공유 링크 복사'), 1600);
    } catch {
      setLabel('복사하지 못했어요. 주소창의 링크를 복사해 주세요');
    }
  };
  return (
    <button className="idcard__share" type="button" onClick={copy}>
      {label}
    </button>
  );
}
