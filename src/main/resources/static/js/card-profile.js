/*
 * 전적 검색 화면 왼쪽 위 플레이어 카드. 사이트에 저장한 모습(옷·태그·배경) + 최신 기록으로 움직이는 카드를 그린다.
 * 저장한 적 없으면 기본 옷(흰 반팔 + 반바지)에 랜덤 배경.
 * 그리기는 card-render.js (카드 꾸미기 화면과 같은 그림).
 */
import {
  loadAssets, tierGlow, makeSheet, backgroundImage, randomBackground, normalizeChoices, styleTags, play,
} from './card-render.js';

const canvas = document.querySelector('[data-card-profile]');
if (canvas) {
  (async () => {
    const card = JSON.parse(canvas.dataset.card);
    const saved = canvas.dataset.saved ? JSON.parse(canvas.dataset.saved) : null;
    const A = await loadAssets(card);
    const c = normalizeChoices((k) => (saved ? saved.choices[k] : null));
    const bg = saved && saved.background ? saved.background : await randomBackground(A);
    const [sheet, area] = await Promise.all([makeSheet(A, c), backgroundImage(A, bg)]);
    canvas.style.setProperty('--tier-glow', tierGlow(A, card));
    const look = { sheet, area, tags: styleTags(card, c) };
    play(canvas, A, card, () => look);
  })().catch((e) => console.error('플레이어 카드를 그리지 못함', e));
}
