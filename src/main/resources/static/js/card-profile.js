/*
 * 전적 검색 화면 왼쪽 위 플레이어 카드 (A카드). 최신 기록으로 그린다.
 * 성향 태그는 없다 (태그는 공유 카드(B카드)를 만들 때만 고른다). 배경은 열 때마다 랜덤.
 * 그리기는 card-render.js (공유 카드 꾸미기 화면과 같은 그림).
 */
import {
  loadAssets, tierGlow, backgroundImage, randomBackground, play,
} from './card-render.js';

const canvas = document.querySelector('[data-card-profile]');
if (canvas) {
  (async () => {
    const card = JSON.parse(canvas.dataset.card);
    const A = await loadAssets(card);
    const bg = await randomBackground(A);
    const area = await backgroundImage(A, bg);
    canvas.style.setProperty('--tier-glow', tierGlow(A, card));
    const look = { area, tags: [] };
    play(canvas, A, card, () => look);
  })().catch((e) => console.error('플레이어 카드를 그리지 못함', e));
}
