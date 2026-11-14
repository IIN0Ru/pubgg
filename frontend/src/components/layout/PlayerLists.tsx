import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { searchPath } from '../../lib/search';
import { usePlayerLists, type SavedPlayer } from '../../store/players';

/** 지울 때 mousedown 에서 막아야 상단 검색창 드롭다운이 먼저 닫히지 않는다 */
const keepFocus = (e: React.MouseEvent) => e.preventDefault();

function Chip({ player, starred, onRemove }: { player: SavedPlayer; starred: boolean; onRemove: () => void }) {
  return (
    <li className={starred ? 'pchip pchip--fav' : 'pchip'}>
      <Link className="pchip__link" to={searchPath(player.platform, player.nickname)} title={player.nickname}>
        <span className="pchip__platform">{player.label || player.platform}</span>
        {/* 긴 닉네임은 말줄임 */}
        <span className="pchip__name">{player.nickname}</span>
      </Link>
      <button
        type="button"
        className="pchip__remove"
        aria-label={`${player.nickname} 지우기`}
        onMouseDown={keepFocus}
        onClick={onRemove}
      >
        ×
      </button>
    </li>
  );
}

const UNDO_MS = 5000;

/**
 * 즐겨찾기 + 최근 검색 목록 (첫 화면에는 늘, 상단 검색창에서는 펼쳐질 때).
 * 즐겨찾기는 일부러 모은 거라 실수로 지웠을 때 5초 동안 되돌릴 수 있다 (최근 검색은 바로 삭제).
 */
export function PlayerLists() {
  const favorites = usePlayerLists((s) => s.favorites);
  const recent = usePlayerLists((s) => s.recent);
  const removeFavorite = usePlayerLists((s) => s.removeFavorite);
  const removeRecent = usePlayerLists((s) => s.removeRecent);
  const restoreFavorite = usePlayerLists((s) => s.restoreFavorite);
  const [undo, setUndo] = useState<{ player: SavedPlayer; index: number } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => () => clearTimeout(timer.current), []);

  const removeFav = (player: SavedPlayer, index: number) => {
    removeFavorite(player);
    setUndo({ player, index });
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setUndo(null), UNDO_MS);
  };

  const undoBar = undo && (
    <p className="plist__undo" role="status">
      {undo.player.nickname} 님을 즐겨찾기에서 뺐어요.{' '}
      <button
        type="button"
        onMouseDown={keepFocus}
        onClick={() => {
          restoreFavorite(undo.player, undo.index);
          clearTimeout(timer.current);
          setUndo(null);
        }}
      >
        되돌리기
      </button>
    </p>
  );

  return (
    <>
      <section className="plist">
        <h2 className="plist__title">즐겨찾기</h2>
        {favorites.length === 0 ? (
          <p className="plist__empty">검색 결과에서 ☆을 누르면 여기에 모여요.</p>
        ) : (
          <ul className="plist__items">
            {favorites.map((p, i) => (
              <Chip key={`${p.platform}:${p.nickname}`} player={p} starred onRemove={() => removeFav(p, i)} />
            ))}
          </ul>
        )}
        {undoBar}
      </section>
      <section className="plist">
        <h2 className="plist__title">최근 검색</h2>
        {recent.length === 0 ? (
          <p className="plist__empty">아직 검색한 플레이어가 없어요.</p>
        ) : (
          <ul className="plist__items">
            {recent.map((p) => (
              <Chip key={`${p.platform}:${p.nickname}`} player={p} starred={false} onRemove={() => removeRecent(p)} />
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
