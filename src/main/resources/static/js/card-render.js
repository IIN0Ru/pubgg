/*
 * 플레이어 카드 그리기 (공용). 카드 꾸미기 화면(player-card.js)과 전적 화면의 작은 프로필 카드(card-profile.js)가 같이 쓴다.
 *
 *  - 기록 값(card): 서버의 ShareCard JSON (닉네임·클랜·티어·KDA·딜·주무기·전투 태그·주 활동 시간)
 *  - 꾸민 값(choices): 마이크·지향·구함·역할, 옷·옷 색 (주소 값과 같은 이름)
 *  - 배경(bg): { map, sx, sy } 글자 없는 맵(2048px) 위 구역의 왼쪽 위 좌표
 *  - 캐릭터: 총·옷 조합별 애니메이션 시트(static/images/card/anim/<총>/<외투>-<하의>.png, 전신 옷은 suit-<이름>).
 *    반팔·외투·하의는 열쇠색으로 그려져 있고, anim/keys.json(열쇠색 → 밝기 비율)대로 고른 색을 입힌다
 *
 * 색: 카드 안은 사이트 테마와 상관없이 고정 (맵 사진·티어 색·옷 색이라). CLAUDE.md 예외 참고.
 *     티어 색은 static/images/card/frames/palette.json (테두리 그림과 같은 값)
 */
export const BASE = '/images/card/';
export const W = 360, H = 480;   // 카드 크기 (논리 단위)
const INNER = 12;                // 도트 테두리 두께 (3칸 × 4)

export const TEE_DEFAULT = 'f0f0e8';
export const OUTERS = ['none', 'hoodie', 'jacket', 'shirt'];
export const BOTTOMS = ['shorts', 'training', 'slacks'];
export const SUITS = ['ghillie'];
export const MAPS = ['erangel', 'miramar', 'taego', 'rondo'];

// 주무기 → 그릴 총. weapons.json 에 없으면 분류로
const CLASS_GUN = { AR_556: 'M416', AR_762: 'M416', DMR: 'Mini14', SR: 'Kar98k', SMG: 'MP5K', SHOTGUN: 'S1897', LMG: 'M249', OTHER: 'M416' };

// 성향 태그: 값 → 글자 (순서 = 카드에 붙는 순서)
export const PICKS = {
  mic: { o: '마이크 O', x: '마이크 X', listen: '듣기만' },
  aim: { chicken: '치킨지향', rank: '순위지향', social: '친목지향' },
  find: { duo: '듀오 구함', squad: '스쿼드 구함', clan: '클랜원 모집' },
  role: { igl: 'IGL', front: '1선', second: '2선', turret: '포탑' },
};

export const loadImage = (src) => new Promise((ok, fail) => {
  const im = new Image();
  im.onload = () => ok(im);
  im.onerror = () => fail(new Error('그림을 못 불러옴: ' + src));
  im.src = src;
});
const loadJson = (src) => fetch(src).then((r) => r.json());

const hexOr = (v, fallback) => (/^[0-9a-f]{6}$/.test((v || '').toLowerCase()) ? v.toLowerCase() : fallback);
const oneOf = (v, list, fallback) => (list.includes(v) ? v : fallback);

/** 주소 값이나 저장한 값(아무 객체) → 검사한 꾸민 값 */
export function normalizeChoices(get) {
  const list = (k) => (get(k) || '').split(',').filter((v) => PICKS[k][v]);
  return {
    mic: PICKS.mic[get('mic')] ? get('mic') : '',
    aim: list('aim'), find: list('find'), role: list('role'),
    tee: hexOr(get('tee'), TEE_DEFAULT),
    suit: oneOf(get('suit'), SUITS, ''),
    outer: oneOf(get('outer'), OUTERS, 'none'),
    oc: hexOr(get('oc'), ''),          // 빈 값 = 옷 원래 색
    bottom: oneOf(get('bottom'), BOTTOMS, 'shorts'),
    bc: hexOr(get('bc'), ''),
  };
}

/** 꾸민 값 → 저장·주소용 (기본값은 뺌) */
export function choicesToParams(c) {
  const out = {};
  const set = (k, v) => { if (v) out[k] = v; };
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
  return out;
}

/** 성향 줄: 마이크 → 주 활동 시간(기록) → 지향 → 구함 → 역할 */
export function styleTags(card, c) {
  const tags = [];
  if (c.mic) tags.push(PICKS.mic[c.mic]);
  if (card.peakHours) tags.push(card.peakHours);
  c.aim.forEach((v) => tags.push(PICKS.aim[v]));
  c.find.forEach((v) => tags.push(PICKS.find[v]));
  c.role.forEach((v) => tags.push(PICKS.role[v]));
  return tags;
}

const hexRgb = (h) => [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];

/** 카드 하나에 필요한 그림·표를 받는다 */
export async function loadAssets(card) {
  const weapons = await loadJson(BASE + 'anim/weapons.json');
  const gun = weapons[card.weaponName] || CLASS_GUN[card.weaponClass] || 'M416';
  const [metaAll, palette, keys, frame, tierIcon, gunIcon] = await Promise.all([
    loadJson(BASE + 'anim/meta.json'),
    loadJson(BASE + 'frames/palette.json'),
    loadJson(BASE + 'anim/keys.json'),
    loadImage(BASE + 'frames/' + card.tierFamily + '.png'),
    loadImage(BASE + 'tiers/' + card.tierIcon + '.png'),
    loadImage(BASE + 'guns/' + gun + '.png'),
    document.fonts ? document.fonts.ready : null,
  ]);
  return { gun, meta: metaAll[gun], palette, keys, frame, tierIcon, gunIcon, sheets: {}, maps: {} };
}

/** 티어 색 (빛 번짐용 바탕색) */
export const tierGlow = (A, card) => (A.palette[card.tierFamily] || A.palette.Unranked)[1];

// ---------- 캐릭터 ----------

const comboOf = (c) => (c.suit ? 'suit-' + c.suit : c.outer + '-' + c.bottom);

/** 고른 옷의 시트에 고른 색을 입힌 것 (조합별 시트는 처음 쓸 때만 받는다) */
export async function makeSheet(A, c) {
  const src = BASE + 'anim/' + A.gun + '/' + comboOf(c) + '.png';
  const sheet = await (A.sheets[src] = A.sheets[src] || loadImage(src));
  const k = A.keys;
  const parts = [];
  if (!c.suit) {
    parts.push([k.tee, c.tee]);
    if (k.outer[c.outer]) parts.push([k.outer[c.outer], c.oc || k.default.outer[c.outer]]);
    parts.push([k.bottom[c.bottom], c.bc || k.default.bottom[c.bottom]]);
  }
  // 열쇠색 → 고른 색 × 밝기 비율 (소매 그림자까지 표에 들어 있음)
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

// ---------- 배경 ----------

export const loadMap = (A, key) => (A.maps[key] = A.maps[key] || loadImage(BASE + 'maps/' + key + '.webp'));

/**
 * 카드 안쪽(336×456)을 2px 도트로 → 168×228칸. 원본(2048px 맵)에서는 336×456px 구역 (1칸 = 2px ≈ 8m)
 */
const CELLS_W = (W - INNER * 2) / 2, CELLS_H = (H - INNER * 2) / 2;
const SW = CELLS_W * 2, SH = CELLS_H * 2;

/** 정해진 구역의 배경 그림 (색 수를 줄여 도트 느낌) */
export function areaAt(map, sx, sy) {
  const c = document.createElement('canvas');
  c.width = CELLS_W; c.height = CELLS_H;
  const x = c.getContext('2d', { willReadFrequently: true });
  x.drawImage(map, sx, sy, SW, SH, 0, 0, CELLS_W, CELLS_H);
  const img = x.getImageData(0, 0, CELLS_W, CELLS_H);
  for (let i = 0; i < img.data.length; i += 4) {
    for (let k = 0; k < 3; k++) img.data[i + k] = Math.round(img.data[i + k] / 16) * 16;   // 채널마다 16단계
  }
  x.putImageData(img, 0, 0);
  return c;
}

/**
 * 랜덤 맵의 랜덤 구역 → { map, sx, sy }.
 * 맵 전체에서 아무 곳이나 뽑되, 바다가 35% 넘거나 너무 어둡거나 아무것도 없는(도로·건물 1% 미만) 곳만 다시 뽑는다.
 */
export async function randomBackground(A) {
  const key = MAPS[Math.floor(Math.random() * MAPS.length)];
  const map = await loadMap(A, key);
  const c = document.createElement('canvas');
  c.width = CELLS_W; c.height = CELLS_H;
  const x = c.getContext('2d', { willReadFrequently: true });
  for (let t = 0; t < 40; t++) {
    const sx = Math.floor(Math.random() * (map.width - SW)), sy = Math.floor(Math.random() * (map.height - SH));
    x.drawImage(map, sx, sy, SW, SH, 0, 0, CELLS_W, CELLS_H);
    const d = x.getImageData(0, 0, CELLS_W, CELLS_H).data;
    let water = 0, bright = 0, sum = 0;
    const n = d.length / 4;
    for (let i = 0; i < d.length; i += 4) {
      const r = d[i], g = d[i + 1], b = d[i + 2], l = 0.3 * r + 0.59 * g + 0.11 * b;
      if (b > r + 12 && b >= g - 5) water++;
      if (l > 150) bright++;
      sum += l;
    }
    if (water / n <= 0.35 && sum / n >= 35 && bright / n >= 0.01) return { map: key, sx, sy };
  }
  return { map: key, sx: Math.round((map.width - SW) / 2), sy: Math.round((map.height - SH) / 2) };
}

/** 배경 값 → 그림 */
export async function backgroundImage(A, bg) {
  return areaAt(await loadMap(A, bg.map), bg.sx, bg.sy);
}

// ---------- 카드 그리기 ----------

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

/**
 * 카드 한 장.
 * @param look  { sheet(색 입힌 캐릭터 시트), area(배경 그림), tags(성향 태그) }
 * @param i     캐릭터 장면 번호
 * @param ms    지금까지 흐른 시간 (귀퉁이 반짝임용)
 * @param scale 픽셀 배율
 */
export function draw(x, A, card, look, i, ms, scale) {
  const pal = A.palette[card.tierFamily] || A.palette.Unranked;
  x.setTransform(scale, 0, 0, scale, 0, 0);
  x.clearRect(0, 0, W, H);
  x.imageSmoothingEnabled = false;

  // 배경 맵 + 위아래 어둡게
  x.fillStyle = '#0b0b0c';
  x.fillRect(INNER, INNER, W - INNER * 2, H - INNER * 2);
  x.drawImage(look.area, INNER, INNER, W - INNER * 2, H - INNER * 2);
  const g = x.createLinearGradient(0, INNER, 0, H - INNER);
  g.addColorStop(0, 'rgba(10,10,12,.78)');
  g.addColorStop(0.35, 'rgba(10,10,12,.35)');
  g.addColorStop(0.7, 'rgba(10,10,12,.55)');
  g.addColorStop(1, 'rgba(10,10,12,.92)');
  x.fillStyle = g;
  x.fillRect(INNER, INNER, W - INNER * 2, H - INNER * 2);

  // 머리: 티어 아이콘(가만히) | 클랜 / 닉네임 / 티어
  const icon = A.tierIcon;
  const iw = 60, ih = Math.round(icon.height * iw / icon.width);
  x.drawImage(icon, 26, 30 + Math.max(0, (52 - ih) / 2), iw, ih);
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
  x.font = `800 10px ${FONT}`; x.fillStyle = pal[2];
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
  x.drawImage(look.sheet, i * m.w, 0, m.w, m.h, 14, 128, m.w * 3, m.h * 3);

  // 아래: 전투 / 성향 태그
  const left = 82, right = W - 28;
  const combat = layoutChips(x, card.combatTags, left, right);
  const style = layoutChips(x, look.tags, left, right);
  const bottom = 432;
  const styleTop = bottom - style.height;
  const combatTop = (style.height ? styleTop - 10 : bottom) - combat.height;
  const row = (name, lay, top) => {
    if (!lay.chips.length) return;
    x.font = `700 9px ${FONT}`; x.fillStyle = YELLOW;
    x.fillText(name, 28, top + 14);
    for (const c of lay.chips) {
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

/**
 * 캔버스에서 카드를 계속 재생한다. getLook() 이 돌려준 모습을 매 장면 그린다.
 * @return 멈추는 함수
 */
export function play(canvas, A, card, getLook) {
  const ctx = canvas.getContext('2d');
  const started = performance.now();
  let frame = 0, timer = null;
  const tick = () => {
    const look = getLook();
    if (look) draw(ctx, A, card, look, frame, performance.now() - started, canvas.width / W);
    const d = A.meta.ms[frame];
    frame = (frame + 1) % A.meta.ms.length;
    timer = setTimeout(tick, d);
  };
  tick();
  return () => clearTimeout(timer);
}
