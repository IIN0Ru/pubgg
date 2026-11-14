import type { ShareCard } from '../api/types';
import { backgroundImage, loadAssets, play, randomBackground, tierGlow } from './card/render';

/**
 * A카드: 성향 태그 없이 기록만 그린다. 배경은 열 때마다 랜덤. 멈추는 함수를 돌려준다.
 * 그리기는 공유 카드 꾸미기(/card)와 같은 lib/card/render.ts 를 써서 두 화면의 그림이 어긋나지 않는다.
 */
export async function drawProfileCard(canvas: HTMLCanvasElement, card: ShareCard): Promise<() => void> {
  const assets = await loadAssets(card);
  const background = await randomBackground(assets);
  const area = await backgroundImage(assets, background);
  canvas.style.setProperty('--tier-glow', tierGlow(assets, card));
  return play(canvas, assets, card, () => ({ area, tags: [] }));
}
