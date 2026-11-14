/*
 * 2D 리플레이 캔버스 (지도·자기장·사람·이펙트 그리기 + 재생 시계 + 확대·이동).
 * 원래 static/js/replay.js 의 그리기 부분을 TypeScript 클래스로 옮긴 것. 그리는 방식·색·수치는 그대로다.
 *
 * React 화면(ReplayScreen)과의 약속
 *  - 화면은 버튼을 누르면 이 클래스의 메서드(seek, togglePlay, setMode …)를 부른다
 *  - 이 클래스는 재생 시각·재생 중·따라가기가 바뀌면 subscribe 한 화면에 알린다 (getState 로 읽음)
 *  - 화면 요소(DOM)는 캔버스와 그 바깥 칸(크기 재기용)만 만진다
 *
 * 좌표: 서버가 m 단위로 준다. 지도 이미지 한 변 = map.size(m).
 * 색: 지도 이미지는 다크/라이트와 상관없이 늘 어두워서, 지도 위에 그리는 색은 고정(MAP_COLORS)하고
 *     지도 바깥 바탕색과 노랑만 main.css 변수에서 읽는다. 다크/라이트를 바꾸면 다시 읽는다.
 */
import {
  involvedEnemies,
  nearness,
  phaseRange,
  posAt,
  visionAt,
  zoneAt,
  firstMoment,
  type Model,
  type Pos,
  type Vision,
  type Zone,
} from './model';
import type { Blast, Heal, Track } from './types';

// ---------- 설정 ----------
const TRAIL_SECONDS = 90; // 재생 중 보여주는 지나온 길 길이
const KILL_MARK_SECONDS = 12; // ✕ 표시가 남아 있는 시간
const HIT_SECONDS = 1.5; // 명중선이 남아 있는 시간
const FOLLOW_WIDTH = 1400; // 따라가기 때 화면 가로에 들어오는 거리 (m)
const TILE_PX = 512;

// 지도 위 색: 어두운 지도 위에서 잘 보이게 고정 (다크 모드 값과 같음)
const MAP_COLORS = {
  ink: '#FFFFFF',
  muted: '#9A9A9A',
  card: '#1A1A1A',
  dark: '#0A0A0A',
  heal: '#22C55E', // 회복 이펙트 (DESIGN.md accent-emerald / success)
};

// 적 팀 색: 근처에 여러 팀이 섞여 있을 때 구분하려고 지도 위에서만 쓰는 색 (디자인 규칙의 예외. CLAUDE.md 참고)
// 노랑(나)·흰색(팀원)·회색과 헷갈리지 않는 5가지만. 주황·노랑 계열은 나(노랑)와 헷갈려서 뺐다.
// 300m 안에 6팀 이상 붙는 일은 드물어서 넘치면 처음 색부터 다시 쓴다. 범례 아이콘도 이 색을 따른다
export const TEAM_PALETTE = ['#FF7A59', '#38BDF8', '#C084FC', '#4ADE80', '#F472B6'];

/*
 * 이펙트는 재생이 그 순간을 지나갈 때 시작해서, 배속과 상관없이 실제 시간으로 FX_MS 동안 보인다.
 * (경기 시간으로 재면 16배속에서 0.1초만 보여서 안 보임)
 * 위치는 지도 좌표로 들고 있어서 화면을 옮기거나 확대해도 제자리에 남는다.
 *  - 폭발물(blasts): 던진 곳 → 터진 곳 궤적 뒤에 폭발. 터진 곳은 맞은 적들 위치로 추정한 값
 *      수류탄·C4·접착 폭탄·박격포·판처 = 섬광 + 노란 충격파 + 파편 / 화염병 = 불꽃
 *      박격포는 높게 휘는 궤적, 판처는 곧은 로켓 연기. 잡은 한 발은 크게, 맞히기만 한 건 작게
 *  - 회복(heals): 쓴 사람 점 위로 초록 플러스가 올라가며 사라짐
 */
const FX_MS = 1500; // 터진 뒤 이펙트 길이
const FLIGHT_MS = 380; // 궤적(날아가는 동안) 길이. 박격포는 더 길게
const HEAL_MS = 1300;

type FxSource = { t: number; fx: 'blast'; b: Blast } | { t: number; fx: 'heal'; h: Heal };
interface Fx {
  src: FxSource;
  /** 시작한 performance.now() */
  at: number;
}

export type Mode = 'play' | 'path';
export type Who = 'team' | 'me';

/** 화면(React)이 읽는 값. 바뀔 때마다 새 객체 */
export interface EngineState {
  t: number;
  playing: boolean;
  follow: boolean;
  mode: Mode;
}

interface Colors {
  primary: string;
  canvas: string;
}

export interface EngineOptions {
  /** 처음 보여줄 시각 (주소의 ?t=). 없으면 우리 팀 착지 직전 */
  startAt: number | null;
  /** 폰·태블릿 배치인지 (지도 높이를 다르게) */
  isNarrow: () => boolean;
  /** 폰에서 한 손가락으로 지도를 끌 때 "두 손가락으로" 안내를 띄우라고 알림 */
  onTouchHint: (show: boolean) => void;
}

export class ReplayEngine {
  private readonly ctx: CanvasRenderingContext2D;
  private readonly R;
  private state: EngineState;
  private readonly listeners = new Set<() => void>();

  // 설정 (화면에서 고름)
  private speed = 4;
  private phase = 0;
  private who: Who = 'team';
  private nearMeters = 500;

  private view = { cx: 0, cy: 0, scale: 1 }; // 화면 가운데의 지도 좌표(m), 1m 당 픽셀
  private size = { w: 0, h: 0, dpr: 1 };
  private colors: Colors = { primary: '#FFD02F', canvas: '#000000' };
  private hatch: CanvasPattern | null = null;
  private readonly tiles = new Map<string, HTMLImageElement>();
  private readonly teamColors = new Map<number, string>();
  private nextTeamColor = 0;

  private fxSources: FxSource[] | null = null;
  private fx: Fx[] = [];

  private started = false;
  private destroyed = false;
  private frame = 0;
  private lastFrame = 0;
  private drag: { x: number; y: number; cx: number; cy: number } | null = null;
  private pinch: { m: { x: number; y: number; d: number }; scale: number; wx: number; wy: number } | null = null;
  private readonly cleanups: Array<() => void> = [];

  constructor(
    private readonly canvas: HTMLCanvasElement,
    private readonly wrap: HTMLElement,
    private readonly model: Model,
    private readonly options: EngineOptions,
  ) {
    const ctx = canvas.getContext('2d');
    if (ctx === null) throw new Error('캔버스를 쓸 수 없어요');
    this.ctx = ctx;
    this.R = model.R;
    this.state = { t: options.startAt ?? firstMoment(model), playing: false, follow: true, mode: 'play' };
    this.readColors();
  }

  // ---------- 화면과 주고받기 ----------

  subscribe = (fn: () => void): (() => void) => {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  };

  getState = (): EngineState => this.state;

  private set(patch: Partial<EngineState>) {
    const next = { ...this.state, ...patch };
    if (
      next.t === this.state.t &&
      next.playing === this.state.playing &&
      next.follow === this.state.follow &&
      next.mode === this.state.mode
    ) {
      return;
    }
    this.state = next;
    this.listeners.forEach((fn) => fn());
  }

  /**
   * 지도 전체 한 조각(단계 0)을 받으면 그리기 시작. 더 자세한 조각은 확대한 만큼 그때그때 불러온다.
   * 조각을 못 받으면 실패한다 (화면이 안내를 띄움).
   */
  start(): Promise<void> {
    return new Promise((resolve, reject) => {
      const base = this.tile(0, 0, 0);
      const begin = () => {
        if (this.destroyed) return;
        this.started = true;
        this.listen();
        this.resize();
        this.fitAll();
        this.draw();
        this.frame = requestAnimationFrame(this.loop);
        resolve();
      };
      if (base.complete && base.naturalWidth > 0) begin();
      else {
        base.onload = begin;
        base.onerror = () => reject(new Error('지도 이미지를 불러오지 못했어요.'));
      }
    });
  }

  destroy() {
    this.destroyed = true;
    cancelAnimationFrame(this.frame);
    this.cleanups.forEach((fn) => fn());
    this.listeners.clear();
  }

  // ---------- 조작 ----------

  seek(t: number) {
    this.fx = [];
    this.set({ t: Math.max(0, Math.min(this.R.duration, t)) });
    this.draw();
  }

  /** 그 순간으로 넘어가서 우리 팀을 따라간다 (경기 흐름·교전 목록을 눌렀을 때) */
  jumpTo(t: number) {
    if (this.state.mode !== 'play') this.setMode('play');
    this.seek(t);
    this.set({ follow: true });
  }

  togglePlay() {
    if (!this.state.playing && this.state.t >= this.R.duration) this.set({ t: firstMoment(this.model) });
    this.set({ playing: !this.state.playing });
  }

  setSpeed(speed: number) {
    this.speed = speed;
  }

  setNearMeters(meters: number) {
    this.nearMeters = meters;
    this.draw();
  }

  setPhase(phase: number) {
    this.phase = phase;
    this.draw();
  }

  setWho(who: Who) {
    this.who = who;
    this.draw();
  }

  setMode(mode: Mode) {
    if (mode === this.state.mode) return;
    if (mode === 'path') {
      this.set({ mode, playing: false });
      this.fitTeam(); // 우리 팀 동선이 다 들어오게 맞춘다
    } else {
      this.set({ mode });
    }
    this.draw();
  }

  toggleFollow() {
    const follow = !this.state.follow;
    if (follow && this.state.mode !== 'play') this.setMode('play');
    this.set({ follow });
  }

  zoom(how: 'in' | 'out' | 'fit') {
    this.set({ follow: false });
    if (how === 'fit') this.fitAll();
    else {
      this.view.scale *= how === 'in' ? 1.5 : 1 / 1.5;
      this.clampView();
    }
    this.draw();
  }

  // ---------- 이벤트 ----------

  private on<K extends keyof HTMLElementEventMap>(
    el: HTMLElement,
    type: K,
    fn: (e: HTMLElementEventMap[K]) => void,
    opts?: AddEventListenerOptions,
  ) {
    el.addEventListener(type, fn, opts);
    this.cleanups.push(() => el.removeEventListener(type, fn, opts));
  }

  private listen() {
    const { canvas } = this;

    // 다크/라이트를 바꾸면 바탕색을 다시 읽는다
    const themeObserver = new MutationObserver(() => {
      this.readColors();
      this.draw();
    });
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    this.cleanups.push(() => themeObserver.disconnect());

    // 지도 영역 크기가 바뀌면 (창 크기, 폰 회전) 다시 맞춘다
    const onResize = () => this.resize();
    window.addEventListener('resize', onResize);
    this.cleanups.push(() => window.removeEventListener('resize', onResize));
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(onResize);
      ro.observe(this.wrap);
      this.cleanups.push(() => ro.disconnect());
    }

    // 휠: 마우스가 가리키던 곳이 그대로 있게 확대·축소
    this.on(
      canvas,
      'wheel',
      (e) => {
        e.preventDefault();
        this.set({ follow: false });
        const rect = canvas.getBoundingClientRect();
        const mx = e.clientX - rect.left;
        const my = e.clientY - rect.top;
        const wx = (mx - this.size.w / 2) / this.view.scale + this.view.cx;
        const wy = (my - this.size.h / 2) / this.view.scale + this.view.cy;
        this.view.scale *= e.deltaY < 0 ? 1.2 : 1 / 1.2;
        this.clampView();
        this.view.cx = wx - (mx - this.size.w / 2) / this.view.scale;
        this.view.cy = wy - (my - this.size.h / 2) / this.view.scale;
        this.draw();
      },
      { passive: false },
    );

    // 마우스·펜: 끌어서 이동. 손가락은 아래 터치 처리에서 (한 손가락은 페이지 내리기로 남겨 둠)
    this.on(canvas, 'pointerdown', (e) => {
      if (e.pointerType === 'touch') return;
      this.drag = { x: e.clientX, y: e.clientY, cx: this.view.cx, cy: this.view.cy };
      canvas.setPointerCapture(e.pointerId);
    });
    this.on(canvas, 'pointermove', (e) => {
      if (!this.drag || e.pointerType === 'touch') return;
      const dx = e.clientX - this.drag.x;
      const dy = e.clientY - this.drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 3 && this.state.follow) this.set({ follow: false });
      this.view.cx = this.drag.cx - dx / this.view.scale;
      this.view.cy = this.drag.cy - dy / this.view.scale;
      this.draw();
    });
    const endDrag = () => {
      this.drag = null;
    };
    this.on(canvas, 'pointerup', endDrag);
    this.on(canvas, 'pointercancel', endDrag);

    // 손가락: 두 손가락이면 지도 이동 + 벌리고 오므려서 확대·축소. 한 손가락이면 페이지가 내려가고 안내만 잠깐
    const touchMid = (t: TouchList) => {
      const rect = canvas.getBoundingClientRect();
      const a = t[0]!;
      const b = t[1]!;
      return {
        x: (a.clientX + b.clientX) / 2 - rect.left,
        y: (a.clientY + b.clientY) / 2 - rect.top,
        d: Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY),
      };
    };
    this.on(
      canvas,
      'touchstart',
      (e) => {
        if (e.touches.length < 2) return;
        e.preventDefault();
        const m = touchMid(e.touches);
        this.pinch = {
          m,
          scale: this.view.scale,
          wx: (m.x - this.size.w / 2) / this.view.scale + this.view.cx,
          wy: (m.y - this.size.h / 2) / this.view.scale + this.view.cy,
        };
        this.set({ follow: false });
        this.options.onTouchHint(false);
      },
      { passive: false },
    );
    this.on(
      canvas,
      'touchmove',
      (e) => {
        if (e.touches.length < 2) {
          if (!this.pinch) this.options.onTouchHint(true);
          return;
        }
        if (!this.pinch) return;
        e.preventDefault();
        const m = touchMid(e.touches);
        this.view.scale = this.pinch.scale * (this.pinch.m.d ? m.d / this.pinch.m.d : 1);
        this.clampView();
        // 처음 잡은 지도 지점이 지금 두 손가락 가운데에 오게
        this.view.cx = this.pinch.wx - (m.x - this.size.w / 2) / this.view.scale;
        this.view.cy = this.pinch.wy - (m.y - this.size.h / 2) / this.view.scale;
        this.draw();
      },
      { passive: false },
    );
    this.on(canvas, 'touchend', (e) => {
      if (e.touches.length < 2) this.pinch = null;
    });
    this.on(canvas, 'touchcancel', () => {
      this.pinch = null;
    });
  }

  // ---------- 재생 ----------

  private loop = () => {
    if (this.destroyed) return;
    const now = performance.now(); // 이펙트와 같은 시계 (rAF 시각은 브라우저마다 기준이 조금 달라서)
    const dt = this.lastFrame ? Math.min(0.25, (now - this.lastFrame) / 1000) : 0; // 탭을 오래 비웠다 와도 크게 안 건너뜀
    this.lastFrame = now;
    const { state } = this;
    if (state.playing && state.mode === 'play') {
      const before = state.t;
      let t = before + dt * this.speed;
      if (t >= this.R.duration) {
        t = this.R.duration;
        this.set({ t, playing: false });
      } else {
        this.set({ t });
      }
      this.spawnEffects(before, t, now);
    }
    if (this.state.follow && this.state.mode === 'play') this.followTeam(dt);
    this.draw();
    this.frame = requestAnimationFrame(this.loop);
  };

  /** 따라가기: 내가 살아 있으면 나, 아니면 살아 있는 팀원 가운데로 부드럽게 */
  private followTeam(dt: number) {
    const targets = this.model.team
      .map((m) => ({ m, p: posAt(this.model, m.track, this.state.t) }))
      .filter((o): o is { m: (typeof o)['m']; p: Pos } => !!o.p && !o.p.dead);
    const mine = targets.filter((o) => o.m.me);
    const use = mine.length ? mine : targets;
    if (!use.length) return;
    let x = 0;
    let y = 0;
    use.forEach((o) => {
      x += o.p.x;
      y += o.p.y;
    });
    x /= use.length;
    y /= use.length;
    const want = Math.min(this.size.w, this.size.h) / FOLLOW_WIDTH;
    const k = dt ? Math.min(1, dt * 4) : 1;
    this.view.cx += (x - this.view.cx) * k;
    this.view.cy += (y - this.view.cy) * k;
    this.view.scale += (want - this.view.scale) * k;
  }

  // ---------- 화면 좌표 ----------

  private sx(x: number) {
    return (x - this.view.cx) * this.view.scale + this.size.w / 2;
  }

  private sy(y: number) {
    return (y - this.view.cy) * this.view.scale + this.size.h / 2;
  }

  private fitAll() {
    this.view.cx = this.R.map.size / 2;
    this.view.cy = this.R.map.size / 2;
    this.view.scale = Math.min(this.size.w || 600, this.size.h || 600) / this.R.map.size;
  }

  private resize() {
    if (!this.started) return;
    const w = this.wrap.clientWidth;
    // 정사각형이 기본이지만, 화면보다 커지지 않게 창 높이의 70% 까지만 (최소 320px)
    // 폰은 지도를 위에 붙여 두므로 화면의 45% 까지만 (아래 칸이 보이게)
    const h = this.options.isNarrow()
      ? Math.round(Math.max(220, Math.min(w, window.innerHeight * 0.45)))
      : Math.round(Math.max(320, Math.min(w, window.innerHeight * 0.7)));
    const dpr = window.devicePixelRatio || 1;
    if (w === this.size.w && h === this.size.h && dpr === this.size.dpr) return;
    const fitBefore =
      this.size.w && Math.abs(this.view.scale - Math.min(this.size.w, this.size.h) / this.R.map.size) < 1e-9;
    this.size = { w, h, dpr };
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    this.canvas.style.height = `${h}px`;
    if (fitBefore || !this.view.scale) this.fitAll();
    this.draw();
  }

  private clampView() {
    const min = Math.min(this.size.w, this.size.h) / this.R.map.size;
    this.view.scale = Math.max(min, Math.min(this.view.scale, min * 24));
  }

  private fitTeam() {
    const xs: number[] = [];
    const ys: number[] = [];
    this.model.team.forEach((m) => {
      m.track.x.forEach((x, i) => {
        if (m.track.diedAt != null && m.track.t[i]! > m.track.diedAt) return;
        xs.push(x);
        ys.push(m.track.y[i]!);
      });
    });
    if (!xs.length) {
      this.fitAll();
      return;
    }
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    this.view.cx = (minX + maxX) / 2;
    this.view.cy = (minY + maxY) / 2;
    this.view.scale = Math.min(this.size.w, this.size.h) / (Math.max(maxX - minX, maxY - minY, 600) * 1.25);
    this.clampView();
    this.set({ follow: false });
  }

  // ---------- 색 ----------

  private readColors() {
    const cs = getComputedStyle(document.documentElement);
    const v = (name: string) => cs.getPropertyValue(name).trim();
    this.colors = { primary: v('--primary') || '#FFD02F', canvas: v('--canvas') || '#000000' };
    this.hatch = null;
  }

  private teamColor(team: number): string {
    let color = this.teamColors.get(team);
    if (!color) {
      color = TEAM_PALETTE[this.nextTeamColor % TEAM_PALETTE.length]!;
      this.teamColors.set(team, color);
      this.nextTeamColor++;
    }
    return color;
  }

  // ---------- 지도 ----------

  /** 지도 조각 하나. 처음 부르면 불러오기 시작하고, 다 오기 전까지는 complete 가 false */
  private tile(z: number, x: number, y: number): HTMLImageElement {
    const key = `${z}/${x}_${y}`;
    let t = this.tiles.get(key);
    if (!t) {
      t = new Image();
      t.decoding = 'async';
      t.src = `${this.R.map.tiles}/${key}.webp`;
      this.tiles.set(key, t);
    }
    return t;
  }

  private static ready(t: HTMLImageElement | undefined): t is HTMLImageElement {
    return !!t && t.complete && t.naturalWidth > 0;
  }

  /**
   * 지도: 화면 픽셀에 맞는 단계를 고르고, 보이는 조각만 그린다.
   * 아직 안 온 조각 자리는 이미 받아 둔 덜 자세한 단계가 아래에 깔려 있어서 비지 않는다.
   */
  private drawMap() {
    const { ctx, view, size } = this;
    const s = this.R.map.size;
    const px = s * view.scale * size.dpr; // 지도 한 변이 화면에서 몇 기기 픽셀인지
    const want = Math.max(0, Math.min(this.R.map.levels - 1, Math.ceil(Math.log2(px / TILE_PX))));
    // 화면에 보이는 지도 범위 (m)
    const x0 = view.cx - size.w / 2 / view.scale;
    const x1 = view.cx + size.w / 2 / view.scale;
    const y0 = view.cy - size.h / 2 / view.scale;
    const y1 = view.cy + size.h / 2 / view.scale;
    ctx.imageSmoothingQuality = 'high';
    for (let z = 0; z <= want; z++) {
      const n = 1 << z;
      const m = s / n; // 한 변 조각 수, 조각 한 변(m)
      const ix0 = Math.max(0, Math.floor(x0 / m));
      const ix1 = Math.min(n - 1, Math.floor(x1 / m));
      const iy0 = Math.max(0, Math.floor(y0 / m));
      const iy1 = Math.min(n - 1, Math.floor(y1 / m));
      for (let ix = ix0; ix <= ix1; ix++) {
        for (let iy = iy0; iy <= iy1; iy++) {
          // 원하는 단계만 새로 부르고, 그 아래 단계는 이미 받아 둔 것만 깔개로 쓴다
          const t = z === want ? this.tile(z, ix, iy) : this.tiles.get(`${z}/${ix}_${iy}`);
          if (!ReplayEngine.ready(t)) continue;
          // 조각 사이 틈이 보이지 않게 0.5px 겹쳐 그림
          const dx = this.sx(ix * m);
          const dy = this.sy(iy * m);
          const d = m * view.scale;
          ctx.drawImage(t, dx - 0.25, dy - 0.25, d + 0.5, d + 0.5);
        }
      }
    }
  }

  // ---------- 그리기 ----------

  draw() {
    if (!this.started || !ReplayEngine.ready(this.tiles.get('0/0_0')) || !this.size.w) return;
    const { ctx, size } = this;
    ctx.setTransform(size.dpr, 0, 0, size.dpr, 0, 0);
    ctx.fillStyle = this.colors.canvas;
    ctx.fillRect(0, 0, size.w, size.h);

    const s = this.R.map.size;
    this.drawMap();
    ctx.fillStyle = 'rgba(10,10,10,0.28)'; // 지도를 살짝 어둡게 해서 점이 잘 보이게
    ctx.fillRect(this.sx(0), this.sy(0), s * this.view.scale, s * this.view.scale);

    if (this.state.mode === 'play') this.drawPlay();
    else this.drawPaths();
  }

  private hatchPattern(): CanvasPattern | null {
    if (this.hatch) return this.hatch;
    const c = document.createElement('canvas');
    c.width = c.height = 10;
    const g = c.getContext('2d');
    if (!g) return null;
    g.strokeStyle = 'rgba(10,10,10,0.55)';
    g.lineWidth = 4;
    g.beginPath();
    g.moveTo(-2, 12);
    g.lineTo(12, -2);
    g.stroke();
    g.beginPath();
    g.moveTo(-2, 2);
    g.lineTo(2, -2);
    g.stroke();
    g.beginPath();
    g.moveTo(8, 12);
    g.lineTo(12, 8);
    g.stroke();
    this.hatch = this.ctx.createPattern(c, 'repeat');
    return this.hatch;
  }

  private drawZone(z: Zone | null, showSafe: boolean) {
    if (!z) return;
    const { ctx } = this;
    if (showSafe && z.safeR > 0) {
      // 자기장 밖 빗금
      ctx.save();
      ctx.beginPath();
      ctx.rect(this.sx(0), this.sy(0), this.R.map.size * this.view.scale, this.R.map.size * this.view.scale);
      ctx.arc(this.sx(z.safeX), this.sy(z.safeY), z.safeR * this.view.scale, 0, Math.PI * 2, true);
      const pattern = this.hatchPattern();
      if (pattern) ctx.fillStyle = pattern;
      ctx.fill('evenodd');
      ctx.restore();
      this.circle(z.safeX, z.safeY, z.safeR, MAP_COLORS.ink, 1.5, [4, 4], 0.6);
    }
    if (z.nextR > 0) this.circle(z.nextX, z.nextY, z.nextR, MAP_COLORS.ink, 2.5, null, 1);
  }

  private circle(x: number, y: number, r: number, color: string, width: number, dash: number[] | null, alpha: number) {
    const { ctx } = this;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    if (dash) ctx.setLineDash(dash);
    ctx.beginPath();
    ctx.arc(this.sx(x), this.sy(y), r * this.view.scale, 0, Math.PI * 2);
    ctx.stroke();
    ctx.restore();
  }

  private drawPlane() {
    const plane = this.R.plane;
    if (!plane) return;
    const { ctx } = this;
    ctx.save();
    ctx.strokeStyle = MAP_COLORS.ink;
    ctx.globalAlpha = 0.6;
    ctx.lineWidth = 2;
    ctx.setLineDash([2, 8]);
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(this.sx(plane.x1), this.sy(plane.y1));
    ctx.lineTo(this.sx(plane.x2), this.sy(plane.y2));
    ctx.stroke();
    ctx.restore();
  }

  /** 보급상자: 낙하산(반원 + 줄) 아래 끈 두른 상자 */
  private drawPackages(until: number) {
    this.R.events.forEach((e) => {
      if (e.type !== 'package' || e.t > until) return;
      this.packageIcon(this.sx(e.x), this.sy(e.y));
    });
  }

  private packageIcon(x: number, y: number) {
    const { ctx } = this;
    ctx.save();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = MAP_COLORS.ink;
    // 낙하산
    ctx.beginPath();
    ctx.arc(x, y - 9, 8, Math.PI, 0);
    ctx.closePath();
    ctx.fillStyle = 'rgba(255,255,255,0.25)';
    ctx.fill();
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(x - 8, y - 9);
    ctx.lineTo(x - 5, y - 2);
    ctx.moveTo(x + 8, y - 9);
    ctx.lineTo(x + 5, y - 2);
    ctx.stroke();
    // 상자
    ctx.fillStyle = MAP_COLORS.card;
    ctx.lineWidth = 2;
    ctx.fillRect(x - 6, y - 2, 12, 11);
    ctx.strokeRect(x - 6, y - 2, 12, 11);
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(x, y - 2);
    ctx.lineTo(x, y + 9); // 끈
    ctx.moveTo(x - 6, y + 3.5);
    ctx.lineTo(x + 6, y + 3.5);
    ctx.stroke();
    ctx.restore();
  }

  /**
   * 차량·보트 아이콘 (위에서 본 모양, 가는 방향으로 회전). color = 탄 사람 색
   * 차량: 둥근 직사각형 + 앞유리, 보트: 앞이 뾰족한 배
   */
  private vehicleIcon(x: number, y: number, heading: number, kind: number, color: string, hollow: boolean) {
    const { ctx } = this;
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(heading);
    ctx.beginPath();
    if (kind === 2) {
      ctx.moveTo(13, 0);
      ctx.quadraticCurveTo(4, -8, -10, -7);
      ctx.lineTo(-10, 7);
      ctx.quadraticCurveTo(4, 8, 13, 0);
    } else {
      this.roundRect(-11, -6.5, 22, 13, 4);
    }
    ctx.closePath();
    ctx.fillStyle = hollow ? MAP_COLORS.dark : color;
    ctx.fill();
    ctx.lineWidth = hollow ? 2.5 : 2;
    ctx.strokeStyle = hollow ? color : MAP_COLORS.dark;
    ctx.stroke();
    // 앞쪽 표시 (앞유리 / 뱃머리 선)
    ctx.beginPath();
    if (kind === 2) {
      ctx.moveTo(2, -4);
      ctx.lineTo(2, 4);
    } else {
      ctx.moveTo(4, -4.5);
      ctx.lineTo(4, 4.5);
    }
    ctx.lineWidth = 2;
    ctx.strokeStyle = hollow ? color : MAP_COLORS.dark;
    ctx.stroke();
    ctx.restore();
  }

  private roundRect(x: number, y: number, w: number, h: number, r: number) {
    const { ctx } = this;
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
  }

  // ---------- 이펙트 (실제 시간 기준) ----------

  private buildFxSources(): FxSource[] {
    const list: FxSource[] = [];
    (this.R.blasts ?? []).forEach((b) => list.push({ t: b.t, fx: 'blast', b }));
    (this.R.heals ?? []).forEach((h) => list.push({ t: h.t, fx: 'heal', h }));
    return list.sort((a, b) => a.t - b.t);
  }

  /** 재생이 from → to 로 지나간 사이에 있는 순간들의 이펙트를 시작 */
  private spawnEffects(from: number, to: number, now: number) {
    if (!this.fxSources) this.fxSources = this.buildFxSources();
    if (to - from > 60) return; // 크게 건너뛴 건 재생이 아니라 이동
    this.fxSources.forEach((src) => {
      if (src.t > from && src.t <= to) this.fx.push({ src, at: now });
    });
  }

  /**
   * layer 'under' = 폭발물 (점 아래에 깔아서 사람 위치를 가리지 않게)
   * layer 'over'  = 회복 (쓴 사람 점에 가려지지 않게 맨 위에)
   */
  private drawEffects(now: number, layer: 'under' | 'over') {
    if (layer === 'under') this.fx = this.fx.filter((f) => now - f.at < ReplayEngine.fxLength(f.src));
    this.fx.forEach((f) => {
      if ((f.src.fx === 'blast') !== (layer === 'under')) return;
      const ms = now - f.at;
      this.ctx.save();
      if (f.src.fx === 'blast') this.drawBlast(f.src.b, ms);
      else this.drawHeal(f.src.h, ms);
      this.ctx.restore();
    });
  }

  private static flightMs(kind: string) {
    return kind === 'mortar' ? 650 : kind === 'panzer' ? 260 : FLIGHT_MS;
  }

  private static fxLength(src: FxSource) {
    if (src.fx === 'blast') return ReplayEngine.flightMs(src.b.kind) + (src.b.kind === 'molotov' ? FX_MS * 1.4 : FX_MS);
    return HEAL_MS;
  }

  /** 잡은 한 발은 크게, 기절은 조금 작게, 맞히기만 했으면 작게 */
  private static blastSize(b: Blast) {
    const big = b.kind === 'c4' || b.kind === 'mortar' || b.kind === 'panzer' ? 1.25 : 1;
    return big * (b.result === 'kill' ? 1.15 : b.result === 'knock' ? 1 : 0.65);
  }

  private drawBlast(b: Blast, ms: number) {
    const { ctx } = this;
    const fly = ReplayEngine.flightMs(b.kind);
    const x0 = this.sx(b.fromX);
    const y0 = this.sy(b.fromY);
    const x1 = this.sx(b.x);
    const y1 = this.sy(b.y);
    const dist = Math.hypot(x1 - x0, y1 - y0);
    if (ms < fly) {
      const k = ms / fly;
      if (b.kind === 'panzer') {
        // 로켓: 곧게 날아가며 뒤에 연기
        const hx = x0 + (x1 - x0) * k;
        const hy = y0 + (y1 - y0) * k;
        ctx.lineCap = 'round';
        ctx.strokeStyle = MAP_COLORS.ink;
        ctx.globalAlpha = 0.35;
        ctx.lineWidth = 6;
        ctx.beginPath();
        ctx.moveTo(x0, y0);
        ctx.lineTo(hx, hy);
        ctx.stroke();
        ctx.globalAlpha = 1;
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(x0 + (hx - x0) * 0.6, y0 + (hy - y0) * 0.6);
        ctx.lineTo(hx, hy);
        ctx.stroke();
        ctx.fillStyle = this.colors.primary;
        ctx.beginPath();
        ctx.arc(hx, hy, 3.5, 0, Math.PI * 2);
        ctx.fill();
        return;
      }
      // 포물선: 화면에서 위로 휜다 (박격포는 높게)
      const lift = Math.min(160, dist * (b.kind === 'mortar' ? 0.9 : 0.35) + 12);
      const cx = (x0 + x1) / 2;
      const cy = (y0 + y1) / 2 - lift;
      const px = (1 - k) * (1 - k) * x0 + 2 * (1 - k) * k * cx + k * k * x1;
      const py = (1 - k) * (1 - k) * y0 + 2 * (1 - k) * k * cy + k * k * y1;
      ctx.setLineDash([3, 4]);
      ctx.globalAlpha = 0.7;
      ctx.strokeStyle = MAP_COLORS.ink;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(x0, y0);
      for (let i = 1; i <= 16; i++) {
        const u = (k * i) / 16;
        ctx.lineTo(
          (1 - u) * (1 - u) * x0 + 2 * (1 - u) * u * cx + u * u * x1,
          (1 - u) * (1 - u) * y0 + 2 * (1 - u) * u * cy + u * u * y1,
        );
      }
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.globalAlpha = 1;
      ctx.fillStyle = b.kind === 'molotov' ? this.colors.primary : MAP_COLORS.ink;
      ctx.strokeStyle = MAP_COLORS.dark;
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(px, py, 4, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      return;
    }
    const size = ReplayEngine.blastSize(b);
    if (b.kind === 'molotov') {
      this.drawFire(x1, y1, (ms - fly) / (FX_MS * 1.4), size);
      return;
    }
    this.drawExplosion(x1, y1, (ms - fly) / FX_MS, size);
  }

  /** 폭발: 하얀 섬광 + 노란 충격파 고리 두 겹 + 파편. k = 0 → 1 */
  private drawExplosion(x: number, y: number, k: number, size: number) {
    const { ctx } = this;
    const primary = this.colors.primary;
    const e = 1 - Math.pow(1 - k, 3); // 처음에 빨리 퍼지고 천천히 멈춤
    // 섬광
    ctx.globalAlpha = Math.max(0, 1 - k * 2.2);
    ctx.fillStyle = MAP_COLORS.ink;
    ctx.beginPath();
    ctx.arc(x, y, (10 + 16 * e) * size, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = Math.max(0, 0.55 - k);
    ctx.fillStyle = primary;
    ctx.beginPath();
    ctx.arc(x, y, (18 + 22 * e) * size, 0, Math.PI * 2);
    ctx.fill();
    // 충격파
    ctx.globalAlpha = Math.max(0, 1 - k);
    ctx.strokeStyle = primary;
    ctx.lineWidth = 4 * size;
    ctx.beginPath();
    ctx.arc(x, y, (12 + 44 * e) * size, 0, Math.PI * 2);
    ctx.stroke();
    ctx.globalAlpha = Math.max(0, 0.6 - k);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(x, y, (8 + 30 * e) * size, 0, Math.PI * 2);
    ctx.stroke();
    // 파편
    ctx.globalAlpha = Math.max(0, 1 - k * 1.2);
    ctx.strokeStyle = MAP_COLORS.ink;
    ctx.lineCap = 'round';
    ctx.lineWidth = 2.5;
    for (let j = 0; j < 10; j++) {
      const a = (j / 10) * Math.PI * 2 + 0.3;
      const r1 = (14 + 38 * e) * size;
      const r2 = r1 + 9 * size;
      ctx.beginPath();
      ctx.moveTo(x + Math.cos(a) * r1, y + Math.sin(a) * r1);
      ctx.lineTo(x + Math.cos(a) * r2, y + Math.sin(a) * r2);
      ctx.stroke();
    }
  }

  /** 화염병: 불 번진 자리(흐린 원) 위에 불꽃이 일렁이다 꺼짐 */
  private drawFire(x: number, y: number, k: number, size: number) {
    const { ctx } = this;
    const primary = this.colors.primary;
    const fade = k < 0.75 ? 1 : 1 - (k - 0.75) / 0.25;
    ctx.globalAlpha = 0.28 * fade;
    ctx.fillStyle = primary;
    ctx.beginPath();
    ctx.arc(x, y, 26 * size * Math.min(1, k * 5), 0, Math.PI * 2);
    ctx.fill();
    for (let i = 0; i < 11; i++) {
      const a = (i / 11) * Math.PI * 2 + i;
      const r = ((6 + (15 * ((i * 37) % 10)) / 10) * size * Math.min(1, k * 5));
      const fx = x + Math.cos(a) * r;
      const fy = y + Math.sin(a) * r - Math.abs(Math.sin(k * 14 + i)) * 5;
      const h = (5 + 4 * Math.abs(Math.sin(k * 11 + i * 1.3))) * size;
      ctx.globalAlpha = 0.95 * fade;
      ctx.fillStyle = i % 3 ? primary : MAP_COLORS.ink;
      ctx.beginPath(); // 불꽃 모양 (아래 둥글고 위가 뾰족)
      ctx.moveTo(fx, fy - h * 1.6);
      ctx.quadraticCurveTo(fx + h, fy, fx, fy + h * 0.6);
      ctx.quadraticCurveTo(fx - h, fy, fx, fy - h * 1.6);
      ctx.fill();
    }
  }

  /** 회복: 쓴 사람 둘레가 초록으로 잠깐 빛나고, 초록 플러스 세 개가 차례로 올라가며 사라진다 */
  private drawHeal(h: Heal, ms: number) {
    const { ctx } = this;
    const m = this.model.byName.get(h.name);
    const p = m ? posAt(this.model, m, this.state.t) : null; // 쓴 사람을 따라다님
    if (p && p.dead) return;
    const x = this.sx(p ? p.x : h.x);
    const y = this.sy(p ? p.y : h.y);
    const k = ms / HEAL_MS;
    const glow = k < 0.15 ? k / 0.15 : 1 - Math.max(0, (k - 0.55) / 0.45);
    ctx.globalAlpha = 0.28 * glow;
    ctx.fillStyle = MAP_COLORS.heal;
    ctx.beginPath();
    ctx.arc(x, y, 18, 0, Math.PI * 2);
    ctx.fill();
    (
      [
        [-9, 0],
        [8, 0.14],
        [0, 0.28],
      ] as const
    ).forEach(([dx, delay]) => {
      const kk = k - delay;
      if (kk <= 0) return;
      const a = Math.max(0, 1 - kk / 0.75);
      this.plusSign(x + dx, y - 14 - kk * 34, 11, a);
    });
  }

  /** 초록 플러스 (검은 테두리) */
  private plusSign(x: number, y: number, s: number, alpha: number) {
    const { ctx } = this;
    const w = s / 3;
    ctx.globalAlpha = alpha;
    ctx.beginPath();
    ctx.rect(x - w / 2, y - s / 2, w, s);
    ctx.rect(x - s / 2, y - w / 2, s, w);
    ctx.lineWidth = 2;
    ctx.strokeStyle = MAP_COLORS.dark;
    ctx.stroke();
    ctx.fillStyle = MAP_COLORS.heal;
    ctx.fill();
  }

  private trail(p: Track, from: number, to: number, color: string, width: number, alpha: number) {
    const pts: Array<[number, number]> = [];
    for (let i = 0; i < p.t.length; i++) {
      const ti = p.t[i]!;
      if (ti < from) continue;
      if (ti > to) break;
      if (p.diedAt != null && ti > p.diedAt) break;
      pts.push([p.x[i]!, p.y[i]!]);
    }
    const now = posAt(this.model, p, to);
    if (now) pts.push([now.x, now.y]);
    const first = pts[0];
    if (!first || pts.length < 2) return;
    const { ctx } = this;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(this.sx(first[0]), this.sy(first[1]));
    for (let j = 1; j < pts.length; j++) ctx.lineTo(this.sx(pts[j]![0]), this.sy(pts[j]![1]));
    ctx.stroke();
    ctx.restore();
  }

  private cross(x: number, y: number, color: string, alpha: number, r: number) {
    const { ctx } = this;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.strokeStyle = color;
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(x - r, y - r);
    ctx.lineTo(x + r, y + r);
    ctx.moveTo(x + r, y - r);
    ctx.lineTo(x - r, y + r);
    ctx.stroke();
    ctx.restore();
  }

  /** 부채꼴 시야: 점에서 앞으로 옅게 번지는 빛 */
  private visionWedge(x: number, y: number, v: Vision, color: string, alpha: number) {
    const { ctx } = this;
    const half = v.shoot ? 0.35 : 0.75;
    const r = v.shoot ? 110 : 80;
    const g = ctx.createRadialGradient(x, y, 4, x, y, r);
    g.addColorStop(0, color);
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.save();
    ctx.globalAlpha = alpha * (v.shoot ? 0.55 : 0.26);
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.arc(x, y, r, v.a - half, v.a + half);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }

  /** 체력 막대: 점 아래 가운데. 바탕은 어둡게, 남은 만큼 색으로 */
  private hpBar(x: number, y: number, hp: number, w: number, color: string) {
    const { ctx } = this;
    const h = w > 18 ? 4 : 3;
    ctx.save();
    ctx.fillStyle = MAP_COLORS.dark;
    ctx.globalAlpha *= 0.85;
    ctx.fillRect(x - w / 2 - 1, y - 1, w + 2, h + 2);
    ctx.globalAlpha /= 0.85;
    ctx.fillStyle = color;
    ctx.fillRect(x - w / 2, y, (w * Math.max(0, Math.min(100, hp))) / 100, h);
    ctx.restore();
  }

  /** 번호가 적힌 동그라미 (팀원, 교전) */
  private badge(x: number, y: number, r: number, fill: string, text: string, hollow: boolean) {
    const { ctx } = this;
    ctx.save();
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    if (hollow) {
      ctx.fillStyle = MAP_COLORS.dark;
      ctx.fill();
      ctx.lineWidth = 3;
      ctx.strokeStyle = fill;
      ctx.stroke();
    } else {
      ctx.fillStyle = fill;
      ctx.fill();
      ctx.lineWidth = 2.5;
      ctx.strokeStyle = '#0A0A0A';
      ctx.stroke();
    }
    if (text) {
      ctx.fillStyle = hollow ? fill : '#0A0A0A';
      ctx.font = `800 ${Math.round(r * 1.15)}px Inter, Pretendard, sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(text, x, y + 0.5);
    }
    ctx.restore();
  }

  private drawPlay() {
    const { ctx, model } = this;
    const t = this.state.t;
    const primary = this.colors.primary;
    this.drawZone(zoneAt(this.R, t), true);
    this.drawPlane();
    this.drawPackages(t);

    // 우리 팀 지나온 길
    model.team.forEach((m) => {
      this.trail(m.track, t - TRAIL_SECONDS, t, m.me ? primary : MAP_COLORS.ink, m.me ? 3 : 2, m.me ? 0.9 : 0.5);
    });

    // 최근 킬 ✕ (우리 팀 근처 것만)
    const teamPos = model.team
      .map((m) => posAt(model, m.track, t))
      .filter((p): p is Pos => !!p && !p.dead);
    this.R.events.forEach((e) => {
      if (e.type !== 'kill' || e.t > t || e.t < t - KILL_MARK_SECONDS) return;
      if (!e.ours && nearness(e.x, e.y, teamPos, this.nearMeters) === 0) return;
      const a = 1 - (t - e.t) / KILL_MARK_SECONDS;
      this.cross(this.sx(e.x), this.sy(e.y), e.ours ? MAP_COLORS.ink : MAP_COLORS.muted, a, 7);
    });

    // 근처 적: 팀마다 색, 팀마다 한 명 옆에 "N팀"
    const involved = involvedEnemies(model, t);
    const labeled = new Set<number>();
    model.enemies.forEach((p) => {
      const pos = posAt(model, p, t);
      if (!pos || pos.dead) return;
      // 1 = 범위 안 또는 우리와 싸우는 중, 0.35 = 범위를 조금 넘음(흐리게), 0 = 안 보여줌
      const alpha = involved.has(p.name) ? 1 : nearness(pos.x, pos.y, teamPos, this.nearMeters);
      if (alpha === 0) return;
      const color = this.teamColor(p.team);
      const x = this.sx(pos.x);
      const y = this.sy(pos.y);
      if (!pos.vehicle && !pos.knocked) {
        const ev = visionAt(p, pos, t);
        // 적은 사람이 많아서 움직이는 방향 시야는 더 옅게 (쏜 방향은 그대로)
        if (ev) this.visionWedge(x, y, ev, color, ev.shoot ? alpha : alpha * 0.55);
      }
      ctx.save();
      ctx.globalAlpha = alpha;
      if (pos.vehicle) {
        this.vehicleIcon(x, y, pos.heading, pos.vehicle, color, false);
      } else {
        ctx.beginPath();
        ctx.arc(x, y, 7, 0, Math.PI * 2);
        if (pos.knocked) {
          ctx.fillStyle = MAP_COLORS.dark;
          ctx.fill();
          ctx.lineWidth = 3;
          ctx.strokeStyle = color;
          ctx.stroke();
        } else {
          ctx.fillStyle = color;
          ctx.fill();
          ctx.lineWidth = 2;
          ctx.strokeStyle = MAP_COLORS.dark;
          ctx.stroke();
        }
      }
      // 적 체력: 깎였을 때만 (다 찬 막대까지 그리면 지도가 복잡해서)
      if (!pos.knocked && pos.hp !== null && pos.hp < 100) this.hpBar(x, y + 12, pos.hp, 16, color);
      if (!labeled.has(p.team)) {
        labeled.add(p.team);
        ctx.font = '700 12px Pretendard, Inter, sans-serif';
        ctx.textBaseline = 'middle';
        ctx.lineWidth = 3;
        ctx.strokeStyle = MAP_COLORS.dark; // 글자 테두리: 지도 무늬 위에서도 읽히게
        ctx.strokeText(`${p.team}팀`, x + 11, y - 10);
        ctx.fillStyle = color;
        ctx.fillText(`${p.team}팀`, x + 11, y - 10);
      }
      ctx.restore();
    });

    this.drawHits(t);
    this.drawEffects(performance.now(), 'under');

    // 우리 팀 시야 (점보다 아래에 깔리게 먼저)
    model.team.forEach((m) => {
      const pos = posAt(model, m.track, t);
      if (!pos || pos.dead || pos.knocked || pos.vehicle) return;
      const v = visionAt(m.track, pos, t);
      if (v) this.visionWedge(this.sx(pos.x), this.sy(pos.y), v, v.shoot ? primary : MAP_COLORS.ink, 1);
    });

    // 우리 팀 (나를 맨 위에)
    model.team
      .slice()
      .reverse()
      .forEach((m) => {
        const pos = posAt(model, m.track, t);
        if (!pos) return;
        const x = this.sx(pos.x);
        const y = this.sy(pos.y);
        if (pos.dead) {
          this.cross(x, y, MAP_COLORS.muted, 0.9, 6);
          return;
        }
        const color = m.me ? primary : MAP_COLORS.ink;
        if (m.me && !pos.knocked) {
          ctx.save();
          ctx.globalAlpha = 0.25;
          ctx.fillStyle = primary;
          ctx.beginPath();
          ctx.arc(x, y, 16, 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
        }
        if (pos.vehicle) {
          this.vehicleIcon(x, y, pos.heading, pos.vehicle, color, false);
          this.badge(x + 12, y - 11, 7, color, String(m.n), false);
        } else {
          this.badge(x, y, 9.5, color, String(m.n), pos.knocked);
        }
        if (!pos.knocked && pos.hp !== null) this.hpBar(x, y + 15, pos.hp, 22, color);
      });
    this.drawEffects(performance.now(), 'over');
  }

  /**
   * 명중선: 맞힌 순간부터 HIT_SECONDS 동안 쏜 사람 → 맞은 사람.
   *  - 우리 팀이 맞힘: 노란 실선 (나는 굵게) + 맞은 적에 노란 고리
   *  - 적이 우리를 맞힘: 흰 점선
   * 선 끝은 지금 화면의 점 위치에 맞춘다 (점과 선이 어긋나지 않게). 사람을 못 찾으면 맞힌 순간 기록 위치
   */
  private drawHits(t: number) {
    const { ctx, model } = this;
    (this.R.hits ?? []).forEach((h) => {
      if (h.t > t || t - h.t > HIT_SECONDS) return;
      const attacker = model.byName.get(h.attacker);
      const victim = model.byName.get(h.victim);
      const a = attacker ? posAt(model, attacker, t) : null;
      const v = victim ? posAt(model, victim, t) : null;
      const ax = this.sx(a ? a.x : h.ax);
      const ay = this.sy(a ? a.y : h.ay);
      const vx = this.sx(v ? v.x : h.vx);
      const vy = this.sy(v ? v.y : h.vy);
      const alpha = 1 - (t - h.t) / HIT_SECONDS;
      ctx.save();
      ctx.globalAlpha = Math.max(0.15, alpha);
      ctx.lineCap = 'round';
      if (h.ours) {
        const mine = !!attacker && attacker.id === model.me;
        ctx.strokeStyle = this.colors.primary;
        ctx.lineWidth = mine ? 3 : 2;
      } else {
        ctx.strokeStyle = MAP_COLORS.ink;
        ctx.lineWidth = 1.5;
        ctx.setLineDash([5, 4]);
      }
      ctx.beginPath();
      ctx.moveTo(ax, ay);
      ctx.lineTo(vx, vy);
      ctx.stroke();
      if (h.ours) {
        ctx.setLineDash([]);
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(vx, vy, 12, 0, Math.PI * 2);
        ctx.stroke();
      }
      ctx.restore();
    });
  }

  // ---------- 동선 전체 보기 ----------

  private drawPaths() {
    const { ctx, model } = this;
    const primary = this.colors.primary;
    const range = phaseRange(this.R, this.phase);
    // 그 페이즈의 다음 안전구역(흰 원). 전체면 마지막 원
    const z = zoneAt(this.R, this.phase ? Math.min(range[1] - 1, this.R.duration) : this.R.duration);
    this.drawZone(z, false);
    this.drawPlane();

    const who = this.who === 'me' ? model.team.filter((m) => m.me) : model.team;
    who.forEach((m) => {
      const color = m.me ? primary : MAP_COLORS.ink;
      this.trail(m.track, range[0], range[1], color, m.me ? 3.5 : 2.5, m.me ? 1 : 0.65);
      // 착지 지점
      const landing = this.R.events.find((e) => e.type === 'landing' && e.actor === m.track.name);
      if (landing && landing.t >= range[0] && landing.t <= range[1]) {
        ctx.save();
        ctx.strokeStyle = color;
        ctx.lineWidth = 2.5;
        ctx.beginPath();
        ctx.arc(this.sx(landing.x), this.sy(landing.y), 7, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }
      // 끝난 곳
      const end = posAt(model, m.track, range[1]);
      if (end) {
        if (end.dead) this.cross(this.sx(end.x), this.sy(end.y), color, 1, 6);
        else this.badge(this.sx(end.x), this.sy(end.y), 8, color, String(m.n), false);
      }
    });

    // 교전 번호
    this.R.fights.forEach((f) => {
      if (f.end < range[0] || f.start > range[1]) return;
      this.badge(this.sx(f.x), this.sy(f.y), 12, MAP_COLORS.ink, String(f.n), false);
    });
  }
}
