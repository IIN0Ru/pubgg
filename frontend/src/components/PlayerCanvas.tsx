import { useEffect, useRef } from 'react';
import type { ShareCard } from '../api/types';
import { drawProfileCard } from '../lib/cardRenderer';

/**
 * A카드 캔버스. 그리는 일은 lib/card/render.ts 가 하고, 여기서는 켜고 끄는 것만 맡는다.
 * 카드 값(닉네임·티어·KDA 등)이 바뀌면 다시 그리고, 화면에서 사라지면 반짝임 타이머를 멈춘다.
 */
export function PlayerCanvas({ card }: { card: ShareCard }) {
  const ref = useRef<HTMLCanvasElement>(null);
  // 값이 같은 새 객체(폴링으로 다시 받은 응답)로는 다시 그리지 않으려고 내용으로 비교한다
  const cardKey = JSON.stringify(card);

  useEffect(() => {
    const canvas = ref.current;
    if (canvas === null) return;
    let stop: (() => void) | undefined;
    let cancelled = false;
    drawProfileCard(canvas, JSON.parse(cardKey) as ShareCard)
      .then((stopFn) => {
        if (cancelled) stopFn();
        else stop = stopFn;
      })
      .catch((e: unknown) => console.error('플레이어 카드를 그리지 못함', e));
    return () => {
      cancelled = true;
      stop?.();
    };
  }, [cardKey]);

  return (
    <canvas
      ref={ref}
      width={720}
      height={960}
      role="img"
      aria-label={`${card.nickname} 플레이어 카드`}
    />
  );
}
