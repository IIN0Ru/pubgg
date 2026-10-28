/*
 * 플레이어 카드 (/card). 카드 전체를 캔버스 하나에 그린다 → 화면에 보이는 그림 = PNG = GIF.
 *
 *  - 기록 값: <canvas data-card='{ShareCard JSON}'>
 *  - 사용자가 고르는 것(마이크·지향·구함·역할, 옷·옷 색)은 주소 뒤에 붙인다
 *    (?mic=o&aim=chicken,rank&tee=f0f0e8&outer=hoodie&oc=3a64c8&bottom=training&bc=2a2a2e&suit=ghillie)
 *    → 공유 링크를 받은 사람도 같은 카드를 본다. DB 없음
 *  - 배경: 에란겔·미라마·태이고·론도 중 랜덤 맵(글자 없는 공식 맵)의 랜덤 구역. 도로·건물이 적당히 보이고 바다가 적은 곳을 고른다.
 *    처음 열 때, "배경 다른 구역", 저장할 때마다 새로 고른다
 *  - 캐릭터: 총·옷 조합별 애니메이션 시트(static/images/card/anim/<총>/<외투>-<하의>.png, 전신 옷은 suit-<이름>).
 *    반팔·외투·하의는 열쇠색으로 그려져 있고, anim/keys.json(열쇠색 → 밝기 비율)대로 고른 색을 입힌다
 *
 * 색: 카드 안은 사이트 테마와 상관없이 고정 (맵 사진·티어 색·옷 색이라). CLAUDE.md 예외 참고.
 *     티어 색은 static/images/card/frames/palette.json (테두리 그림과 같은 값)
 */
import { GIFEncoder, quantize, applyPalette } from './vendor/gifenc.esm.js';

const BASE = '/images/card/';
const W = 360, H = 480;          // 카드 크기 (논리 단위). 화면은 2배, PNG 는 3배로 그린다
const INNER = 12;                // 도트 테두리 두께 (3칸 × 4)
const TEE_DEFAULT = 'f0f0e8';
const OUTERS = ['none', 'hoodie', 'jacket', 'shirt'];
const BOTTOMS = ['shorts', 'training', 'slacks'];
const SUITS = ['ghillie', 'bear'];

// 주무기 → 그릴 총. weapons.json 에 없으면 분류로
const CLASS_GUN = { AR_556: 'M416', AR_762: 'M416', DMR: 'Mini14', SR: 'Kar98k', SMG: 'MP5K', SHOTGUN: 'S1897', LMG: 'M249', OTHER: 'M416' };

// 성향 태그: 주소 값 → 글자 (순서 = 카드에 붙는 순서)
const PICKS = {
  mic: { o: '마이크 O', x: '마이크 X', listen: '듣기만' },
  aim: { chicken: '치킨지향', rank: '순위지향', social: '친목지향' },
  find: { duo: '듀오 구함', squad: '스쿼드 구함', clan: '클랜원 모집' },
  role: { igl: 'IGL', front: '1선', second: '2선', turret: '포탑' },
};

const canvas = document.querySelector('[data-player-card]');
const form = document.querySelector('[data-card-form]');
const status = document.querySelector('[data-card-status]');
const card = JSON.parse(canvas.dataset.card);

const loadImage = (src) => new Promise((ok, fail) => {
  const im = new Image();
  im.onload = () => ok(im);
  im.onerror = () => fail(new Error('그림을 못 불러옴: ' + src));
  im.src = src;
});
const loadJson = (src) => fetch(src).then((r) => r.json());

// 배경 맵: 고를 때마다 넷 중 하나. 한 번 받은 맵은 다시 받지 않는다
const MAPS = ['erangel', 'miramar', 'taego', 'rondo'];
const mapCache = {};
const loadMap = (key) => (mapCache[key] = mapCache[key] || loadImage(BASE + 'maps/' + key + '.webp'));
const randomMap = () => loadMap(MAPS[Math.floor(Math.random() * MAPS.length)]);

// ---------- 주소 ↔ 고른 값 ----------

const hexOr = (v, fallback) => (/^[0-9a-f]{6}$/.test((v || '').toLowerCase()) ? v.toLowerCase() : fallback);
const oneOf = (v, list, fallback) => (list.includes(v) ? v : fallback);

function readChoices() {
  const q = new URLSearchParams(location.search);
  const list = (k) => (q.get(k) || '').split(',').filter((v) => PICKS[k][v]);
  return {
    mic: PICKS.mic[q.get('mic')] ? q.get('mic') : '',
    aim: list('aim'), find: list('find'), role: list('role'),
    tee: hexOr(q.get('tee'), TEE_DEFAULT),
    suit: oneOf(q.get('suit'), SUITS, ''),
    outer: oneOf(q.get('outer'), OUTERS, 'none'),
    oc: hexOr(q.get('oc'), ''),          // 빈 값 = 옷 원래 색
    bottom: oneOf(q.get('bottom'), BOTTOMS, 'shorts'),
    bc: hexOr(q.get('bc'), ''),
  };
}

function writeChoices(c) {
  const q = new URLSearchParams(location.search);
  const set = (k, v) => (v ? q.set(k, v) : q.delete(k));
  set('mic', c.mic);
  set('aim', c.aim.join(','));
  set('find', c.find.join(','));
  set('role', c.role.join(','));
  set('tee', c.tee === TEE_DEFAULT ? '' : c.tee);
  set('suit', c.suit);
  set('outer', c.outer === 'none' ? '' : c.outer);
  set('oc', c.oc);
  set('bottom', c.bottom === 'shorts' ? '' : c.bottom);
  set('bc', c.bc);
  history.replaceState(null, '', location.pathname + '?' + q.toString());
}

function fillForm(c) {
  form.querySelectorAll('input').forEach((el) => {
    const v = c[el.name];
    el.checked = Array.isArray(v) ? v.includes(el.value) : v === el.value;
  });
}

function formChoices() {
  const one = (n) => (form.querySelector(`input[name="${n}"]:checked`) || {}).value || '';
  // 체크박스는 화면 순서(= PICKS 순서)대로
  const many = (n) => [...form.querySelectorAll(`input[name="${n}"]:checked`)].map((el) => el.value);
  return {
    mic: one('mic'), aim: many('aim'), find: many('find'), role: many('role'),
    tee: one('tee') || TEE_DEFAULT, suit: one('suit'),
    outer: one('outer') || 'none', oc: one('oc'), bottom: one('bottom') || 'shorts', bc: one('bc'),
  };
}

/** 전신 옷이면 반팔·외투·하의 칸을 잠그고, 색을 못 바꾸는 외투(없음·셔츠)면 외투 색 칸을 잠근다 */
function syncWear(c) {
  form.querySelectorAll('[data-wear]').forEach((fs) => {
    const w = fs.dataset.wear;
    fs.disabled = !!c.suit || (w === 'oc' && !A.keys.outer[c.outer]);
  });
}

/** 성향 줄: 마이크 → 주 활동 시간(기록) → 지향 → 구함 → 역할 */
function styleTags(c) {
  const tags = [];
  if (c.mic) tags.push(PICKS.mic[c.mic]);
  if (card.peakHours) tags.push(card.peakHours);
  c.aim.forEach((v) => tags.push(PICKS.aim[v]));
  c.find.forEach((v) => tags.push(PICKS.find[v]));
  c.role.forEach((v) => tags.push(PICKS.role[v]));
  return tags;
}

// ---------- 자료 ----------

const hexRgb = (h) => [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
const rgba = (c, a) => `rgba(${c[0]},${c[1]},${c[2]},${a})`;

/** 고른 옷의 시트 (총·조합별로 처음 쓸 때만 받는다) */
const sheetCache = {};
function comboOf(c) {
  return c.suit ? 'suit-' + c.suit : c.outer + '-' + c.bottom;
}
function loadSheet(c) {
  const src = BASE + 'anim/' + A.gun + '/' + comboOf(c) + '.png';
  return (sheetCache[src] = sheetCache[src] || loadImage(src));
}

/**
 * 열쇠색을 고른 색으로 바꾼 시트.
 * keys.json: 부위마다 [r, g, b, 밝기 비율] — 그 열쇠색 자리는 고른 색 × 비율 (소매 그림자까지 들어 있음)
 */
function recolorSheet(sheet, c) {
  const k = A.keys;
  const parts = [];
  if (!c.suit) {
    parts.push([k.tee, c.tee]);
    if (k.outer[c.outer]) parts.push([k.outer[c.outer], c.oc || k.default.outer[c.outer]]);
    parts.push([k.bottom[c.bottom], c.bc || k.default.bottom[c.bottom]]);
  }
  const table = new Map();
  for (const [list, hex] of parts) {
    const base = hexRgb(hex);
    for (const [r, g, b, ratio] of list) {
      table.set((r << 16) | (g << 8) | b, base.map((v) => Math.min(255, Math.floor(v * ratio))));
    }
  }
  const out = document.createElement('canvas');
  out.width = sheet.width; out.height = sheet.height;
  const x = out.getContext('2d');
  x.drawImage(sheet, 0, 0);
  if (!table.size) return out;
  const img = x.getImageData(0, 0, out.width, out.height);
  const d = img.data;
  for (let i = 0; i < d.length; i += 4) {
    if (d[i + 3] === 0) continue;
    const to = table.get((d[i] << 16) | (d[i + 1] << 8) | d[i + 2]);
    if (to) { d[i] = to[0]; d[i + 1] = to[1]; d[i + 2] = to[2]; }
  }
  x.putImageData(img, 0, 0);
  return out;
}

/**
 * 맵에서 카드 배경 구역 고르기.
 * 카드 안쪽(336×456)을 2px 도트로 → 168×228칸. 원본(2048px 맵)에서는 336×456px 구역 (1칸 = 2px ≈ 8m)
 */
const CELLS_W = (W - INNER * 2) / 2, CELLS_H = (H - INNER * 2) / 2;
function pickMapArea(map) {
  const c = document.createElement('canvas');
  c.width = CELLS_W; c.height = CELLS_H;
  const x = c.getContext('2d', { willReadFrequently: true });
  const sw = CELLS_W * 2, sh = CELLS_H * 2;
  // 맵 전체에서 아무 곳이나 뽑되, 바다가 35% 넘거나 너무 어둡거나 아무것도 없는(도로·건물 1% 미만) 곳만 다시 뽑는다.
  // (전에는 "도로·건물 8%" 에 가장 가까운 곳을 골라서 마을 근처만 반복해 나왔음)
  let best = null;
  for (let t = 0; t < 40 && !best; t++) {
    const sx = Math.floor(Math.random() * (map.width - sw)), sy = Math.floor(Math.random() * (map.height - sh));
    x.drawImage(map, sx, sy, sw, sh, 0, 0, CELLS_W, CELLS_H);
    const d = x.getImageData(0, 0, CELLS_W, CELLS_H).data;
    let water = 0, bright = 0, sum = 0;
    const n = d.length / 4;
    for (let i = 0; i < d.length; i += 4) {
      const r = d[i], g = d[i + 1], b = d[i + 2], l = 0.3 * r + 0.59 * g + 0.11 * b;
      if (b > r + 12 && b >= g - 5) water++;
      if (l > 150) bright++;
      sum += l;
    }
    if (water / n <= 0.35 && sum / n >= 35 && bright / n >= 0.01) best = { sx, sy };
  }
  if (!best) best = { sx: (map.width - sw) / 2, sy: (map.height - sh) / 2 };
  x.drawImage(map, best.sx, best.sy, sw, sh, 0, 0, CELLS_W, CELLS_H);
  // 색 수를 줄여 도트 느낌 (채널마다 16단계)
  const img = x.getImageData(0, 0, CELLS_W, CELLS_H);
  for (let i = 0; i < img.data.length; i += 4) {
    for (let k = 0; k < 3; k++) img.data[i + k] = Math.round(img.data[i + k] / 16) * 16;
  }
  x.putImageData(img, 0, 0);
  return c;
}

// ---------- 그리기 ----------

let A = null;              // 불러온 자료
let state = null;          // { choices, sheet(색 바꾼 것), area(배경), tags }
let frame = 0;

function roundRect(x, l, t, w, h, r) {
  x.beginPath();
  x.moveTo(l + r, t);
  x.arcTo(l + w, t, l + w, t + h, r);
  x.arcTo(l + w, t + h, l, t + h, r);
  x.arcTo(l, t + h, l, t, r);
  x.arcTo(l, t, l + w, t, r);
  x.closePath();
}

const FONT = '"Pretendard", "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif';
const YELLOW = '#FFD02F';   // 사이트 강조색 (--primary)

/** 태그 한 줄 묶음을 놓을 자리 계산: [{text, x, y, w}] , 높이 */
function layoutChips(x, tags, left, right) {
  x.font = `500 11px ${FONT}`;
  const out = [];
  let cx = left, cy = 0;
  for (const t of tags) {
    const w = Math.ceil(x.measureText('#' + t).width) + 18;
    if (cx + w > right && cx > left) { cx = left; cy += 27; }
    out.push({ text: '#' + t, x: cx, y: cy, w });
    cx += w + 5;
  }
  return { chips: out, height: tags.length ? cy + 22 : 0 };
}

/** 카드 한 장. i = 캐릭터 프레임 번호, ms = 지금까지 흐른 시간(반짝임용), scale = 픽셀 배율 */
function draw(x, i, ms, scale) {
  const pal = A.palette[card.tierFamily] || A.palette.Unranked;
  const light = hexRgb(pal[2].slice(1));
  x.setTransform(scale, 0, 0, scale, 0, 0);
  x.clearRect(0, 0, W, H);
  x.imageSmoothingEnabled = false;

  // 배경 맵 + 위아래 어둡게
  x.fillStyle = '#0b0b0c';
  x.fillRect(INNER, INNER, W - INNER * 2, H - INNER * 2);
  x.drawImage(state.area, INNER, INNER, W - INNER * 2, H - INNER * 2);
  const g = x.createLinearGradient(0, INNER, 0, H - INNER);
  g.addColorStop(0, 'rgba(10,10,12,.78)');
  g.addColorStop(0.35, 'rgba(10,10,12,.35)');
  g.addColorStop(0.7, 'rgba(10,10,12,.55)');
  g.addColorStop(1, 'rgba(10,10,12,.92)');
  x.fillStyle = g;
  x.fillRect(INNER, INNER, W - INNER * 2, H - INNER * 2);

  // 머리: 티어 아이콘 | 클랜 / 닉네임 / 티어
  const icon = A.tierIcon;
  const iw = 60, ih = Math.round(icon.height * iw / icon.width);
  const bob = Math.round(Math.sin(ms / 380) * 1.5);
  x.drawImage(icon, 26, 30 + Math.max(0, (52 - ih) / 2) + bob, iw, ih);
  let ty = 38;
  x.textBaseline = 'alphabetic';
  if (card.clan) {
    x.font = `700 11px ${FONT}`; x.fillStyle = YELLOW;
    x.fillText(card.clan, 96, ty); ty += 24;
  } else {
    ty += 12;
  }
  x.font = `800 22px ${FONT}`; x.fillStyle = '#fff';
  x.shadowColor = 'rgba(0,0,0,.8)'; x.shadowBlur = 6; x.shadowOffsetY = 2;
  x.fillText(card.nickname, 96, ty);
  x.shadowBlur = 0; x.shadowOffsetY = 0;
  x.font = `800 10px ${FONT}`; x.fillStyle = rgba(light, 1);
  if ('letterSpacing' in x) x.letterSpacing = '1.5px';
  x.fillText(card.tierText, 96, ty + 16);
  if ('letterSpacing' in x) x.letterSpacing = '0px';

  // 가운데 오른쪽: KDA / 평균 딜 / 주무기
  const bx = 220, bw = 112;
  const box = (top, h) => {
    roundRect(x, bx, top, bw, h, 8);
    x.fillStyle = 'rgba(8,8,10,.72)'; x.fill();
    x.strokeStyle = 'rgba(255,255,255,.12)'; x.lineWidth = 1; x.stroke();
  };
  const label = (t, top) => { x.font = `500 9px ${FONT}`; x.fillStyle = '#a6a6ad'; x.fillText(t, bx + 10, top + 15); };
  box(118, 50); label('KDA', 118);
  x.font = `800 19px ${FONT}`; x.fillStyle = '#fff'; x.fillText(card.kda, bx + 10, 118 + 39);
  box(175, 50); label('평균 딜', 175);
  x.font = `800 19px ${FONT}`; x.fillStyle = '#fff'; x.fillText(String(card.avgDamage), bx + 10, 175 + 39);
  box(232, 78); label('주무기', 232);
  const gun = A.gunIcon;
  const gs = Math.max(1, Math.floor(Math.min(24 / gun.height, (bw - 20) / gun.width)));
  x.drawImage(gun, bx + 10, 232 + 22 + Math.round((24 - gun.height * gs) / 2), gun.width * gs, gun.height * gs);
  x.font = `800 13px ${FONT}`; x.fillStyle = '#fff';
  x.fillText(card.weaponName || '-', bx + 10, 232 + 66);

  // 가운데 왼쪽: 캐릭터 (64×56 → 3배) + 발밑 그림자
  const sh = x.createRadialGradient(70, 292, 2, 70, 292, 46);
  sh.addColorStop(0, 'rgba(0,0,0,.7)'); sh.addColorStop(1, 'rgba(0,0,0,0)');
  x.fillStyle = sh;
  x.save(); x.translate(0, 292); x.scale(1, 0.12); x.translate(0, -292);
  x.fillRect(20, 240, 100, 104); x.restore();
  const m = A.meta;
  x.drawImage(state.sheet, i * m.w, 0, m.w, m.h, 14, 128, m.w * 3, m.h * 3);

  // 아래: 전투 / 성향 태그
  const left = 82, right = W - 28;
  const combat = layoutChips(x, card.combatTags, left, right);
  const style = layoutChips(x, state.tags, left, right);
  const bottom = 432;
  const styleTop = bottom - style.height;
  const combatTop = (style.height ? styleTop - 10 : bottom) - combat.height;
  const row = (name, rowLay, top) => {
    if (!rowLay.chips.length) return;
    x.font = `700 9px ${FONT}`; x.fillStyle = YELLOW;
    x.fillText(name, 28, top + 14);
    for (const c of rowLay.chips) {
      roundRect(x, c.x, top + c.y, c.w, 22, 11);
      x.fillStyle = 'rgba(8,8,10,.7)'; x.fill();
      x.strokeStyle = 'rgba(255,255,255,.12)'; x.lineWidth = 1; x.stroke();
      x.font = `500 11px ${FONT}`; x.fillStyle = '#e2e2e2';
      x.fillText(c.text, c.x + 9, top + c.y + 15);
    }
  };
  row('전투', combat, combatTop);
  row('성향', style, styleTop);

  x.font = `500 9px ${FONT}`; x.fillStyle = '#8a8a92';
  x.fillText(`최근 경쟁전 ${card.matches}경기 기준`, 34, 450);
  x.textAlign = 'right'; x.fillText('pub.gg', W - 34, 450); x.textAlign = 'left';

  // 도트 테두리 + 귀퉁이 반짝임
  x.drawImage(A.frame, 0, 0, W, H);
  const on = Math.floor(ms / 900) % 2;
  x.fillStyle = '#fff';
  x.fillRect(on ? 16 : W - 20, on ? 16 : H - 20, 4, 4);
}

// ---------- 화면 재생 ----------

const ctx = canvas.getContext('2d');
let started = 0, timer = null;
function play() {
  clearTimeout(timer);
  const m = A.meta;
  const tick = () => {
    draw(ctx, frame, performance.now() - started, canvas.width / W);
    const d = m.ms[frame];
    frame = (frame + 1) % m.ms.length;
    timer = setTimeout(tick, d);
  };
  tick();
}

const looksKey = (c) => [c.tee, c.suit, c.outer, c.oc, c.bottom, c.bc].join('|');

async function update(reroll) {
  const c = formChoices();
  writeChoices(c);
  syncWear(c);
  const [map, sheet] = await Promise.all([reroll || !state ? randomMap() : null, loadSheet(c)]);
  const sameLook = state && looksKey(state.choices) === looksKey(c);
  state = {
    choices: c,
    sheet: sameLook ? state.sheet : recolorSheet(sheet, c),
    area: map ? pickMapArea(map) : state.area,
    tags: styleTags(c),
  };
}

// ---------- 저장 ----------

const fileBase = () => (card.nickname + '_pubgg').replace(/[\\/:*?"<>|]/g, '_');

function download(blob, name) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

async function savePng() {
  await update(true);
  const c = document.createElement('canvas');
  c.width = W * 3; c.height = H * 3;
  // 총을 들고 있는 장면 (애니메이션 가운데쯤)
  draw(c.getContext('2d'), Math.floor(A.meta.ms.length * 0.45), 0, 3);
  c.toBlob((b) => { download(b, fileBase() + '.png'); status.textContent = 'PNG 를 저장했어요.'; });
  play();
}

async function saveGif() {
  await update(true);
  status.textContent = 'GIF 만드는 중…';
  await new Promise((r) => setTimeout(r, 30));
  const scale = 2;
  const c = document.createElement('canvas');
  c.width = W * scale; c.height = H * scale;
  const x = c.getContext('2d', { willReadFrequently: true });
  const m = A.meta;
  const gif = GIFEncoder();
  // 색표는 첫 장면으로 255색 + 투명 1칸. 두 번째 장면부터는 앞 장면과 같은 점을 투명으로 → 바뀐 곳(캐릭터·반짝임)만 담겨서 파일이 작아진다
  const TRANSPARENT = 255;
  let palette = null, prev = null, t = 0;
  for (let i = 0; i < m.ms.length; i++) {
    draw(x, i, t, scale);
    const data = x.getImageData(0, 0, c.width, c.height).data;
    if (!palette) palette = quantize(data, 255).concat([[0, 0, 0]]);
    const index = applyPalette(data, palette.slice(0, 255));
    const out = index.slice();
    if (prev) {
      for (let k = 0; k < out.length; k++) if (index[k] === prev[k]) out[k] = TRANSPARENT;
    }
    gif.writeFrame(out, c.width, c.height, {
      palette, delay: m.ms[i],
      transparent: !!prev, transparentIndex: TRANSPARENT, dispose: 1,
    });
    prev = index;
    t += m.ms[i];
    if (i % 8 === 0) await new Promise((r) => setTimeout(r, 0));   // 화면이 멈추지 않게
  }
  gif.finish();
  download(new Blob([gif.bytes()], { type: 'image/gif' }), fileBase() + '.gif');
  status.textContent = 'GIF 를 저장했어요.';
  play();
}

// ---------- 시작 ----------

async function start() {
  const weapons = await loadJson(BASE + 'anim/weapons.json');
  const gunKey = weapons[card.weaponName] || CLASS_GUN[card.weaponClass] || 'M416';
  const [metaAll, palette, keys, frameImg, tierIcon, gunIcon] = await Promise.all([
    loadJson(BASE + 'anim/meta.json'),
    loadJson(BASE + 'frames/palette.json'),
    loadJson(BASE + 'anim/keys.json'),
    loadImage(BASE + 'frames/' + card.tierFamily + '.png'),
    loadImage(BASE + 'tiers/' + card.tierIcon + '.png'),
    loadImage(BASE + 'guns/' + gunKey + '.png'),
    document.fonts ? document.fonts.ready : null,
  ]);
  A = { gun: gunKey, meta: metaAll[gunKey], palette, keys, frame: frameImg, tierIcon, gunIcon };
  canvas.style.setProperty('--tier-glow', (palette[card.tierFamily] || palette.Unranked)[1]);
  fillForm(readChoices());
  await update(false);
  started = performance.now();
  play();

  form.addEventListener('change', () => update(false));
  document.querySelector('[data-card-reroll]').addEventListener('click', () => update(true));   // 화면은 재생 중이라 다음 장면부터 바뀐 배경
  document.querySelector('[data-card-png]').addEventListener('click', () => {
    savePng().catch((e) => { status.textContent = 'PNG 를 만들지 못했어요.'; console.error(e); });
  });
  document.querySelector('[data-card-gif]').addEventListener('click', () => {
    saveGif().catch((e) => { status.textContent = 'GIF 를 만들지 못했어요.'; console.error(e); });
  });
}

start().catch((e) => {
  status.textContent = '카드를 그리지 못했어요. 새로고침해 주세요.';
  console.error(e);
});
