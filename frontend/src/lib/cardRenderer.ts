import type { ShareCard } from '../api/types';

/**
 * 기존 플레이어 카드 그리기(static/js/card-render.js, 캔버스 720x960)를 그대로 쓴다.
 * 공유 카드 꾸미기 화면(/card)도 같은 파일을 쓰기 때문에, 여기서 다시 만들면 두 화면의 그림이 어긋난다.
 * Vite 가 묶지 않고 브라우저가 Spring 이 내보내는 파일을 직접 불러온다.
 */
export interface CardAssets {
  readonly opaque: unique symbol;
}

export interface CardRenderer {
  loadAssets(card: ShareCard): Promise<CardAssets>;
  randomBackground(assets: CardAssets): Promise<unknown>;
  backgroundImage(assets: CardAssets, background: unknown): Promise<unknown>;
  /** 카드 테두리 빛 색 (CSS 색 문자열) */
  tierGlow(assets: CardAssets, card: ShareCard): string;
  /** 캔버스에 카드를 계속 그린다 (귀퉁이 반짝임). 멈추는 함수를 돌려준다 */
  play(
    canvas: HTMLCanvasElement,
    assets: CardAssets,
    card: ShareCard,
    getLook: () => { area: unknown; tags: string[] },
  ): () => void;
}

const RENDERER_URL = '/js/card-render.js';

export function loadCardRenderer(): Promise<CardRenderer> {
  return import(/* @vite-ignore */ RENDERER_URL) as Promise<CardRenderer>;
}

/** A카드: 성향 태그 없이 기록만 그린다. 배경은 열 때마다 랜덤. 멈추는 함수를 돌려준다 */
export async function drawProfileCard(canvas: HTMLCanvasElement, card: ShareCard): Promise<() => void> {
  const renderer = await loadCardRenderer();
  const assets = await renderer.loadAssets(card);
  const background = await renderer.randomBackground(assets);
  const area = await renderer.backgroundImage(assets, background);
  canvas.style.setProperty('--tier-glow', renderer.tierGlow(assets, card));
  return renderer.play(canvas, assets, card, () => ({ area, tags: [] }));
}
