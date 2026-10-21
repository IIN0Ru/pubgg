/*
 * 2D 리플레이 (templates/replay.html)
 *
 * 1) /api/replay 에서 경기 데이터를 받는다 (ReplayBuilder 가 줄인 것: 위치·자기장·사건·교전)
 * 2) 캔버스에 지도 이미지 + 자기장 + 사람들을 그린다. 위치는 약 10초마다 있어서 그 사이는 직선으로 이어서 움직인다
 * 3) 두 가지 보기
 *    - 재생: 재생·배속·타임라인. 오른쪽 "경기 흐름"을 누르면 그 순간으로
 *    - 동선 전체: 우리 팀 동선을 한 번에, 교전 지점에 번호. 오른쪽은 교전 목록
 *
 * 좌표: 서버가 m 단위로 준다. 지도 이미지 한 변 = map.size(m).
 * 색: 지도 이미지는 다크/라이트와 상관없이 늘 어두워서, 지도 위에 그리는 색은 고정(MAP_COLORS)하고
 *     지도 바깥 바탕색과 노랑만 main.css 변수에서 읽는다. 다크/라이트를 바꾸면 다시 읽는다.
 */
(function () {
  'use strict';

  var root = document.querySelector('[data-replay]');
  if (!root || !root.querySelector('[data-canvas]')) return;

  // ---------- 설정 ----------
  var nearMeters = 500;      // 우리 팀 누구와든 이 거리 안의 적을 보여준다 (화면에서 300m/500m/1km/전체 고름)
  var FAR_FADE = 1.3;        // 범위의 1.3배 안은 흐리게 먼저 보여준다 (갑자기 튀어나오지 않게)
  var FIGHT_LEAD_SECONDS = 30;  // 우리와 싸울 적은 첫 기절·명중 30초 전부터 거리와 상관없이 보여준다
  var FIGHT_TAIL_SECONDS = 10;  // 마지막으로 주고받은 뒤 10초까지
  var TRAIL_SECONDS = 90;    // 재생 중 보여주는 지나온 길 길이
  var KILL_MARK_SECONDS = 12; // ✕ 표시가 남아 있는 시간
  var HIT_SECONDS = 1.5;     // 명중선이 남아 있는 시간
  var FOLLOW_WIDTH = 1400;   // 따라가기 때 화면 가로에 들어오는 거리 (m)

  // ---------- 화면 요소 ----------
  var q = function (sel) { return root.querySelector(sel); };
  var canvas = q('[data-canvas]');
  var ctx = canvas.getContext('2d');
  var wrap = q('[data-canvas-wrap]');
  var statusEl = q('[data-status]');
  var playBtn = q('[data-play]');
  var timeEl = q('[data-time]');
  var timeline = q('[data-timeline]');
  var doneEl = q('[data-done]');
  var knobEl = q('[data-knob]');
  var marksEl = q('[data-marks]');
  var feedEl = q('[data-feed]');
  var teamEl = q('[data-team]');
  var aliveEl = q('[data-alive]');
  var followBtn = q('[data-follow]');
  var playControls = q('[data-play-controls]');
  var pathControls = q('[data-path-controls]');
  var flowTitle = q('[data-flow-title]');
  var flowHint = q('[data-flow-hint]');
  var feedFilterEl = q('[data-feed-filter]');

  // ---------- 상태 ----------
  var R = null;               // 경기 데이터
  var tiles = {};             // 지도 조각 "단계/x_y" → Image
  var TILE_PX = 512;
  var team = [];              // 우리 팀 (나 먼저) {track, n}
  var enemies = [];
  var byName = {};            // 닉네임 → track
  var knockedSpans = {};      // 계정 ID → [[기절 시각, 일어나거나 죽은 시각], ...]
  var feedItems = [];         // 경기 흐름 목록 {t, el}
  var state = {
    t: 0, playing: false, speed: 4, mode: 'play', follow: true,
    phase: 0, who: 'team', feedFilter: 'all', lastFeed: -1
  };
  var view = { cx: 0, cy: 0, scale: 1 };   // 화면 가운데의 지도 좌표(m), 1m 당 픽셀
  var size = { w: 0, h: 0, dpr: 1 };
  var colors = {};
  var hatch = null;

  // ---------- 색 (main.css 변수) ----------
  // 지도 위 색: 어두운 지도 위에서 잘 보이게 고정 (다크 모드 값과 같음)
  var MAP_COLORS = { ink: '#FFFFFF', muted: '#9A9A9A', card: '#1A1A1A', dark: '#0A0A0A' };

  // 적 팀 색: 근처에 여러 팀이 섞여 있을 때 구분하려고 지도 위에서만 쓰는 색 (디자인 규칙의 예외. NOTES 참고)
  // 노랑(나)·흰색(팀원)·회색과 헷갈리지 않는 색만. 처음 마주친 순서대로 돌려 쓴다
  // 서로 헷갈리지 않는 5가지만. 비슷한 파랑·보라·초록을 여러 개 넣으면 오히려 구분이 안 되고,
  // 주황·노랑 계열은 나(노랑)와 헷갈려서 뺐다. 300m 안에 6팀 이상 붙는 일은 드물어서 넘치면 처음 색부터 다시 쓴다
  var TEAM_PALETTE = ['#FF7A59', '#38BDF8', '#C084FC', '#4ADE80', '#F472B6'];
  var teamColors = {};
  var nextTeamColor = 0;

  function teamColor(team) {
    if (!(team in teamColors)) {
      teamColors[team] = TEAM_PALETTE[nextTeamColor % TEAM_PALETTE.length];
      nextTeamColor++;
    }
    return teamColors[team];
  }

  function readColors() {
    var cs = getComputedStyle(document.documentElement);
    var v = function (name) { return cs.getPropertyValue(name).trim(); };
    colors = {
      primary: v('--primary'),
      canvas: v('--canvas'),          // 지도 바깥 바탕
      ink: MAP_COLORS.ink, muted: MAP_COLORS.muted, card: MAP_COLORS.card, dark: MAP_COLORS.dark
    };
    hatch = null;
  }
  readColors();
  new MutationObserver(function () { readColors(); draw(); })
      .observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

  // ---------- 데이터 받기 ----------
  fetch(root.getAttribute('data-src'), { headers: { Accept: 'application/json' } })
      .then(function (res) {
        return res.json().then(function (body) {
          if (!res.ok) throw new Error(body && body.message ? body.message : '리플레이를 불러오지 못했어요.');
          return body;
        });
      })
      .then(function (data) {
        R = data;
        prepare();
        // 지도 전체 한 조각(단계 0)이 오면 시작. 더 자세한 조각은 확대한 만큼 그때그때 불러온다
        var base = tile(0, 0, 0);
        base.onload = function () {
          statusEl.hidden = true;
          fitAll();
          resize();
          paintTime();   // 처음 시각(착지 직전)을 시간 표시·타임라인에 반영
          requestAnimationFrame(loop);
        };
        base.onerror = function () { statusEl.textContent = '지도 이미지를 불러오지 못했어요.'; };
      })
      .catch(function (err) {
        statusEl.textContent = err.message || '리플레이를 불러오지 못했어요.';
        statusEl.classList.add('replay__status--error');
      });

  // ---------- 준비 ----------
  function prepare() {
    var me = root.getAttribute('data-me');
    R.players.forEach(function (p) { byName[p.name] = p; });
    var mine = R.players.filter(function (p) { return p.team === R.myTeam; });
    mine.sort(function (a, b) { return (a.id === me ? -1 : 0) - (b.id === me ? -1 : 0) || a.name.localeCompare(b.name); });
    team = mine.map(function (p, i) { return { track: p, n: i + 1, me: p.id === me }; });
    enemies = R.players.filter(function (p) { return p.team !== R.myTeam; });

    // 기절 구간: 기절 → (부활 또는 죽음 또는 끝)
    R.events.forEach(function (e) {
      var target = e.target && byName[e.target];
      if (!target) return;
      var spans = knockedSpans[target.id] || (knockedSpans[target.id] = []);
      if (e.type === 'knock') spans.push([e.t, R.duration]);
      if ((e.type === 'revive' || e.type === 'kill') && spans.length && spans[spans.length - 1][1] === R.duration) {
        spans[spans.length - 1][1] = e.t;
      }
    });

    buildTimeline();
    buildPhaseTabs();
    buildFeed();
    state.t = firstMoment();
  }

  /** 처음 보여줄 시각: 우리 팀 착지 직전. 단, 우리 팀 위치 기록이 처음 생기는 때보다 앞서지 않게 (빈 지도 방지) */
  function firstMoment() {
    var landing = R.events.find(function (e) { return e.ours && e.type === 'landing'; });
    var firstSeen = Infinity;
    team.forEach(function (m) { if (m.track.t.length) firstSeen = Math.min(firstSeen, m.track.t[0]); });
    var t = landing ? landing.t - 5 : 0;
    if (firstSeen !== Infinity) t = Math.max(t, firstSeen);
    return Math.max(0, t);
  }

  // ---------- 위치 계산 ----------
  function indexAt(arr, t) {   // arr[i] <= t 인 마지막 i
    var lo = 0, hi = arr.length - 1, ans = -1;
    while (lo <= hi) {
      var mid = (lo + hi) >> 1;
      if (arr[mid] <= t) { ans = mid; lo = mid + 1; } else { hi = mid - 1; }
    }
    return ans;
  }

  /** t 초에 이 사람이 있는 곳. 아직 기록이 없으면 null */
  function posAt(p, t) {
    if (!p.t.length || t < p.t[0]) return null;
    var dead = p.diedAt !== null && p.diedAt !== undefined && t >= p.diedAt;
    var tt = dead ? p.diedAt : t;
    var i = indexAt(p.t, tt);
    if (i < 0) return null;
    var x = p.x[i], y = p.y[i];
    var heading = 0;
    if (i < p.t.length - 1 && !dead) {
      var k = (tt - p.t[i]) / (p.t[i + 1] - p.t[i]);
      heading = Math.atan2(p.y[i + 1] - y, p.x[i + 1] - x);
      x += (p.x[i + 1] - x) * k;
      y += (p.y[i + 1] - y) * k;
    } else if (i > 0) {
      heading = Math.atan2(y - p.y[i - 1], x - p.x[i - 1]);
    }
    // 탄 것: 지금 기록 기준. 다음 기록에서 차에 타면 그 사이의 절반이 지난 뒤부터 차량으로 (10초 간격이라 대략)
    var v = p.v ? p.v[i] : 0;
    if (!v && p.v && i < p.v.length - 1 && !dead) v = p.v[i + 1] && (tt - p.t[i]) > (p.t[i + 1] - p.t[i]) / 2 ? p.v[i + 1] : 0;
    return { x: x, y: y, dead: dead, knocked: !dead && isKnocked(p, t), vehicle: dead ? 0 : v, heading: heading };
  }

  function isKnocked(p, t) {
    var spans = knockedSpans[p.id];
    if (!spans) return false;
    for (var i = 0; i < spans.length; i++) if (t >= spans[i][0] && t < spans[i][1]) return true;
    return false;
  }

  /** t 초의 자기장: 지금 경계(파란 원)는 앞뒤 기록 사이를 이어서 줄어들고, 다음 원(흰 원)은 마지막 기록 */
  function zoneAt(t) {
    var z = R.zones;
    if (!z.length) return null;
    var i = indexAt(z.map(function (s) { return s.t; }), t);
    if (i < 0) return null;
    var a = z[i], b = z[Math.min(i + 1, z.length - 1)];
    var k = b.t > a.t ? Math.min(1, (t - a.t) / (b.t - a.t)) : 0;
    return {
      safeX: a.safeX + (b.safeX - a.safeX) * k, safeY: a.safeY + (b.safeY - a.safeY) * k,
      safeR: a.safeR + (b.safeR - a.safeR) * k,
      nextX: a.nextX, nextY: a.nextY, nextR: a.nextR
    };
  }

  function phaseAt(t) {
    var n = 0;
    R.phases.forEach(function (p) { if (p.t <= t) n = p.n; });
    return n;
  }

  // ---------- 화면 좌표 ----------
  function sx(x) { return (x - view.cx) * view.scale + size.w / 2; }
  function sy(y) { return (y - view.cy) * view.scale + size.h / 2; }

  function fitAll() {
    view.cx = R.map.size / 2;
    view.cy = R.map.size / 2;
    view.scale = Math.min(size.w || 600, size.h || 600) / R.map.size;
  }

  function resize() {
    var w = wrap.clientWidth;
    // 정사각형이 기본이지만, 화면보다 커지지 않게 창 높이의 70% 까지만 (최소 320px)
    var h = Math.round(Math.max(320, Math.min(w, window.innerHeight * 0.7)));
    var dpr = window.devicePixelRatio || 1;
    var fitBefore = R && size.w && Math.abs(view.scale - Math.min(size.w, size.h) / R.map.size) < 1e-9;
    size = { w: w, h: h, dpr: dpr };
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    canvas.style.height = h + 'px';
    if (R && (fitBefore || !view.scale)) fitAll();
    draw();
  }
  window.addEventListener('resize', function () { if (R) resize(); });

  function clampView() {
    var min = Math.min(size.w, size.h) / R.map.size;
    view.scale = Math.max(min, Math.min(view.scale, min * 24));
  }

  // ---------- 그리기 ----------
  function hatchPattern() {
    if (hatch) return hatch;
    var c = document.createElement('canvas');
    c.width = c.height = 10;
    var g = c.getContext('2d');
    g.strokeStyle = 'rgba(10,10,10,0.55)';
    g.lineWidth = 4;
    g.beginPath(); g.moveTo(-2, 12); g.lineTo(12, -2); g.stroke();
    g.beginPath(); g.moveTo(-2, 2); g.lineTo(2, -2); g.stroke();
    g.beginPath(); g.moveTo(8, 12); g.lineTo(12, 8); g.stroke();
    hatch = ctx.createPattern(c, 'repeat');
    return hatch;
  }

  /** 지도 조각 하나. 처음 부르면 불러오기 시작하고, 다 오기 전까지는 complete 가 false */
  function tile(z, x, y) {
    var key = z + '/' + x + '_' + y;
    var t = tiles[key];
    if (!t) {
      t = tiles[key] = new Image();
      t.decoding = 'async';
      t.src = R.map.tiles + '/' + key + '.webp';
    }
    return t;
  }

  function ready(t) { return t && t.complete && t.naturalWidth > 0; }

  /**
   * 지도: 화면 픽셀에 맞는 단계를 고르고, 보이는 조각만 그린다.
   * 아직 안 온 조각 자리는 이미 받아 둔 덜 자세한 단계가 아래에 깔려 있어서 비지 않는다.
   */
  function drawMap() {
    var s = R.map.size;
    var px = s * view.scale * size.dpr;   // 지도 한 변이 화면에서 몇 기기 픽셀인지
    var want = Math.max(0, Math.min(R.map.levels - 1, Math.ceil(Math.log2(px / TILE_PX))));
    // 화면에 보이는 지도 범위 (m)
    var x0 = view.cx - size.w / 2 / view.scale, x1 = view.cx + size.w / 2 / view.scale;
    var y0 = view.cy - size.h / 2 / view.scale, y1 = view.cy + size.h / 2 / view.scale;
    ctx.imageSmoothingQuality = 'high';
    for (var z = 0; z <= want; z++) {
      var n = 1 << z, m = s / n;           // 한 변 조각 수, 조각 한 변(m)
      var ix0 = Math.max(0, Math.floor(x0 / m)), ix1 = Math.min(n - 1, Math.floor(x1 / m));
      var iy0 = Math.max(0, Math.floor(y0 / m)), iy1 = Math.min(n - 1, Math.floor(y1 / m));
      for (var ix = ix0; ix <= ix1; ix++) {
        for (var iy = iy0; iy <= iy1; iy++) {
          var key = z + '/' + ix + '_' + iy;
          // 원하는 단계만 새로 부르고, 그 아래 단계는 이미 받아 둔 것만 깔개로 쓴다
          var t = z === want ? tile(z, ix, iy) : tiles[key];
          if (!ready(t)) continue;
          // 조각 사이 틈이 보이지 않게 0.5px 겹쳐 그림
          var dx = sx(ix * m), dy = sy(iy * m), d = m * view.scale;
          ctx.drawImage(t, dx - 0.25, dy - 0.25, d + 0.5, d + 0.5);
        }
      }
    }
  }

  function draw() {
    if (!R || !ready(tiles['0/0_0']) || !size.w) return;
    ctx.setTransform(size.dpr, 0, 0, size.dpr, 0, 0);
    ctx.fillStyle = colors.canvas;
    ctx.fillRect(0, 0, size.w, size.h);

    var s = R.map.size;
    drawMap();
    ctx.fillStyle = 'rgba(10,10,10,0.28)';   // 지도를 살짝 어둡게 해서 점이 잘 보이게
    ctx.fillRect(sx(0), sy(0), s * view.scale, s * view.scale);

    if (state.mode === 'play') drawPlay(); else drawPaths();
  }

  function drawZone(z, showSafe) {
    if (!z) return;
    if (showSafe && z.safeR > 0) {
      // 자기장 밖 빗금
      ctx.save();
      ctx.beginPath();
      ctx.rect(sx(0), sy(0), R.map.size * view.scale, R.map.size * view.scale);
      ctx.arc(sx(z.safeX), sy(z.safeY), z.safeR * view.scale, 0, Math.PI * 2, true);
      ctx.fillStyle = hatchPattern();
      ctx.fill('evenodd');
      ctx.restore();
      circle(z.safeX, z.safeY, z.safeR, colors.ink, 1.5, [4, 4], 0.6);
    }
    if (z.nextR > 0) circle(z.nextX, z.nextY, z.nextR, colors.ink, 2.5, null, 1);
  }

  function circle(x, y, r, color, width, dash, alpha) {
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    if (dash) ctx.setLineDash(dash);
    ctx.beginPath();
    ctx.arc(sx(x), sy(y), r * view.scale, 0, Math.PI * 2);
    ctx.stroke();
    ctx.restore();
  }

  function drawPlane() {
    if (!R.plane) return;
    ctx.save();
    ctx.strokeStyle = colors.ink;
    ctx.globalAlpha = 0.6;
    ctx.lineWidth = 2;
    ctx.setLineDash([2, 8]);
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(sx(R.plane.x1), sy(R.plane.y1));
    ctx.lineTo(sx(R.plane.x2), sy(R.plane.y2));
    ctx.stroke();
    ctx.restore();
  }

  /** 보급상자: 낙하산(반원 + 줄) 아래 끈 두른 상자 */
  function drawPackages(until) {
    R.events.forEach(function (e) {
      if (e.type !== 'package' || e.t > until) return;
      packageIcon(sx(e.x), sy(e.y));
    });
  }

  function packageIcon(x, y) {
    ctx.save();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = colors.ink;
    // 낙하산
    ctx.beginPath();
    ctx.arc(x, y - 9, 8, Math.PI, 0);
    ctx.closePath();
    ctx.fillStyle = 'rgba(255,255,255,0.25)';
    ctx.fill();
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(x - 8, y - 9); ctx.lineTo(x - 5, y - 2);
    ctx.moveTo(x + 8, y - 9); ctx.lineTo(x + 5, y - 2);
    ctx.stroke();
    // 상자
    ctx.fillStyle = colors.card;
    ctx.lineWidth = 2;
    ctx.fillRect(x - 6, y - 2, 12, 11);
    ctx.strokeRect(x - 6, y - 2, 12, 11);
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(x, y - 2); ctx.lineTo(x, y + 9);       // 끈
    ctx.moveTo(x - 6, y + 3.5); ctx.lineTo(x + 6, y + 3.5);
    ctx.stroke();
    ctx.restore();
  }

  /**
   * 차량·보트 아이콘 (위에서 본 모양, 가는 방향으로 회전). color = 탄 사람 색
   * 차량: 둥근 직사각형 + 앞유리, 보트: 앞이 뾰족한 배
   */
  function vehicleIcon(x, y, heading, kind, color, hollow) {
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
      roundRect(-11, -6.5, 22, 13, 4);
    }
    ctx.closePath();
    ctx.fillStyle = hollow ? colors.dark : color;
    ctx.fill();
    ctx.lineWidth = hollow ? 2.5 : 2;
    ctx.strokeStyle = hollow ? color : colors.dark;
    ctx.stroke();
    // 앞쪽 표시 (앞유리 / 뱃머리 선)
    ctx.beginPath();
    if (kind === 2) { ctx.moveTo(2, -4); ctx.lineTo(2, 4); }
    else { ctx.moveTo(4, -4.5); ctx.lineTo(4, 4.5); }
    ctx.lineWidth = 2;
    ctx.strokeStyle = hollow ? color : colors.dark;
    ctx.stroke();
    ctx.restore();
  }

  function roundRect(x, y, w, h, r) {
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y); ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r); ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h); ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r); ctx.quadraticCurveTo(x, y, x + r, y);
  }

  // ---------- 이펙트 (실제 시간 기준) ----------
  /*
   * 이펙트는 재생이 그 순간을 지나갈 때 시작해서, 배속과 상관없이 실제 시간으로 FX_MS 동안 보인다.
   * (경기 시간으로 재면 16배속에서 0.1초만 보여서 안 보임)
   * 위치는 지도 좌표로 들고 있어서 화면을 옮기거나 확대해도 제자리에 남는다.
   *  - 폭발물(blasts): 던진 곳 → 터진 곳 궤적 뒤에 폭발. 터진 곳은 맞은 적들 위치로 추정한 값
   *      수류탄·C4·접착 폭탄·박격포·판처 = 섬광 + 노란 충격파 + 파편 / 화염병 = 불꽃
   *      박격포는 높게 휘는 궤적, 판처는 곧은 로켓 연기. 잡은 한 발은 크게, 맞히기만 한 건 작게
   *  - 근접(events.cause = melee / meleeThrow / punch): 맞은 자리에 베는 자국. 던진 근접무기는 날아가는 것부터
   */
  var FX_MS = 1500;          // 터진 뒤 이펙트 길이
  var FLIGHT_MS = 380;       // 궤적(날아가는 동안) 길이. 박격포는 더 길게
  var fxSources = null;      // 시각 순 [{t, kind, ...}]
  var fx = [];               // 지금 보이는 이펙트 {src, at: 시작한 performance.now()}

  function buildFxSources() {
    fxSources = [];
    (R.blasts || []).forEach(function (b) {
      fxSources.push({ t: b.t, fx: 'blast', b: b });
    });
    R.events.forEach(function (e) {
      if (!e.ours || (e.type !== 'knock' && e.type !== 'kill')) return;
      if (e.cause !== 'melee' && e.cause !== 'meleeThrow' && e.cause !== 'punch') return;
      var actor = byName[e.actor];
      if (!actor || actor.team !== R.myTeam) return;   // 우리 팀이 한 것만
      fxSources.push({ t: e.t, fx: 'melee', e: e });
    });
    (R.heals || []).forEach(function (h) {
      fxSources.push({ t: h.t, fx: 'heal', h: h });
    });
    fxSources.sort(function (a, b) { return a.t - b.t; });
  }

  /** 재생이 from → to 로 지나간 사이에 있는 순간들의 이펙트를 시작 */
  function spawnEffects(from, to, now) {
    if (!fxSources) buildFxSources();
    if (to - from > 60) return;   // 크게 건너뛴 건 재생이 아니라 이동
    fxSources.forEach(function (src) {
      if (src.t > from && src.t <= to) fx.push({ src: src, at: now });
    });
  }

  function clearEffects() { fx = []; }

  /**
   * layer 'under' = 폭발물 (점 아래에 깔아서 사람 위치를 가리지 않게)
   * layer 'over'  = 근접·회복 (바로 옆에서 일어나서 우리 팀 점에 가려지므로 맨 위에)
   */
  function drawEffects(now, layer) {
    if (layer === 'under') fx = fx.filter(function (f) { return now - f.at < fxLength(f.src); });
    fx.forEach(function (f) {
      if ((f.src.fx === 'blast') !== (layer === 'under')) return;
      var ms = now - f.at;
      ctx.save();
      if (f.src.fx === 'blast') drawBlast(f.src.b, ms);
      else if (f.src.fx === 'melee') drawMelee(f.src.e, ms);
      else drawHeal(f.src.h, ms);
      ctx.restore();
    });
  }

  function flightMs(kind) { return kind === 'mortar' ? 650 : kind === 'panzer' ? 260 : FLIGHT_MS; }

  function fxLength(src) {
    if (src.fx === 'blast') return flightMs(src.b.kind) + (src.b.kind === 'molotov' ? FX_MS * 1.4 : FX_MS);
    if (src.fx === 'melee') return (src.e.cause === 'meleeThrow' ? FLIGHT_MS : 0) + FX_MS;
    return HEAL_MS;
  }

  /** 잡은 한 발은 크게, 기절은 조금 작게, 맞히기만 했으면 작게 */
  function blastSize(b) {
    var big = b.kind === 'c4' || b.kind === 'mortar' || b.kind === 'panzer' ? 1.25 : 1;
    return big * (b.result === 'kill' ? 1.15 : b.result === 'knock' ? 1 : 0.65);
  }

  function drawBlast(b, ms) {
    var fly = flightMs(b.kind);
    var x0 = sx(b.fromX), y0 = sy(b.fromY), x1 = sx(b.x), y1 = sy(b.y);
    var dist = Math.hypot(x1 - x0, y1 - y0);
    if (ms < fly) {
      var k = ms / fly;
      if (b.kind === 'panzer') {
        // 로켓: 곧게 날아가며 뒤에 연기
        var hx = x0 + (x1 - x0) * k, hy = y0 + (y1 - y0) * k;
        ctx.lineCap = 'round';
        ctx.strokeStyle = MAP_COLORS.ink;
        ctx.globalAlpha = 0.35; ctx.lineWidth = 6;
        ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(hx, hy); ctx.stroke();
        ctx.globalAlpha = 1; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.moveTo(x0 + (hx - x0) * 0.6, y0 + (hy - y0) * 0.6); ctx.lineTo(hx, hy); ctx.stroke();
        ctx.fillStyle = colors.primary;
        ctx.beginPath(); ctx.arc(hx, hy, 3.5, 0, Math.PI * 2); ctx.fill();
        return;
      }
      // 포물선: 화면에서 위로 휜다 (박격포는 높게)
      var lift = Math.min(160, dist * (b.kind === 'mortar' ? 0.9 : 0.35) + 12);
      var cx = (x0 + x1) / 2, cy = (y0 + y1) / 2 - lift;
      var px = (1 - k) * (1 - k) * x0 + 2 * (1 - k) * k * cx + k * k * x1;
      var py = (1 - k) * (1 - k) * y0 + 2 * (1 - k) * k * cy + k * k * y1;
      ctx.setLineDash([3, 4]);
      ctx.globalAlpha = 0.7;
      ctx.strokeStyle = MAP_COLORS.ink;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(x0, y0);
      for (var i = 1; i <= 16; i++) {
        var u = k * i / 16;
        ctx.lineTo((1 - u) * (1 - u) * x0 + 2 * (1 - u) * u * cx + u * u * x1,
                   (1 - u) * (1 - u) * y0 + 2 * (1 - u) * u * cy + u * u * y1);
      }
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.globalAlpha = 1;
      ctx.fillStyle = b.kind === 'molotov' ? colors.primary : MAP_COLORS.ink;
      ctx.strokeStyle = MAP_COLORS.dark;
      ctx.lineWidth = 2;
      ctx.beginPath(); ctx.arc(px, py, 4, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      return;
    }
    var size = blastSize(b);
    if (b.kind === 'molotov') { drawFire(x1, y1, (ms - fly) / (FX_MS * 1.4), size); return; }
    drawExplosion(x1, y1, (ms - fly) / FX_MS, size);
  }

  /** 폭발: 하얀 섬광 + 노란 충격파 고리 두 겹 + 파편. k = 0 → 1 */
  function drawExplosion(x, y, k, size) {
    var e = 1 - Math.pow(1 - k, 3);   // 처음에 빨리 퍼지고 천천히 멈춤
    // 섬광
    ctx.globalAlpha = Math.max(0, 1 - k * 2.2);
    ctx.fillStyle = MAP_COLORS.ink;
    ctx.beginPath(); ctx.arc(x, y, (10 + 16 * e) * size, 0, Math.PI * 2); ctx.fill();
    ctx.globalAlpha = Math.max(0, 0.55 - k);
    ctx.fillStyle = colors.primary;
    ctx.beginPath(); ctx.arc(x, y, (18 + 22 * e) * size, 0, Math.PI * 2); ctx.fill();
    // 충격파
    ctx.globalAlpha = Math.max(0, 1 - k);
    ctx.strokeStyle = colors.primary;
    ctx.lineWidth = 4 * size;
    ctx.beginPath(); ctx.arc(x, y, (12 + 44 * e) * size, 0, Math.PI * 2); ctx.stroke();
    ctx.globalAlpha = Math.max(0, 0.6 - k);
    ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(x, y, (8 + 30 * e) * size, 0, Math.PI * 2); ctx.stroke();
    // 파편
    ctx.globalAlpha = Math.max(0, 1 - k * 1.2);
    ctx.strokeStyle = MAP_COLORS.ink;
    ctx.lineCap = 'round';
    ctx.lineWidth = 2.5;
    for (var j = 0; j < 10; j++) {
      var a = j / 10 * Math.PI * 2 + 0.3;
      var r1 = (14 + 38 * e) * size, r2 = r1 + 9 * size;
      ctx.beginPath();
      ctx.moveTo(x + Math.cos(a) * r1, y + Math.sin(a) * r1);
      ctx.lineTo(x + Math.cos(a) * r2, y + Math.sin(a) * r2);
      ctx.stroke();
    }
  }

  /** 화염병: 불 번진 자리(흐린 원) 위에 불꽃이 일렁이다 꺼짐 */
  function drawFire(x, y, k, size) {
    var fade = k < 0.75 ? 1 : 1 - (k - 0.75) / 0.25;
    ctx.globalAlpha = 0.28 * fade;
    ctx.fillStyle = colors.primary;
    ctx.beginPath(); ctx.arc(x, y, 26 * size * Math.min(1, k * 5), 0, Math.PI * 2); ctx.fill();
    for (var i = 0; i < 11; i++) {
      var a = i / 11 * Math.PI * 2 + i;
      var r = (6 + 15 * ((i * 37) % 10) / 10) * size * Math.min(1, k * 5);
      var fx = x + Math.cos(a) * r, fy = y + Math.sin(a) * r - Math.abs(Math.sin(k * 14 + i)) * 5;
      var h = (5 + 4 * Math.abs(Math.sin(k * 11 + i * 1.3))) * size;
      ctx.globalAlpha = 0.95 * fade;
      ctx.fillStyle = i % 3 ? colors.primary : MAP_COLORS.ink;
      ctx.beginPath();   // 불꽃 모양 (아래 둥글고 위가 뾰족)
      ctx.moveTo(fx, fy - h * 1.6);
      ctx.quadraticCurveTo(fx + h, fy, fx, fy + h * 0.6);
      ctx.quadraticCurveTo(fx - h, fy, fx, fy - h * 1.6);
      ctx.fill();
    }
  }

  /** 근접: 맞은 자리에 베는 자국 세 줄 (주먹은 별 모양 충격). 던진 근접무기는 돌면서 날아가는 것부터 */
  function drawMelee(e, ms) {
    var x = sx(e.x), y = sy(e.y);
    if (e.cause === 'meleeThrow') {
      var from = posAt(byName[e.actor], e.t);
      if (ms < FLIGHT_MS) {
        if (!from) return;
        var k = ms / FLIGHT_MS, x0 = sx(from.x), y0 = sy(from.y);
        var px = x0 + (x - x0) * k, py = y0 + (y - y0) * k;
        ctx.translate(px, py);
        ctx.rotate(k * Math.PI * 6);
        ctx.fillStyle = MAP_COLORS.ink;
        ctx.strokeStyle = MAP_COLORS.dark;
        ctx.lineWidth = 2;
        ctx.beginPath(); ctx.arc(0, 0, 5, 0, Math.PI * 2); ctx.fill(); ctx.stroke();   // 프라이팬 머리
        ctx.fillRect(4, -1.5, 8, 3);                                                   // 손잡이
        return;
      }
      ms -= FLIGHT_MS;
    }
    var k2 = ms / FX_MS;
    var draw = Math.min(1, k2 * 5);           // 0.3초 동안 그어지고
    var fade = Math.max(0, 1 - Math.max(0, k2 - 0.4) / 0.6);
    ctx.globalAlpha = fade;
    ctx.lineCap = 'round';
    if (e.cause === 'punch') {
      ctx.strokeStyle = colors.primary;
      ctx.lineWidth = 3;
      for (var j = 0; j < 8; j++) {
        var a = j / 8 * Math.PI * 2;
        var r1 = 8, r2 = 8 + 14 * draw * (j % 2 ? 0.6 : 1);
        ctx.beginPath();
        ctx.moveTo(x + Math.cos(a) * r1, y + Math.sin(a) * r1);
        ctx.lineTo(x + Math.cos(a) * r2, y + Math.sin(a) * r2);
        ctx.stroke();
      }
      return;
    }
    for (var i = -1; i <= 1; i++) {
      var ox = i * 9, len = 40;
      var x0s = x - len / 2 + ox - 4, y0s = y - len / 2 + ox * 0.2 - 4;
      var x1s = x0s + len * draw, y1s = y0s + len * draw;
      ctx.strokeStyle = MAP_COLORS.dark;
      ctx.lineWidth = 6;
      ctx.beginPath(); ctx.moveTo(x0s, y0s); ctx.lineTo(x1s, y1s); ctx.stroke();
      ctx.strokeStyle = i === 0 ? colors.primary : MAP_COLORS.ink;
      ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(x0s, y0s); ctx.lineTo(x1s, y1s); ctx.stroke();
    }
  }

  /** 회복: 시안 선택 전 임시 (점 둘레 고리) */
  var HEAL_MS = 1200;
  function drawHeal(h, ms) {
    var m = byName[h.name];
    var p = m ? posAt(m, state.t) : null;   // 쓴 사람을 따라다님
    var x = sx(p ? p.x : h.x), y = sy(p ? p.y : h.y);
    var k = ms / HEAL_MS;
    ctx.globalAlpha = 1 - k;
    ctx.strokeStyle = MAP_COLORS.ink;
    ctx.lineWidth = 3;
    ctx.beginPath(); ctx.arc(x, y, 14 + 10 * k, 0, Math.PI * 2); ctx.stroke();
  }

  function trail(p, from, to, color, width, alpha) {
    var pts = [];
    for (var i = 0; i < p.t.length; i++) {
      if (p.t[i] < from) continue;
      if (p.t[i] > to) break;
      if (p.diedAt != null && p.t[i] > p.diedAt) break;
      pts.push([p.x[i], p.y[i]]);
    }
    var now = posAt(p, to);
    if (now) pts.push([now.x, now.y]);
    if (pts.length < 2) return;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineJoin = ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(sx(pts[0][0]), sy(pts[0][1]));
    for (var j = 1; j < pts.length; j++) ctx.lineTo(sx(pts[j][0]), sy(pts[j][1]));
    ctx.stroke();
    ctx.restore();
  }

  function cross(x, y, color, alpha, r) {
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.strokeStyle = color;
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(x - r, y - r); ctx.lineTo(x + r, y + r);
    ctx.moveTo(x + r, y - r); ctx.lineTo(x - r, y + r);
    ctx.stroke();
    ctx.restore();
  }

  /** 번호가 적힌 동그라미 (팀원, 교전) */
  function badge(x, y, r, fill, text, hollow) {
    ctx.save();
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    if (hollow) {
      ctx.fillStyle = colors.dark;
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
      ctx.font = '800 ' + Math.round(r * 1.15) + 'px Inter, Pretendard, sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(text, x, y + 0.5);
    }
    ctx.restore();
  }

  function drawPlay() {
    var t = state.t;
    drawZone(zoneAt(t), true);
    drawPlane();
    drawPackages(t);

    // 우리 팀 지나온 길
    team.forEach(function (m) {
      trail(m.track, t - TRAIL_SECONDS, t, m.me ? colors.primary : colors.ink, m.me ? 3 : 2, m.me ? 0.9 : 0.5);
    });

    // 최근 킬 ✕ (우리 팀 근처 것만)
    var teamPos = team.map(function (m) { return posAt(m.track, t); }).filter(function (p) { return p && !p.dead; });
    R.events.forEach(function (e) {
      if (e.type !== 'kill' || e.t > t || e.t < t - KILL_MARK_SECONDS) return;
      if (!e.ours && nearness(e.x, e.y, teamPos) === 0) return;
      var a = 1 - (t - e.t) / KILL_MARK_SECONDS;
      cross(sx(e.x), sy(e.y), e.ours ? colors.ink : colors.muted, a, 7);
    });

    // 근처 적: 팀마다 색, 팀마다 한 명 옆에 "N팀"
    var involved = involvedEnemies(t);
    var labeled = {};
    enemies.forEach(function (p) {
      var pos = posAt(p, t);
      if (!pos || pos.dead) return;
      // 1 = 범위 안 또는 우리와 싸우는 중, 0.35 = 범위를 조금 넘음(흐리게), 0 = 안 보여줌
      var alpha = involved[p.name] ? 1 : nearness(pos.x, pos.y, teamPos);
      if (alpha === 0) return;
      var color = teamColor(p.team);
      var x = sx(pos.x), y = sy(pos.y);
      ctx.save();
      ctx.globalAlpha = alpha;
      if (pos.vehicle) {
        vehicleIcon(x, y, pos.heading, pos.vehicle, color, false);
      } else {
        ctx.beginPath();
        ctx.arc(x, y, 7, 0, Math.PI * 2);
        if (pos.knocked) {
          ctx.fillStyle = colors.dark; ctx.fill();
          ctx.lineWidth = 3; ctx.strokeStyle = color; ctx.stroke();
        } else {
          ctx.fillStyle = color; ctx.fill();
          ctx.lineWidth = 2; ctx.strokeStyle = colors.dark; ctx.stroke();
        }
      }
      if (!labeled[p.team]) {
        labeled[p.team] = true;
        ctx.font = '700 12px Pretendard, Inter, sans-serif';
        ctx.textBaseline = 'middle';
        ctx.lineWidth = 3;
        ctx.strokeStyle = colors.dark;   // 글자 테두리: 지도 무늬 위에서도 읽히게
        ctx.strokeText(p.team + '팀', x + 11, y - 10);
        ctx.fillStyle = color;
        ctx.fillText(p.team + '팀', x + 11, y - 10);
      }
      ctx.restore();
    });

    drawHits(t);
    drawEffects(performance.now(), 'under');

    // 우리 팀 (나를 맨 위에)
    team.slice().reverse().forEach(function (m) {
      var pos = posAt(m.track, t);
      if (!pos) return;
      var x = sx(pos.x), y = sy(pos.y);
      if (pos.dead) { cross(x, y, colors.muted, 0.9, 6); return; }
      var color = m.me ? colors.primary : colors.ink;
      if (m.me && !pos.knocked) {
        ctx.save(); ctx.globalAlpha = 0.25; ctx.fillStyle = colors.primary;
        ctx.beginPath(); ctx.arc(x, y, 16, 0, Math.PI * 2); ctx.fill(); ctx.restore();
      }
      if (pos.vehicle) {
        vehicleIcon(x, y, pos.heading, pos.vehicle, color, false);
        badge(x + 12, y - 11, 7, color, String(m.n), false);
      } else {
        badge(x, y, 9.5, color, String(m.n), pos.knocked);
      }
    });
    drawEffects(performance.now(), 'over');

    updatePanels(t);
  }

  /** 우리 팀과 얼마나 가까운지: 1 = 범위 안, 0.35 = 범위의 1.3배 안, 0 = 멀다. 전체 보기면 늘 1 */
  function nearness(x, y, teamPos) {
    if (nearMeters === Infinity) return 1;
    var best = Infinity;
    for (var i = 0; i < teamPos.length; i++) {
      best = Math.min(best, Math.hypot(teamPos[i].x - x, teamPos[i].y - y));
    }
    if (best <= nearMeters) return 1;
    if (best <= nearMeters * FAR_FADE) return 0.35;
    return 0;
  }

  /**
   * 지금 우리 팀과 싸우는 중이거나 곧 싸울 적.
   * 우리 팀 기절·킬·명중에 이름이 나온 적을, 그 적과 처음 주고받기 30초 전부터 마지막 10초 뒤까지 보여준다.
   * (위치 기록이 10초 간격이라 300m 기준만 쓰면 이미 쏘고 있을 때 나타나서)
   */
  var enemyWindows = null;   // 닉네임 → [처음 주고받은 시각, 마지막 시각]
  function involvedEnemies(t) {
    if (!enemyWindows) {
      enemyWindows = {};
      var add = function (name, when) {
        if (!name || !byName[name] || byName[name].team === R.myTeam) return;
        var w = enemyWindows[name] || (enemyWindows[name] = [when, when]);
        w[0] = Math.min(w[0], when);
        w[1] = Math.max(w[1], when);
      };
      R.events.forEach(function (e) {
        if (!e.ours) return;
        add(e.actor, e.t);
        add(e.target, e.t);
      });
      (R.hits || []).forEach(function (h) {
        add(h.attacker, h.t);
        add(h.victim, h.t);
      });
    }
    var names = {};
    Object.keys(enemyWindows).forEach(function (name) {
      var w = enemyWindows[name];
      if (t >= w[0] - FIGHT_LEAD_SECONDS && t <= w[1] + FIGHT_TAIL_SECONDS) names[name] = true;
    });
    return names;
  }

  /**
   * 명중선: 맞힌 순간부터 HIT_SECONDS 동안 쏜 사람 → 맞은 사람.
   *  - 우리 팀이 맞힘: 노란 실선 (나는 굵게) + 맞은 적에 노란 고리
   *  - 적이 우리를 맞힘: 흰 점선
   * 선 끝은 지금 화면의 점 위치에 맞춘다 (점과 선이 어긋나지 않게). 사람을 못 찾으면 맞힌 순간 기록 위치
   */
  function drawHits(t) {
    var me = root.getAttribute('data-me');
    (R.hits || []).forEach(function (h) {
      if (h.t > t || t - h.t > HIT_SECONDS) return;
      var a = byName[h.attacker] ? posAt(byName[h.attacker], t) : null;
      var v = byName[h.victim] ? posAt(byName[h.victim], t) : null;
      var ax = sx(a ? a.x : h.ax), ay = sy(a ? a.y : h.ay);
      var vx = sx(v ? v.x : h.vx), vy = sy(v ? v.y : h.vy);
      var alpha = 1 - (t - h.t) / HIT_SECONDS;
      ctx.save();
      ctx.globalAlpha = Math.max(0.15, alpha);
      ctx.lineCap = 'round';
      if (h.ours) {
        var mine = byName[h.attacker] && byName[h.attacker].id === me;
        ctx.strokeStyle = colors.primary;
        ctx.lineWidth = mine ? 3 : 2;
      } else {
        ctx.strokeStyle = colors.ink;
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
  function phaseRange() {
    if (!state.phase) return [0, R.duration];
    var p = R.phases[state.phase - 1];
    var next = R.phases[state.phase];
    return [p.t, next ? next.t : R.duration];
  }

  function drawPaths() {
    var range = phaseRange();
    // 그 페이즈의 다음 안전구역(흰 원). 전체면 마지막 원
    var z = zoneAt(state.phase ? Math.min(range[1] - 1, R.duration) : R.duration);
    drawZone(z, false);
    drawPlane();

    var who = state.who === 'me' ? team.filter(function (m) { return m.me; }) : team;
    who.forEach(function (m) {
      trail(m.track, range[0], range[1], m.me ? colors.primary : colors.ink, m.me ? 3.5 : 2.5, m.me ? 1 : 0.65);
      // 착지 지점
      var landing = R.events.find(function (e) { return e.type === 'landing' && e.actor === m.track.name; });
      if (landing && landing.t >= range[0] && landing.t <= range[1]) {
        ctx.save();
        ctx.strokeStyle = m.me ? colors.primary : colors.ink;
        ctx.lineWidth = 2.5;
        ctx.beginPath(); ctx.arc(sx(landing.x), sy(landing.y), 7, 0, Math.PI * 2); ctx.stroke();
        ctx.restore();
      }
      // 끝난 곳
      var end = posAt(m.track, range[1]);
      if (end) {
        if (end.dead) cross(sx(end.x), sy(end.y), m.me ? colors.primary : colors.ink, 1, 6);
        else badge(sx(end.x), sy(end.y), 8, m.me ? colors.primary : colors.ink, String(m.n), false);
      }
    });

    // 교전 번호
    R.fights.forEach(function (f) {
      if (f.end < range[0] || f.start > range[1]) return;
      badge(sx(f.x), sy(f.y), 12, colors.ink, String(f.n), false);
    });
    updatePanels(range[1]);
  }

  // ---------- 오른쪽 패널 ----------
  function updatePanels(t) {
    var alive = 0;
    var html = team.map(function (m) {
      var pos = posAt(m.track, t);
      var status = !pos ? '비행기' : pos.dead ? '사망' : pos.knocked ? '기절' : '생존';
      if (pos && !pos.dead) alive++;
      var kills = R.events.filter(function (e) { return e.type === 'kill' && e.t <= t && e.actor === m.track.name; }).length;
      return '<li class="replay__member' + (m.me ? ' replay__member--me' : '') + (pos && pos.dead ? ' is-dead' : '') + '">' +
          '<span class="replay__num">' + m.n + '</span>' +
          '<span class="replay__name">' + escapeHtml(m.track.name) + '</span>' +
          '<span class="replay__state">' + status + ' · ' + kills + '킬</span></li>';
    }).join('');
    if (teamEl.innerHTML !== html) teamEl.innerHTML = html;
    aliveEl.textContent = alive + '명 생존';

    // 경기 흐름: 지금 시각까지 마지막 사건에 표시
    if (state.mode === 'play') {
      var current = -1;
      feedItems.forEach(function (it, i) { if (it.t <= t && !it.el.hidden) current = i; });
      if (current !== state.lastFeed) {
        feedItems.forEach(function (it, i) { it.el.classList.toggle('is-current', i === current); });
        if (current >= 0 && state.playing) {
          var el = feedItems[current].el;
          var box = feedEl;
          if (el.offsetTop < box.scrollTop || el.offsetTop > box.scrollTop + box.clientHeight - el.offsetHeight) {
            box.scrollTop = el.offsetTop - box.clientHeight / 3;
          }
        }
        state.lastFeed = current;
      }
    }
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  var TYPE_LABEL = { landing: '착지', knock: '기절', kill: '킬', revive: '부활', ride: '차량', leave: '하차', phase: '자기장' };
  var TYPE_GROUP = { landing: 'move', ride: 'move', leave: 'move', knock: 'fight', kill: 'fight', revive: 'fight', phase: 'move' };

  function buildFeed() {
    var items = R.events.filter(function (e) { return e.ours; }).map(function (e) {
      // 우리 팀이 한 기절·킬은 노랑 배지
      var mine = (e.type === 'kill' || e.type === 'knock') && byName[e.actor] && byName[e.actor].team === R.myTeam;
      return { t: e.t, type: e.type, text: e.text, strong: mine };
    });
    R.phases.forEach(function (p) {
      items.push({ t: p.t, type: 'phase', text: p.n + '페이즈 시작', strong: false });
    });
    items.sort(function (a, b) { return a.t - b.t; });
    renderFeed(items);
  }

  function renderFeed(items) {
    feedEl.innerHTML = '';
    feedItems = items.map(function (it) {
      var li = document.createElement('li');
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'replay__event';
      btn.innerHTML = '<time>' + clock(it.t) + '</time>' +
          '<span class="replay__badge' + (it.strong ? ' replay__badge--strong' : '') + '">' + (TYPE_LABEL[it.type] || '') + '</span>' +
          '<span>' + escapeHtml(it.text) + '</span>';
      btn.addEventListener('click', function () { seek(Math.max(0, it.t - 5)); state.follow = true; paintFollow(); });
      li.appendChild(btn);
      li.dataset.group = TYPE_GROUP[it.type] || 'move';
      feedEl.appendChild(li);
      return { t: it.t, el: li };
    });
    applyFeedFilter();
  }

  function applyFeedFilter() {
    feedItems.forEach(function (it) {
      it.el.hidden = state.feedFilter !== 'all' && it.el.dataset.group !== state.feedFilter;
    });
    state.lastFeed = -2;
  }

  /** 동선 전체 보기: 오른쪽에 교전 목록 */
  function renderFights() {
    feedEl.innerHTML = '';
    feedItems = [];
    if (!R.fights.length) {
      feedEl.innerHTML = '<li class="replay__empty">우리 팀이 싸운 기록이 없어요.</li>';
      return;
    }
    R.fights.forEach(function (f) {
      var li = document.createElement('li');
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'replay__fight';
      var result = [];
      if (f.kills) result.push(f.kills + '킬');
      if (f.knocks) result.push('기절시킴 ' + f.knocks);
      if (f.knocked) result.push('기절당함 ' + f.knocked);
      if (f.deaths) result.push('사망 ' + f.deaths);
      btn.innerHTML = '<span class="replay__fight-n">' + f.n + '</span>' +
          '<span><b>' + clock(f.start) + ' · ' + phaseText(f.start) + '</b>' +
          '<span class="replay__fight-sub">' + (result.join(' · ') || '피해만 오감') + ' · ' + Math.max(1, f.end - f.start) + '초</span></span>';
      // 누르면 재생 보기로 바꿔서 그 교전 5초 전부터
      btn.addEventListener('click', function () {
        setMode('play');
        seek(Math.max(0, f.start - 5));
        state.follow = true; paintFollow();
      });
      li.appendChild(btn);
      feedEl.appendChild(li);
    });
  }

  function phaseText(t) {
    var n = phaseAt(t);
    return n ? n + '페이즈' : '자기장 전';
  }

  // ---------- 타임라인 ----------
  function buildTimeline() {
    timeline.setAttribute('aria-valuemax', R.duration);
    var html = '';
    R.phases.forEach(function (p) {
      html += '<span class="replay__phase" style="left:' + pct(p.t) + '%"><i>' + p.n + '</i></span>';
    });
    R.events.forEach(function (e) {
      if (!e.ours || (e.type !== 'kill' && e.type !== 'knock')) return;
      var mine = byName[e.actor] && byName[e.actor].team === R.myTeam;
      html += '<span class="replay__mark' + (mine ? ' replay__mark--ours' : '') + '" style="left:' + pct(e.t) + '%"></span>';
    });
    marksEl.innerHTML = html;
  }

  function buildPhaseTabs() {
    var el = q('[data-phase]');
    var html = '<button type="button" class="tab is-active" data-value="0" aria-pressed="true">전체</button>';
    R.phases.forEach(function (p) {
      html += '<button type="button" class="tab" data-value="' + p.n + '" aria-pressed="false">' + p.n + '</button>';
    });
    el.innerHTML = html;
  }

  function pct(t) { return R.duration ? (t / R.duration * 100).toFixed(2) : 0; }

  function clock(t) {
    t = Math.max(0, Math.floor(t));
    return String(Math.floor(t / 60)).padStart(2, '0') + ':' + String(t % 60).padStart(2, '0');
  }

  function paintTime() {
    var p = pct(state.t);
    doneEl.style.width = p + '%';
    knobEl.style.left = p + '%';
    timeline.setAttribute('aria-valuenow', Math.floor(state.t));
    timeline.setAttribute('aria-valuetext', clock(state.t) + ', ' + phaseText(state.t));
    timeEl.textContent = clock(state.t) + ' / ' + clock(R.duration);
  }

  function seek(t) {
    state.t = Math.max(0, Math.min(R.duration, t));
    state.lastFeed = -2;
    clearEffects();
    paintTime();
    draw();
  }

  // ---------- 재생 ----------
  var lastFrame = 0;
  function loop() {
    var now = performance.now();   // 이펙트와 같은 시계 (rAF 시각은 브라우저마다 기준이 조금 달라서)
    var dt = lastFrame ? Math.min(0.25, (now - lastFrame) / 1000) : 0;   // 탭을 오래 비웠다 와도 크게 안 건너뜀
    lastFrame = now;
    if (state.playing && state.mode === 'play') {
      var before = state.t;
      state.t += dt * state.speed;
      if (state.t >= R.duration) { state.t = R.duration; setPlaying(false); }
      spawnEffects(before, state.t, now);
      paintTime();
    }
    if (state.follow && state.mode === 'play') followTeam(dt);
    draw();
    requestAnimationFrame(loop);
  }

  /** 따라가기: 내가 살아 있으면 나, 아니면 살아 있는 팀원 가운데로 부드럽게 */
  function followTeam(dt) {
    var targets = team.map(function (m) { return { m: m, p: posAt(m.track, state.t) }; })
        .filter(function (o) { return o.p && !o.p.dead; });
    var mine = targets.filter(function (o) { return o.m.me; });
    var use = mine.length ? mine : targets;
    if (!use.length) return;
    var x = 0, y = 0;
    use.forEach(function (o) { x += o.p.x; y += o.p.y; });
    x /= use.length; y /= use.length;
    var want = Math.min(size.w, size.h) / FOLLOW_WIDTH;
    var k = dt ? Math.min(1, dt * 4) : 1;
    view.cx += (x - view.cx) * k;
    view.cy += (y - view.cy) * k;
    view.scale += (want - view.scale) * k;
  }

  function setPlaying(on) {
    state.playing = on;
    playBtn.classList.toggle('is-playing', on);
    playBtn.setAttribute('aria-label', on ? '일시 정지' : '재생');
  }

  playBtn.addEventListener('click', function () {
    if (!R) return;
    if (!state.playing && state.t >= R.duration) state.t = firstMoment();
    setPlaying(!state.playing);
  });

  // 타임라인 누르기·끌기
  function seekFromPointer(e) {
    var rect = timeline.getBoundingClientRect();
    var k = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    seek(k * R.duration);
  }
  timeline.addEventListener('pointerdown', function (e) {
    if (!R) return;
    timeline.setPointerCapture(e.pointerId);
    seekFromPointer(e);
    var move = function (ev) { seekFromPointer(ev); };
    var up = function () {
      timeline.removeEventListener('pointermove', move);
      timeline.removeEventListener('pointerup', up);
    };
    timeline.addEventListener('pointermove', move);
    timeline.addEventListener('pointerup', up);
  });
  timeline.addEventListener('keydown', function (e) {
    if (!R) return;
    if (e.key === 'ArrowRight') { seek(state.t + 10); e.preventDefault(); }
    if (e.key === 'ArrowLeft') { seek(state.t - 10); e.preventDefault(); }
  });
  document.addEventListener('keydown', function (e) {
    if (!R || e.target.closest('input, textarea')) return;
    if (e.code === 'Space' && !e.target.closest('button, [role="slider"]')) {
      e.preventDefault();
      playBtn.click();
    }
  });

  // 탭 묶음 공통: 누른 것만 켜기
  function tabs(el, onChange) {
    el.addEventListener('click', function (e) {
      var btn = e.target.closest('button[data-value]');
      if (!btn) return;
      el.querySelectorAll('button').forEach(function (b) {
        var on = b === btn;
        b.classList.toggle('is-active', on);
        b.setAttribute('aria-pressed', on ? 'true' : 'false');
      });
      onChange(btn.getAttribute('data-value'));
    });
  }
  function selectTab(el, value) {
    el.querySelectorAll('button').forEach(function (b) {
      var on = b.getAttribute('data-value') === value;
      b.classList.toggle('is-active', on);
      b.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
  }

  tabs(q('[data-speed]'), function (v) { state.speed = Number(v); });
  tabs(feedFilterEl, function (v) { state.feedFilter = v; applyFeedFilter(); });
  tabs(q('[data-phase]'), function (v) { state.phase = Number(v); draw(); });
  tabs(q('[data-who]'), function (v) { state.who = v; draw(); });

  // 적 표시 범위
  var rangeLabel = q('[data-range-label]');
  tabs(q('[data-range]'), function (v) {
    nearMeters = v === 'all' ? Infinity : Number(v);
    if (rangeLabel) {
      rangeLabel.textContent = v === 'all' ? '모든 적' : (nearMeters >= 1000 ? nearMeters / 1000 + 'km' : nearMeters + 'm') + ' 안';
    }
    draw();
  });
  tabs(q('[data-mode]'), function (v) { setMode(v); });

  function setMode(mode) {
    state.mode = mode;
    selectTab(q('[data-mode]'), mode);
    var play = mode === 'play';
    playControls.hidden = !play;
    pathControls.hidden = play;
    feedFilterEl.hidden = !play;
    if (!R) return;
    if (play) {
      flowTitle.textContent = '경기 흐름';
      flowHint.textContent = '누르면 그 순간으로 넘어가요.';
      buildFeed();
    } else {
      setPlaying(false);
      flowTitle.textContent = '교전 ' + R.fights.length + '번';
      flowHint.textContent = '누르면 그 교전 5초 전부터 재생해요.';
      renderFights();
      // 우리 팀 동선이 다 들어오게 맞춘다
      fitTeam();
    }
    draw();
  }

  function fitTeam() {
    var xs = [], ys = [];
    team.forEach(function (m) {
      m.track.x.forEach(function (x, i) {
        if (m.track.diedAt != null && m.track.t[i] > m.track.diedAt) return;
        xs.push(x); ys.push(m.track.y[i]);
      });
    });
    if (!xs.length) { fitAll(); return; }
    var minX = Math.min.apply(null, xs), maxX = Math.max.apply(null, xs);
    var minY = Math.min.apply(null, ys), maxY = Math.max.apply(null, ys);
    view.cx = (minX + maxX) / 2;
    view.cy = (minY + maxY) / 2;
    view.scale = Math.min(size.w, size.h) / (Math.max(maxX - minX, maxY - minY, 600) * 1.25);
    clampView();
    state.follow = false;
    paintFollow();
  }

  // ---------- 확대·이동 ----------
  function paintFollow() {
    followBtn.setAttribute('aria-pressed', state.follow ? 'true' : 'false');
    followBtn.classList.toggle('is-on', state.follow);
  }
  paintFollow();

  followBtn.addEventListener('click', function () {
    state.follow = !state.follow;
    if (state.follow && state.mode !== 'play') setMode('play');
    paintFollow();
  });

  root.querySelectorAll('[data-zoom]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (!R) return;
      var z = btn.getAttribute('data-zoom');
      state.follow = false; paintFollow();
      if (z === 'fit') fitAll();
      else { view.scale *= z === 'in' ? 1.5 : 1 / 1.5; clampView(); }
      draw();
    });
  });

  canvas.addEventListener('wheel', function (e) {
    if (!R) return;
    e.preventDefault();
    state.follow = false; paintFollow();
    var rect = canvas.getBoundingClientRect();
    var mx = e.clientX - rect.left, my = e.clientY - rect.top;
    var wx = (mx - size.w / 2) / view.scale + view.cx;
    var wy = (my - size.h / 2) / view.scale + view.cy;
    view.scale *= e.deltaY < 0 ? 1.2 : 1 / 1.2;
    clampView();
    // 마우스가 가리키던 곳이 그대로 있게
    view.cx = wx - (mx - size.w / 2) / view.scale;
    view.cy = wy - (my - size.h / 2) / view.scale;
    draw();
  }, { passive: false });

  var drag = null;
  canvas.addEventListener('pointerdown', function (e) {
    if (!R) return;
    drag = { x: e.clientX, y: e.clientY, cx: view.cx, cy: view.cy };
    canvas.setPointerCapture(e.pointerId);
  });
  canvas.addEventListener('pointermove', function (e) {
    if (!drag) return;
    var dx = e.clientX - drag.x, dy = e.clientY - drag.y;
    if (Math.abs(dx) + Math.abs(dy) > 3 && state.follow) { state.follow = false; paintFollow(); }
    view.cx = drag.cx - dx / view.scale;
    view.cy = drag.cy - dy / view.scale;
    draw();
  });
  canvas.addEventListener('pointerup', function () { drag = null; });
  canvas.addEventListener('pointercancel', function () { drag = null; });

  // 지도 영역 크기가 바뀌면 (창 크기, 폰 회전) 다시 맞춘다
  if (window.ResizeObserver) new ResizeObserver(function () { if (R) resize(); }).observe(wrap);
  else resize();
})();
