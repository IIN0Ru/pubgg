import { useEffect, useMemo, useRef } from 'react';
import type { Mode, ReplayEngine } from '../../lib/replay/engine';
import {
  clock,
  deathAt,
  feedItems,
  fightParts,
  fightResult,
  fightSummary,
  fightTotals,
  hurtNotes,
  phaseRange,
  phaseText,
  teamStatus,
  TYPE_LABEL,
  type Model,
} from '../../lib/replay/model';
import { Tabs } from './Tabs';
import { useEngine } from './useEngine';

export type Panel = 'flow' | 'team' | 'stats' | 'death';
export type FeedFilter = 'all' | 'fight' | 'move';

const PANELS: ReadonlyArray<{ value: Panel; label: string }> = [
  { value: 'flow', label: '경기 흐름' },
  { value: 'team', label: '우리 팀' },
  { value: 'stats', label: '교전' },
  { value: 'death', label: '죽기 전 30초' },
];

const FEED_FILTERS: ReadonlyArray<{ value: FeedFilter; label: string }> = [
  { value: 'all', label: '전체' },
  { value: 'fight', label: '교전' },
  { value: 'move', label: '이동' },
];

interface Props {
  engine: ReplayEngine | null;
  model: Model | null;
  mode: Mode;
  phase: number;
  panel: Panel;
  onPanel: (panel: Panel) => void;
  feedFilter: FeedFilter;
  onFeedFilter: (filter: FeedFilter) => void;
  /** 폰에서 지도가 위에 붙어 있을 때, 칸을 바꾸면 그 칸의 맨 위가 지도 아래에 보이게 */
  stageRef: React.RefObject<HTMLElement | null>;
}

/** 오른쪽(폰은 아래) 칸: 경기 흐름·교전 목록 / 우리 팀 / 교전 지표 / 죽기 전 30초 */
export function ReplaySide({ engine, model, mode, phase, panel, onPanel, feedFilter, onFeedFilter, stageRef }: Props) {
  // 초가 바뀔 때만 다시 그린다. 동선 전체 보기는 고른 페이즈의 끝 시각 기준
  const second = useEngine(engine, (s) => Math.floor(s.t));
  const playing = useEngine(engine, (s) => s.playing);
  const t = model && mode === 'path' ? phaseRange(model.R, phase)[1] : second;
  const sideRef = useRef<HTMLElement>(null);

  const team = model ? teamStatus(model, t) : null;
  const death = model ? deathAt(model, t) : null;
  const fights = model?.R.fights ?? [];

  const selectPanel = (p: Panel) => {
    onPanel(p);
    // 내려가 있었으면 바꾼 칸의 맨 위가 보이게
    const side = sideRef.current;
    const stage = stageRef.current;
    if (!side) return;
    const top = side.getBoundingClientRect().top;
    const stageH = stage && getComputedStyle(stage).position === 'sticky' ? stage.offsetHeight : 0;
    if (top < stageH) window.scrollBy(0, top - stageH);
  };

  const jump = (to: number) => engine?.jumpTo(Math.max(0, to));

  return (
    <aside className="replay__side" data-panel={panel} ref={sideRef}>
      {/* 폰: 아래 칸들을 탭으로 하나씩 (PC 에서는 숨김, 전부 보임) */}
      <Tabs className="replay__panels" label="보여줄 칸" value={panel} options={PANELS} onChange={selectPanel} />

      {/* 죽기 전 30초: 지금 시각까지 우리 팀이 마지막으로 기절·사망한 순간, 그 전에 받은 피해 */}
      <section className="sidebox replay__death" data-death data-panel-id="death" hidden={!death}>
        <h2 className="sidebox__title">죽기 전 30초</h2>
        {death && (
          <>
            <div className="replay__death-head">
              <b>
                {death.event.target} {death.label} <span>{clock(death.event.t)}</span>
              </b>
              <button type="button" className="tab" onClick={() => jump(death.goTo)}>
                그 장면 보기
              </button>
            </div>
            <p className="replay__death-sum">{death.summary}</p>
            <ol className="replay__hurts">
              {death.hurts.map((h, i) => {
                const notes = hurtNotes(h);
                return (
                  <li key={i}>
                    <button type="button" className="replay__hurt" onClick={() => jump(h.t - 2)}>
                      <time>{clock(h.t)}</time>
                      <span>
                        {h.attacker ? (
                          <>
                            <b>{h.attacker}</b> · {h.weapon}
                            {h.distance >= 0 ? ` · ${h.distance}m` : ''}
                          </>
                        ) : (
                          <b>{h.weapon}</b>
                        )}
                        {notes.length > 0 && <small>{notes.join(' · ')}</small>}
                      </span>
                      <span className="replay__hurt-dmg">
                        <b>−{h.damage}</b>
                        <small>남은 {h.hp}</small>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ol>
          </>
        )}
      </section>
      <p className="sidebox replay__death-none" data-panel-id="death">
        아직 우리 팀이 당한 순간이 없어요. 재생하다 팀원이 기절하거나 죽으면 여기에 나와요.
      </p>

      <section className="sidebox" data-panel-id="team">
        <h2 className="sidebox__title">
          우리 팀 <span>{team ? `${team.alive}명 생존` : ''}</span>
        </h2>
        <ol className="replay__team">
          {team?.members.map((m) => (
            <li key={m.n} className={['replay__member', m.me && 'replay__member--me', m.dead && 'is-dead'].filter(Boolean).join(' ')}>
              <span className="replay__num">{m.n}</span>
              <span className="replay__name">{m.name}</span>
              <span className="replay__state">
                {m.status} · {m.kills}킬
              </span>
            </li>
          ))}
        </ol>
      </section>

      {/* 교전 지표: 이 판 합계 + 교전마다 한 줄 */}
      <FightStats fights={fights} onJump={(to) => jump(to)} />
      <p className="sidebox replay__death-none" data-panel-id="stats">
        우리 팀이 싸운 기록이 없어요.
      </p>

      <section className="sidebox replay__flow" data-panel-id="flow">
        <div className="replay__flow-head">
          <h2 className="sidebox__title">{mode === 'play' ? '경기 흐름' : `교전 ${fights.length}번`}</h2>
          <Tabs
            label="경기 흐름 거르기"
            value={feedFilter}
            options={FEED_FILTERS}
            onChange={onFeedFilter}
            hidden={mode !== 'play'}
          />
        </div>
        <p className="replay__hint">
          {mode === 'play' ? '누르면 그 순간으로 넘어가요.' : '누르면 그 교전 5초 전부터 재생해요.'}
        </p>
        {mode === 'play' ? (
          <Feed model={model} t={second} playing={playing} filter={feedFilter} onJump={(to) => jump(to)} />
        ) : (
          <FightList model={model} onJump={(to) => jump(to)} />
        )}
      </section>
    </aside>
  );
}

/** 경기 흐름: 지금 시각까지 마지막 사건에 표시하고, 재생 중이면 그 줄이 보이게 내린다 */
function Feed({
  model,
  t,
  playing,
  filter,
  onJump,
}: {
  model: Model | null;
  t: number;
  playing: boolean;
  filter: FeedFilter;
  onJump: (t: number) => void;
}) {
  const items = useMemo(() => (model ? feedItems(model) : []), [model]);
  const listRef = useRef<HTMLOListElement>(null);
  const itemRefs = useRef<Array<HTMLLIElement | null>>([]);

  let current = -1;
  items.forEach((it, i) => {
    if (it.t <= t && (filter === 'all' || it.group === filter)) current = i;
  });

  useEffect(() => {
    if (current < 0 || !playing) return;
    const el = itemRefs.current[current];
    const box = listRef.current;
    if (!el || !box) return;
    if (el.offsetTop < box.scrollTop || el.offsetTop > box.scrollTop + box.clientHeight - el.offsetHeight) {
      box.scrollTop = el.offsetTop - box.clientHeight / 3;
    }
    // 재생이 지나갈 때만 따라 내린다 (재생을 멈추면 목록을 마음대로 볼 수 있게)
  }, [current]);

  return (
    <ol className="replay__feed" ref={listRef}>
      {items.map((it, i) => (
        <li
          key={i}
          ref={(el) => {
            itemRefs.current[i] = el;
          }}
          data-group={it.group}
          hidden={filter !== 'all' && it.group !== filter}
          className={i === current ? 'is-current' : undefined}
        >
          <button type="button" className="replay__event" onClick={() => onJump(it.t - 5)}>
            <time>{clock(it.t)}</time>
            <span className={it.strong ? 'replay__badge replay__badge--strong' : 'replay__badge'}>
              {TYPE_LABEL[it.type] ?? ''}
            </span>
            <span>{it.text}</span>
          </button>
        </li>
      ))}
    </ol>
  );
}

/** 동선 전체 보기: 오른쪽에 교전 목록. 누르면 재생 보기로 바꿔서 그 교전 5초 전부터 */
function FightList({ model, onJump }: { model: Model | null; onJump: (t: number) => void }) {
  const fights = model?.R.fights ?? [];
  return (
    <ol className="replay__feed">
      {fights.length === 0 ? (
        <li className="replay__empty">우리 팀이 싸운 기록이 없어요.</li>
      ) : (
        fights.map((f) => (
          <li key={f.n}>
            <button type="button" className="replay__fight" onClick={() => onJump(f.start - 5)}>
              <span className="replay__fight-n">{f.n}</span>
              <span>
                <b>
                  {clock(f.start)} · {model ? phaseText(model.R, f.start) : ''}
                </b>
                <span className="replay__fight-sub">
                  {fightSummary(f)} · {Math.max(1, f.end - f.start)}초
                </span>
              </span>
            </button>
          </li>
        ))
      )}
    </ol>
  );
}

function FightStats({ fights, onJump }: { fights: Model['R']['fights']; onJump: (t: number) => void }) {
  const sum = fightTotals(fights);
  return (
    <section className="sidebox" data-stats data-panel-id="stats" hidden={fights.length === 0}>
      <h2 className="sidebox__title">
        교전 <span>이 판 우리 팀</span>
      </h2>
      <dl className="replay__stats">
        <div>
          <dt>
            기절→킬
            <br />
            {sum.converted}/{sum.knocks}
          </dt>
          <dd>{sum.knocks ? `${Math.round((sum.converted / sum.knocks) * 100)}%` : '−'}</dd>
        </div>
        <div>
          <dt>
            복수
            <br />
            당하고 10초 안
          </dt>
          <dd>
            {sum.traded}/{sum.losses}
          </dd>
        </div>
        <div>
          <dt>
            살림
            <br />
            기절당한 팀원
          </dt>
          <dd>
            {sum.revived}/{sum.knocked}
          </dd>
        </div>
      </dl>
      <ol className="replay__fightstats">
        {fights.map((f) => {
          const parts = fightParts(f);
          const result = fightResult(f);
          return (
            <li key={f.n}>
              <button type="button" className="replay__fightstat" onClick={() => onJump(f.start - 5)}>
                <span className="replay__fight-n">{f.n}</span>
                <span>
                  {clock(f.start)}
                  {f.enemyTeam >= 0 ? ` · ${f.enemyTeam}팀` : ''}
                  {parts.length > 0 && <small>{parts.join(' · ')}</small>}
                </span>
                <b className={result === '패' ? 'is-lose' : undefined}>{result}</b>
              </button>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
