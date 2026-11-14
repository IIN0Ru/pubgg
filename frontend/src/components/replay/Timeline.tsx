import { useMemo, useRef } from 'react';
import type { ReplayEngine } from '../../lib/replay/engine';
import { clock, pct, phaseText, type Model } from '../../lib/replay/model';
import { useEngine } from './useEngine';

/** 재생 버튼 + 시간 + 타임라인(페이즈·교전 표시, 누르고 끌어서 이동, ←→ 10초) */
export function Timeline({ engine, model }: { engine: ReplayEngine | null; model: Model | null }) {
  const t = useEngine(engine, (s) => s.t);
  const playing = useEngine(engine, (s) => s.playing);
  const ref = useRef<HTMLDivElement>(null);
  const R = model?.R ?? null;
  const duration = R?.duration ?? 0;
  const at = R ? pct(R, t).toFixed(2) : '0';

  // 페이즈 시작과 우리 팀 기절·킬 표시
  const marks = useMemo(() => {
    if (!model) return null;
    const { R: data, byName } = model;
    return (
      <>
        {data.phases.map((p) => (
          <span key={`p${p.n}`} className="replay__phase" style={{ left: `${pct(data, p.t).toFixed(2)}%` }}>
            <i>{p.n}</i>
          </span>
        ))}
        {data.events
          .filter((e) => e.ours && (e.type === 'kill' || e.type === 'knock'))
          .map((e, i) => {
            const actor = e.actor ? byName.get(e.actor) : undefined;
            const mine = !!actor && actor.team === data.myTeam;
            return (
              <span
                key={`e${i}`}
                className={mine ? 'replay__mark replay__mark--ours' : 'replay__mark'}
                style={{ left: `${pct(data, e.t).toFixed(2)}%` }}
              />
            );
          })}
      </>
    );
  }, [model]);

  const seekFromPointer = (clientX: number) => {
    const el = ref.current;
    if (!engine || !el) return;
    const rect = el.getBoundingClientRect();
    const k = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    engine.seek(k * duration);
  };

  return (
    <>
      <button
        type="button"
        className={playing ? 'replay__play is-playing' : 'replay__play'}
        aria-label={playing ? '일시 정지' : '재생'}
        onClick={() => engine?.togglePlay()}
      >
        <svg className="replay__play-icon" viewBox="0 0 14 14" aria-hidden="true">
          <path d="M3 1.5l9 5.5-9 5.5z" />
        </svg>
        <svg className="replay__pause-icon" viewBox="0 0 14 14" aria-hidden="true">
          <path d="M3 1.5h3v11H3zM8 1.5h3v11H8z" />
        </svg>
      </button>
      <span className="replay__time">
        {clock(t)} / {clock(duration)}
      </span>
      <div
        ref={ref}
        className="replay__timeline"
        role="slider"
        tabIndex={0}
        aria-label="경기 시간"
        aria-valuemin={0}
        aria-valuenow={Math.floor(t)}
        aria-valuemax={duration}
        aria-valuetext={R ? `${clock(t)}, ${phaseText(R, t)}` : undefined}
        onPointerDown={(e) => {
          if (!engine) return;
          e.currentTarget.setPointerCapture(e.pointerId);
          seekFromPointer(e.clientX);
        }}
        onPointerMove={(e) => {
          if (e.currentTarget.hasPointerCapture(e.pointerId)) seekFromPointer(e.clientX);
        }}
        onKeyDown={(e) => {
          if (!engine) return;
          if (e.key === 'ArrowRight') {
            engine.seek(t + 10);
            e.preventDefault();
          }
          if (e.key === 'ArrowLeft') {
            engine.seek(t - 10);
            e.preventDefault();
          }
        }}
      >
        <span className="replay__track" />
        <span className="replay__done" style={{ width: `${at}%` }} />
        <span>{marks}</span>
        <span className="replay__knob" style={{ left: `${at}%` }} />
      </div>
    </>
  );
}
