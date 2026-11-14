import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ApiError } from '../../api/client';
import { fetchReplay } from '../../api/client';
import type { Shard } from '../../api/types';
import { ReplayEngine, type Mode, type Who } from '../../lib/replay/engine';
import { clock, parseTimeParam, prepareModel, rangeLabel } from '../../lib/replay/model';
import { useMediaQuery } from '../../lib/useMediaQuery';
import { ReplaySide, type FeedFilter, type Panel } from './ReplaySide';
import { Tabs } from './Tabs';
import { Timeline } from './Timeline';
import { useEngine } from './useEngine';

const MODES: ReadonlyArray<{ value: Mode; label: string }> = [
  { value: 'play', label: '재생' },
  { value: 'path', label: '동선 전체' },
];
const RANGES: ReadonlyArray<{ value: number; label: string }> = [
  { value: 300, label: '300m' },
  { value: 500, label: '500m' },
  { value: 1000, label: '1km' },
  { value: Infinity, label: '전체' },
];
const SPEEDS: ReadonlyArray<{ value: number; label: string }> = [
  { value: 1, label: '1x' },
  { value: 4, label: '4x' },
  { value: 16, label: '16x' },
];
const WHO: ReadonlyArray<{ value: Who; label: string }> = [
  { value: 'team', label: '우리 팀' },
  { value: 'me', label: '나만' },
];

/** 폰·태블릿(위아래로 쌓이는 화면): 지도 버튼을 지도 안으로, 범례 접기, 아래 칸 탭 */
const NARROW = '(max-width: 960px)';
const HINT_MS = 1400;
const HINT_TIMES = 3; // 한 번 볼 때 세 번까지만

interface Props {
  shard: Shard;
  matchId: string;
  /** 검색한 플레이어 계정 ID */
  player: string;
  /** 주소의 ?t= (장면 링크) */
  startParam: string | null;
}

/**
 * 2D 리플레이 본문: 지도(캔버스) + 조작 + 범례 | 오른쪽 칸.
 * 경기 데이터는 /api/replay 로 따로 받는다 (처음 여는 경기는 텔레메트리를 읽느라 몇 초 걸려서 틀부터 띄운다).
 * 그리기와 재생 시계는 lib/replay/engine.ts, 이 컴포넌트는 버튼·칸만 맡는다.
 */
export function ReplayScreen({ shard, matchId, player, startParam }: Props) {
  const replay = useQuery({
    queryKey: ['replay', shard, matchId, player],
    queryFn: () => fetchReplay(shard, matchId, player),
    staleTime: Infinity,
    retry: (count, error: ApiError) => error.code === 'NETWORK' && count < 2,
  });
  const model = useMemo(() => (replay.data ? prepareModel(replay.data, player) : null), [replay.data, player]);

  const rootRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLElement>(null);
  const wrapRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [engine, setEngine] = useState<ReplayEngine | null>(null);
  const [mapError, setMapError] = useState<string | null>(null);

  const narrow = useMediaQuery(NARROW);
  const narrowRef = useRef(narrow);
  narrowRef.current = narrow;

  const [speed, setSpeed] = useState(4);
  const [range, setRange] = useState(500);
  const [phase, setPhase] = useState(0);
  const [who, setWho] = useState<Who>('team');
  const [feedFilter, setFeedFilter] = useState<FeedFilter>('all');
  const [panel, setPanel] = useState<Panel>('flow');
  const [legendOpen, setLegendOpen] = useState(!narrow); // 폰은 범례를 접어서 시작
  const [hint, setHint] = useState(false);
  const hintCount = useRef(0);
  const hintTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const [shareText, setShareText] = useState('장면 링크');

  const mode = useEngine(engine, (s) => s.mode);
  const follow = useEngine(engine, (s) => s.follow);

  // 데이터가 오면 캔버스를 켠다 (지도 첫 조각을 받으면 그리기 시작)
  useEffect(() => {
    const canvas = canvasRef.current;
    const wrap = wrapRef.current;
    if (!model || !canvas || !wrap) return undefined;
    const showHint = (show: boolean) => {
      clearTimeout(hintTimer.current);
      if (!show) {
        setHint(false);
        return;
      }
      if (hintCount.current >= HINT_TIMES) return;
      hintCount.current++;
      setHint(true);
      hintTimer.current = setTimeout(() => setHint(false), HINT_MS);
    };
    let created: ReplayEngine;
    try {
      created = new ReplayEngine(canvas, wrap, model, {
        startAt: parseTimeParam(startParam, model.R.duration),
        isNarrow: () => narrowRef.current,
        onTouchHint: showHint,
      });
    } catch {
      setMapError('이 브라우저에서는 지도를 그릴 수 없어요.');
      return undefined;
    }
    let alive = true;
    created
      .start()
      .then(() => alive && setEngine(created))
      .catch((e: unknown) => alive && setMapError(e instanceof Error ? e.message : '지도 이미지를 불러오지 못했어요.'));
    return () => {
      alive = false;
      clearTimeout(hintTimer.current);
      created.destroy();
      setEngine(null);
    };
    // 장면 링크(?t=)는 처음 열 때만 본다 (startParam 이 바뀌어도 다시 만들지 않음)
  }, [model]);

  // 화면에서 고른 설정을 캔버스에 넘긴다
  useEffect(() => engine?.setSpeed(speed), [engine, speed]);
  useEffect(() => engine?.setNearMeters(range), [engine, range]);
  useEffect(() => engine?.setPhase(phase), [engine, phase]);
  useEffect(() => engine?.setWho(who), [engine, who]);

  // 스페이스: 재생/정지 (입력창·버튼·타임라인에 초점이 있을 때는 그쪽 동작)
  useEffect(() => {
    if (!engine) return undefined;
    const onKey = (e: KeyboardEvent) => {
      const target = e.target instanceof Element ? e.target : null;
      if (target?.closest('input, textarea')) return;
      if (e.code === 'Space' && !target?.closest('button, [role="slider"], summary, a')) {
        e.preventDefault();
        engine.togglePlay();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [engine]);

  // PC 로 넓어지면 범례를 다시 펼친다
  useEffect(() => {
    if (!narrow) setLegendOpen(true);
  }, [narrow]);

  // 지도 칸 높이를 CSS 에 알려줌 (폰에서 탭을 그 바로 아래에 붙이려고)
  useEffect(() => {
    const stage = stageRef.current;
    const root = rootRef.current?.closest<HTMLElement>('.replay');
    if (!stage || !root) return undefined;
    const measure = () => root.style.setProperty('--stage-h', `${stage.offsetHeight}px`);
    measure();
    if (typeof ResizeObserver === 'undefined') return undefined;
    const ro = new ResizeObserver(measure);
    ro.observe(stage);
    return () => ro.disconnect();
  }, []);

  const shareScene = () => {
    if (!engine) return;
    const url = new URL(window.location.href);
    url.searchParams.set('t', clock(engine.getState().t));
    const link = url.toString().replace(/([?&]t=\d+)%3A(\d{2})/, '$1:$2'); // ?t=07%3A02 → ?t=07:02 (읽기 쉽게)
    const say = (text: string) => {
      setShareText(text);
      setTimeout(() => setShareText('장면 링크'), 1800);
    };
    // 복사가 안 되는 환경(권한 거부 등)이면 주소창에 넣어 두고 그렇게 알려준다
    const fallback = () => {
      window.history.replaceState(window.history.state, '', link);
      say('주소창에 넣었어요');
    };
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(link).then(() => say('복사됨'), fallback);
    } else {
      fallback();
    }
  };

  const status = replay.isError
    ? replay.error.message || '리플레이를 불러오지 못했어요.'
    : mapError ?? (engine ? null : '리플레이를 만드는 중이에요. 처음 여는 경기는 몇 초 걸려요.');
  const failed = replay.isError || mapError !== null;

  // 지도 안 작은 버튼에는 짧은 기호, 넓은 화면에서는 원래 글자
  const viewButtons = (
    <div className="replay__view">
      <button
        type="button"
        className={follow ? 'replay__icon is-on' : 'replay__icon'}
        aria-pressed={follow}
        title="우리 팀 따라가기"
        aria-label="우리 팀 따라가기"
        onClick={() => engine?.toggleFollow()}
      >
        {narrow ? '◎' : '따라가기'}
      </button>
      <button type="button" className="replay__icon" aria-label="축소" onClick={() => engine?.zoom('out')}>
        −
      </button>
      <button type="button" className="replay__icon" aria-label="확대" onClick={() => engine?.zoom('in')}>
        +
      </button>
      <button
        type="button"
        className="replay__icon"
        title="지도 전체 보기"
        aria-label="지도 전체 보기"
        onClick={() => engine?.zoom('fit')}
      >
        {narrow ? '⤢' : '전체'}
      </button>
    </div>
  );

  const phases = [{ value: 0, label: '전체' }, ...(model?.R.phases ?? []).map((p) => ({ value: p.n, label: String(p.n) }))];

  return (
    <div className="replay__body" ref={rootRef}>
      {/* 지도 + 조작 */}
      <section className="replay__stage" ref={stageRef}>
        <div className="replay__bar">
          <Tabs label="보기 방식" value={mode} options={MODES} onChange={(m) => engine?.setMode(m)} />
          <Tabs
            label="적 표시 범위"
            title="우리 팀 누구와든 이 거리 안의 적을 보여줘요. 우리와 싸울 적은 30초 전부터 거리와 상관없이 보여요"
            value={range}
            options={RANGES}
            onChange={setRange}
          />
          {!narrow && viewButtons}
          <button
            type="button"
            className="replay__icon replay__share"
            title="지금 장면으로 바로 열리는 주소를 복사해요"
            onClick={shareScene}
          >
            {shareText}
          </button>
        </div>

        <div className="replay__canvas" ref={wrapRef}>
          <canvas
            ref={canvasRef}
            role="img"
            aria-label="2D 리플레이 지도. 아래 경기 흐름 목록에서 같은 내용을 글로 볼 수 있어요."
          />
          {/* 폰: 한 손가락으로 지도를 끌면 페이지가 내려가고, 지도는 두 손가락으로 움직인다는 안내 */}
          <p className={hint ? 'replay__touch-hint is-on' : 'replay__touch-hint'} aria-hidden="true">
            두 손가락으로 지도를 움직여요
          </p>
          {status !== null && (
            <p className={failed ? 'replay__status replay__status--error' : 'replay__status'} role="status">
              {status}
            </p>
          )}
          {narrow && viewButtons}
        </div>

        {/* 재생 모드 */}
        <div className="replay__controls" hidden={mode !== 'play'}>
          <Timeline engine={engine} model={model} />
          <Tabs className="replay__speed" label="재생 속도" value={speed} options={SPEEDS} onChange={setSpeed} />
        </div>

        {/* 동선 전체 모드 */}
        <div className="replay__controls" hidden={mode !== 'path'}>
          <span className="replay__label">페이즈</span>
          <Tabs label="페이즈" value={phase} options={phases} onChange={setPhase} />
          <span className="replay__grow" />
          <span className="replay__label">동선</span>
          <Tabs label="누구 동선" value={who} options={WHO} onChange={setWho} />
        </div>

        {/* 범례: PC 는 늘 펼침, 폰은 접어 둠 */}
        <details
          className="replay__legend-box"
          open={legendOpen}
          onToggle={(e) => setLegendOpen((e.currentTarget as HTMLDetailsElement).open)}
        >
          <summary>범례</summary>
          <p className="replay__legend">
            <span>
              <i className="replay__dot replay__dot--me" />나
            </span>
            <span>
              <i className="replay__dot" />
              팀원
            </span>
            <span>
              <i className="replay__dot replay__dot--enemy" />적 ({rangeLabel(range)}, 팀마다 색 · 범위 근처는 흐리게)
            </span>
            <span>
              <i className="replay__line" />
              우리가 맞힘
            </span>
            <span>
              <i className="replay__line replay__line--taken" />
              우리가 맞음
            </span>
            <span>✕ 킬</span>
            <span>
              <i className="replay__icon-car" aria-hidden="true" />
              차량
            </span>
            <span>
              <i className="replay__icon-crate" aria-hidden="true" />
              보급
            </span>
            <span>
              <i className="replay__icon-boom" aria-hidden="true" />
              우리 팀 폭발물 (투척·박격포·판처, 터진 곳은 맞은 적 위치로 추정)
            </span>
            <span>
              <i className="replay__icon-heal" aria-hidden="true" />
              우리 팀 회복
            </span>
            <span>
              <i className="replay__icon-vision" aria-hidden="true" />
              추정 시야 (쏜 방향은 진하게, 아니면 움직이는 방향)
            </span>
            <span>빗금 자기장 밖</span>
            <span>흰 원 다음 안전구역</span>
            <span>점선 비행기</span>
          </p>
        </details>
      </section>

      {/* 오른쪽: 우리 팀 + 경기 흐름 / 교전 */}
      <ReplaySide
        engine={engine}
        model={model}
        mode={mode}
        phase={phase}
        panel={panel}
        onPanel={setPanel}
        feedFilter={feedFilter}
        onFeedFilter={setFeedFilter}
        stageRef={stageRef}
      />
    </div>
  );
}
