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
  var NEAR_METERS = 300;     // 우리 팀 누구와든 이 거리 안에 있는 적만 보여준다
  var EVENT_NEAR_SECONDS = 8; // 우리 팀 사건에 얽힌 적은 거리와 상관없이 이 시간 동안 보여준다
  var TRAIL_SECONDS = 90;    // 재생 중 보여주는 지나온 길 길이
  var KILL_MARK_SECONDS = 12; // ✕ 표시가 남아 있는 시간
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
  var img = new Image();
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
        img.onload = function () {
          statusEl.hidden = true;
          fitAll();
          resize();
          paintTime();   // 처음 시각(착지 직전)을 시간 표시·타임라인에 반영
          requestAnimationFrame(loop);
        };
        img.onerror = function () { statusEl.textContent = '지도 이미지를 불러오지 못했어요.'; };
        img.src = R.map.image;
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
    if (i < p.t.length - 1 && !dead) {
      var k = (tt - p.t[i]) / (p.t[i + 1] - p.t[i]);
      x += (p.x[i + 1] - x) * k;
      y += (p.y[i + 1] - y) * k;
    }
    return { x: x, y: y, dead: dead, knocked: !dead && isKnocked(p, t) };
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

  function draw() {
    if (!R || !img.complete || !size.w) return;
    ctx.setTransform(size.dpr, 0, 0, size.dpr, 0, 0);
    ctx.fillStyle = colors.canvas;
    ctx.fillRect(0, 0, size.w, size.h);

    var s = R.map.size;
    ctx.drawImage(img, sx(0), sy(0), s * view.scale, s * view.scale);
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

  function drawPackages(until) {
    R.events.forEach(function (e) {
      if (e.type !== 'package' || e.t > until) return;
      var x = sx(e.x), y = sy(e.y);
      ctx.save();
      ctx.fillStyle = colors.card;
      ctx.strokeStyle = colors.ink;
      ctx.lineWidth = 2;
      ctx.fillRect(x - 6, y - 6, 12, 12);
      ctx.strokeRect(x - 6, y - 6, 12, 12);
      ctx.restore();
    });
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
      if (!e.ours && !isNear(e.x, e.y, teamPos)) return;
      var a = 1 - (t - e.t) / KILL_MARK_SECONDS;
      cross(sx(e.x), sy(e.y), e.ours ? colors.ink : colors.muted, a, 7);
    });

    // 근처 적
    var involved = involvedEnemies(t);
    enemies.forEach(function (p) {
      var pos = posAt(p, t);
      if (!pos || pos.dead) return;
      if (!involved[p.name] && !isNear(pos.x, pos.y, teamPos)) return;
      ctx.save();
      ctx.beginPath();
      ctx.arc(sx(pos.x), sy(pos.y), 6, 0, Math.PI * 2);
      if (pos.knocked) {
        ctx.lineWidth = 2.5; ctx.strokeStyle = colors.muted; ctx.stroke();
      } else {
        ctx.fillStyle = colors.muted; ctx.fill();
        ctx.lineWidth = 2; ctx.strokeStyle = '#0A0A0A'; ctx.stroke();
      }
      ctx.restore();
    });

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
      badge(x, y, 9.5, color, String(m.n), pos.knocked);
    });

    updatePanels(t);
  }

  function isNear(x, y, teamPos) {
    for (var i = 0; i < teamPos.length; i++) {
      if (Math.hypot(teamPos[i].x - x, teamPos[i].y - y) <= NEAR_METERS) return true;
    }
    return false;
  }

  /** 지금 우리 팀과 싸우는 중인 적 (최근·곧 있을 우리 팀 사건에 이름이 나온 적) */
  function involvedEnemies(t) {
    var names = {};
    R.events.forEach(function (e) {
      if (!e.ours || Math.abs(e.t - t) > EVENT_NEAR_SECONDS) return;
      if (e.actor) names[e.actor] = true;
      if (e.target) names[e.target] = true;
    });
    return names;
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
    paintTime();
    draw();
  }

  // ---------- 재생 ----------
  var lastFrame = 0;
  function loop(now) {
    var dt = lastFrame ? (now - lastFrame) / 1000 : 0;
    lastFrame = now;
    if (state.playing && state.mode === 'play') {
      state.t += dt * state.speed;
      if (state.t >= R.duration) { state.t = R.duration; setPlaying(false); }
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
