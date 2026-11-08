import type { SavedPlayer } from '../store/players';
import { usePlayerLists } from '../store/players';

/** ☆/★ 즐겨찾기 버튼. 눌러서 추가·삭제하고, 상단 검색창 목록과 같은 저장소를 쓴다 */
export function FavoriteButton({ player }: { player: SavedPlayer }) {
  const on = usePlayerLists((s) => s.favorites.some((p) => p.platform === player.platform && p.nickname.toLowerCase() === player.nickname.toLowerCase()));
  const toggle = usePlayerLists((s) => s.toggleFavorite);
  return (
    <button
      className="fav"
      type="button"
      aria-pressed={on}
      aria-label={on ? '즐겨찾기에서 빼기' : '즐겨찾기에 추가'}
      onClick={() => toggle(player)}
    >
      {on ? '★' : '☆'}
    </button>
  );
}
