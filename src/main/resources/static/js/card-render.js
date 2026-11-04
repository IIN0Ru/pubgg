/*
 * 플레이어 카드 그리기 (공용). 카드 꾸미기 화면(player-card.js)과 전적 화면의 작은 프로필 카드(card-profile.js)가 같이 쓴다.
 *
 *  - 기록 값(card): 서버의 ShareCard JSON (닉네임·클랜·티어·KDA·딜·주무기/보조무기·전투 태그·주 활동 시간)
 *  - 꾸민 값(choices): 마이크·지향·구함·역할 (주소 값과 같은 이름)
 *  - 배경(bg): { map, sx, sy } 글자 없는 맵(2048px) 위 구역의 왼쪽 위 좌표
 *  - 무기: static/images/card/weapons/<itemId>.png (공식 api-assets 그림을 수평으로 돌려 자른 것, 총구가 왼쪽)
 *
 * 색: 카드 안은 사이트 테마와 상관없이 고정 (맵 사진·티어 색이라). CLAUDE.md 예외 참고.
 *     티어 색은 static/images/card/frames/palette.json (테두리 그림과 같은 값)
 */
export const BASE = '/images/card/';
export const W = 360, H = 480;   // 카드 크기 (논리 단위)
const INNER = 12;                // 도트 테두리 두께 (3칸 × 4)

export const MAPS = ['erangel', 'miramar', 'taego', 'rondo'];

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

/** 주소 값이나 저장한 값(아무 객체) → 검사한 꾸민 값. 예전 카드에 저장된 옷 값은 모르는 이름이라 무시된다 */
export function normalizeChoices(get) {
  const list = (k) => (get(k) || '').split(',').filter((v) => PICKS[k][v]);
  return {
    mic: PICKS.mic[get('mic')] ? get('mic') : '',
    aim: list('aim'), find: list('find'), role: list('role'),
  };
}

/** 꾸민 값 → 저장·주소용 (안 고른 건 뺌) */
export function choicesToParams(c) {
  const out = {};
  const set = (k, v) => { if (v) out[k] = v; };
  set('mic', c.mic);
  set('aim', c.aim.join(','));
  set('find', c.find.join(','));
  set('role', c.role.join(','));
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

/** 총 그림 하나. 그림이 없는 총(JS9 등)이거나 못 불러오면 null → 카드에는 이름만 나온다 */
const loadGun = (gun) => (gun && gun.image
  ? loadImage(BASE + 'weapons/' + gun.image + '.png').catch(() => null)
  : Promise.resolve(null));

/** 카드 하나에 필요한 그림·표를 받는다 */
export async function loadAssets(card) {
  const [palette, frame, tierIcon, mainImg, subImg] = await Promise.all([
    loadJson(BASE + 'frames/palette.json'),
    loadImage(BASE + 'frames/' + card.tierFamily + '.png'),
    loadImage(BASE + 'tiers/' + card.tierIcon + '.png'),
    loadGun(card.mainGun),
    loadGun(card.subGun),
    document.fonts ? document.fonts.ready : null,
  ]);
  return { palette, frame, tierIcon, gunImg: { main: mainImg, sub: subImg }, maps: {} };
}

/** 티어 색 (빛 번짐용 바탕색) */
export const tierGlow = (A, card) => (A.palette[card.tierFamily] || A.palette.Unranked)[1];

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
const LEFT = 28, RIGHT = W - 28;   // 카드 안 글자·상자가 놓이는 가로 범위
const BODY_TOP = 100;              // 머리(티어·닉네임) 아래 본문이 시작하는 곳
const BODY_BOTTOM = 436;           // 본문이 끝나야 하는 곳 (아래는 "최근 경쟁전 N경기 기준" 줄)
const PANEL_MAX = 116, PANEL_MIN = 84;   // 무기 상자 높이 범위 (태그가 많으면 줄어든다)
const PANEL_GAP = 14;              // 무기 상자 아래 여백
const STATS_H = 44, STATS_GAP = 18;      // KDA·딜 큰 숫자 줄과 그 아래 여백
const MAX_COMBAT_LINES = 3;

/** 작은 제목 (노란 글자, 자간 넓게) */
function label(x, text, px, y, color, align = 'left') {
  x.font = `700 10px ${FONT}`; x.fillStyle = color; x.textAlign = align;
  if ('letterSpacing' in x) x.letterSpacing = '1px';
  x.fillText(text, px, y);
  if ('letterSpacing' in x) x.letterSpacing = '0px';
  x.textAlign = 'left';
}

/** 태그 묶음을 놓을 자리 계산: [{text, x, y, w}] , 높이 */
function layoutChips(x, tags, left, right) {
  x.font = `600 11px ${FONT}`;
  const out = [];
  let cx = left, cy = 0;
  for (const t of tags) {
    const w = Math.ceil(x.measureText('#' + t).width) + 18;
    if (cx + w > right && cx > left) { cx = left; cy += 27; }
    out.push({ text: '#' + t, x: cx, y: cy, w });
    cx += w + 6;
  }
  return { chips: out, height: tags.length ? cy + 22 : 0 };
}

function drawChips(x, lay, top) {
  for (const c of lay.chips) {
    roundRect(x, c.x, top + c.y, c.w, 22, 11);
    x.fillStyle = 'rgba(8,8,10,.72)'; x.fill();
    x.strokeStyle = 'rgba(255,255,255,.16)'; x.lineWidth = 1; x.stroke();
    x.font = `600 11px ${FONT}`; x.fillStyle = '#eee';
    x.fillText(c.text, c.x + 9, top + c.y + 15);
  }
}

/** 전투 성향: 점으로 이은 글자 줄 (가로를 넘으면 다음 줄로) */
function combatLines(x, tags, left, right) {
  x.font = `500 11px ${FONT}`;
  const lines = [];
  let cur = '';
  for (const t of tags) {
    const next = cur ? cur + '  ·  ' + t : t;
    if (cur && x.measureText(next).width > right - left) { lines.push(cur); cur = t; } else cur = next;
  }
  if (cur) lines.push(cur);
  return lines;
}

/** 총 그림을 (cx, cy) 가운데에 maxW×maxH 안으로 맞춰 그린다 (원본보다 크게 키우지 않음) */
function drawGunImage(x, img, cx, cy, maxW, maxH) {
  const k = Math.min(maxW / img.width, maxH / img.height, 1);
  const w = img.width * k, h = img.height * k;
  x.save();
  x.imageSmoothingEnabled = true; x.imageSmoothingQuality = 'high';
  x.shadowColor = 'rgba(0,0,0,.7)'; x.shadowBlur = 12; x.shadowOffsetY = 5;
  x.drawImage(img, cx - w / 2, cy - h / 2, w, h);
  x.restore();
}

/** 무기 한 칸 (상자 안쪽): 그림 / 이름·비율 / 종류. cx = 칸 가운데, maxW = 그림 최대 너비 */
function drawGun(x, gun, img, cx, top, h, maxW) {
  const gunTop = top + 12, gunH = h - 12 - 34;
  x.textAlign = 'center';
  if (img) {
    drawGunImage(x, img, cx, gunTop + gunH / 2, maxW, gunH);
  } else {
    // 그림이 없는 총(JS9 등)이거나 못 불러오면 이름만 크게
    x.font = `800 22px ${FONT}`; x.fillStyle = '#fff';
    x.fillText(gun.name, cx, gunTop + gunH / 2 + 8);
  }
  // 이름 + 비율 (그림이 없으면 비율만) 가운데 맞춤
  const share = gun.share + '%';
  x.font = `800 13px ${FONT}`;
  const nameW = img ? x.measureText(gun.name).width + 6 : 0;
  const shareW = x.measureText(share).width;
  const start = cx - (nameW + shareW) / 2;
  x.textAlign = 'left';
  if (img) { x.fillStyle = '#fff'; x.fillText(gun.name, start, top + h - 20); }
  x.fillStyle = YELLOW; x.fillText(share, start + nameW, top + h - 20);
  x.font = `500 9px ${FONT}`; x.fillStyle = '#8a8a92'; x.textAlign = 'center';
  x.fillText(gun.type, cx, top + h - 7); x.textAlign = 'left';
}

/** 무기 상자: 주무기·보조무기가 한 상자 안에 나란히 (보조무기가 없으면 주무기 하나) */
function drawWeapons(x, A, card, top, h) {
  roundRect(x, LEFT, top, RIGHT - LEFT, h, 10);
  x.fillStyle = 'rgba(8,8,10,.62)'; x.fill();
  x.strokeStyle = 'rgba(255,255,255,.1)'; x.lineWidth = 1; x.stroke();
  if (card.subGun) {
    x.fillStyle = 'rgba(255,255,255,.1)'; x.fillRect(W / 2, top + 12, 1, h - 24);
    drawGun(x, card.mainGun, A.gunImg.main, (LEFT + W / 2) / 2, top, h, 136);
    drawGun(x, card.subGun, A.gunImg.sub, (W / 2 + RIGHT) / 2, top, h, 136);
  } else {
    drawGun(x, card.mainGun, A.gunImg.main, W / 2, top, h, 240);
  }
}

/**
 * 카드 한 장.
 * @param look  { area(배경 그림), tags(성향 태그) }
 * @param ms    지금까지 흐른 시간 (귀퉁이 반짝임용)
 * @param scale 픽셀 배율
 */
export function draw(x, A, card, look, ms, scale) {
  const pal = A.palette[card.tierFamily] || A.palette.Unranked;
  x.setTransform(scale, 0, 0, scale, 0, 0);
  x.clearRect(0, 0, W, H);
  x.imageSmoothingEnabled = false;
  x.textAlign = 'left';

  // 배경 맵 + 위아래 어둡게
  x.fillStyle = '#0b0b0c';
  x.fillRect(INNER, INNER, W - INNER * 2, H - INNER * 2);
  x.drawImage(look.area, INNER, INNER, W - INNER * 2, H - INNER * 2);
  const g = x.createLinearGradient(0, INNER, 0, H - INNER);
  g.addColorStop(0, 'rgba(10,10,12,.78)');
  g.addColorStop(0.35, 'rgba(10,10,12,.4)');
  g.addColorStop(0.7, 'rgba(10,10,12,.62)');
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
    x.fillText(card.clan, 96, ty); ty += 25;
  } else {
    ty += 14;
  }
  x.save();
  x.shadowColor = 'rgba(0,0,0,.85)'; x.shadowBlur = 6; x.shadowOffsetY = 2;
  let size = 24;                      // 긴 닉네임은 글자를 줄여서 머리 칸에 맞춘다
  for (; size > 15; size--) { x.font = `800 ${size}px ${FONT}`; if (x.measureText(card.nickname).width <= RIGHT - 96) break; }
  x.font = `800 ${size}px ${FONT}`; x.fillStyle = '#fff';
  x.fillText(card.nickname, 96, ty);
  x.restore();
  x.font = `800 10px ${FONT}`; x.fillStyle = pal[2];
  if ('letterSpacing' in x) x.letterSpacing = '1.5px';
  x.fillText(card.tierText, 96, ty + 16);
  if ('letterSpacing' in x) x.letterSpacing = '0px';

  // 본문: 무기 상자 → KDA·딜 큰 숫자 → 나의 키워드 → 전투 성향. 위에서부터 쌓고, 넘치면 무기 상자를 줄인다
  const keywords = layoutChips(x, look.tags, LEFT, RIGHT);
  let lines = combatLines(x, card.combatTags, LEFT, RIGHT).slice(0, MAX_COMBAT_LINES);
  const kwBlock = keywords.height ? 14 + keywords.height + 16 : 0;
  const cbBlock = (n) => (n ? 26 + (n - 1) * 16 + 4 : 0);
  const hasGun = !!card.mainGun;
  const room = BODY_BOTTOM - BODY_TOP;
  const rest = (n) => STATS_H + STATS_GAP + kwBlock + cbBlock(n) + (hasGun ? PANEL_GAP : 14);
  while (lines.length > 1 && room - rest(lines.length) < PANEL_MIN) lines = lines.slice(0, -1);
  const panelH = hasGun ? Math.max(PANEL_MIN, Math.min(PANEL_MAX, room - rest(lines.length))) : 0;

  let y = BODY_TOP;
  if (hasGun) {
    drawWeapons(x, A, card, y, panelH);
    y += panelH + PANEL_GAP;
  } else {
    y += 14;
  }

  // KDA · 평균 딜: 큰 숫자
  const big = (name, value, px) => {
    x.font = `500 10px ${FONT}`; x.fillStyle = '#a6a6ad'; x.fillText(name, px, y + 8);
    x.font = `800 28px ${FONT}`; x.fillStyle = '#fff'; x.fillText(value, px, y + 36);
  };
  big('KDA', card.kda, LEFT);
  big('평균 딜', String(card.avgDamage), LEFT + 100);
  y += STATS_H + STATS_GAP;

  if (keywords.chips.length) {
    label(x, '나의 키워드', LEFT, y + 8, YELLOW);
    drawChips(x, keywords, y + 14);
    y += 14 + keywords.height + 16;
  }
  if (lines.length) {
    label(x, '전투 성향', LEFT, y + 8, YELLOW);
    x.font = `500 11px ${FONT}`; x.fillStyle = '#d4d4d8';
    lines.forEach((l, i) => x.fillText(l, LEFT, y + 26 + i * 16));
  }

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
 * 캔버스에서 카드를 계속 그린다 (귀퉁이 반짝임만 움직임). getLook() 이 돌려준 모습을 그린다.
 * @return 멈추는 함수
 */
export function play(canvas, A, card, getLook) {
  const ctx = canvas.getContext('2d');
  const started = performance.now();
  let timer = null;
  const tick = () => {
    const look = getLook();
    if (look) draw(ctx, A, card, look, performance.now() - started, canvas.width / W);
    timer = setTimeout(tick, 450);
  };
  tick();
  return () => clearTimeout(timer);
}
