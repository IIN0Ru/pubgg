import { useSyncExternalStore } from 'react';
import type { EngineState, ReplayEngine } from '../../lib/replay/engine';

const IDLE: EngineState = { t: 0, playing: false, follow: true, mode: 'play' };
const noop = () => () => undefined;

/**
 * 리플레이 캔버스의 값 하나를 읽는다. 고른 값이 바뀔 때만 다시 그린다.
 * 예: 타임라인은 매 프레임 t 를, 오른쪽 칸은 초가 바뀔 때만(Math.floor(t)) 다시 그린다.
 */
export function useEngine<T>(engine: ReplayEngine | null, select: (s: EngineState) => T): T {
  return useSyncExternalStore(engine ? engine.subscribe : noop, () => select(engine ? engine.getState() : IDLE));
}
