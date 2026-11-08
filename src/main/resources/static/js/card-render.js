/*
 * 플레이어 카드 그리기 (공용). 공유 카드 꾸미기 화면(player-card.js)과 전적 화면의 A카드(card-profile.js)가 같이 쓴다.
 *
 *  - 기록 값(card): 서버의 ShareCard JSON (닉네임·클랜·티어·KDA·딜·주무기/보조무기·전투 태그)
 *  - 꾸민 값(choices): 마이크·시간대·지향·구함·역할 (주소 값과 같은 이름). 공유할 때 만드는 B카드에만 붙는다
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
  time: { dawn: '새벽 접속', morning: '아침 접속', lunch: '점심 접속', evening: '저녁 접속', night: '밤 접속' },
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

/** 주소 값(아무 객체) → 검사한 꾸민 값. 모르는 이름이나 값은 무시된다 */
export function normalizeChoices(get) {
  const list = (k) => (get(k) || '').split(',').filter((v) => PICKS[k][v]);
  return {
    mic: PICKS.mic[get('mic')] ? get('mic') : '',
    time: PICKS.time[get('time')] ? get('time') : '',
    aim: list('aim'), find: list('find'), role: list('role'),
  };
}

/** 꾸민 값 → 저장·주소용 (안 고른 건 뺌) */
export function choicesToParams(c) {
  const out = {};
  const set = (k, v) => { if (v) out[k] = v; };
  set('mic', c.mic);
  set('time', c.time);
  set('aim', c.aim.join(','));
  set('find', c.find.join(','));
  set('role', c.role.join(','));
  return out;
}

/** 성향 줄: 마이크 → 접속 시간대 → 지향 → 구함 → 역할 */
export function styleTags(card, c) {
  const tags = [];
  if (c.mic) tags.push(PICKS.mic[c.mic]);
  if (c.time) tags.push(PICKS.time[c.time]);
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
const SLOT_MAX = 140, SLOT_MIN = 84;   // 무기 칸 높이 범위 (태그가 많으면 줄어든다)
const GAP_AFTER_SLOT = 28, ROW_GAP = 22;

/** 태그 묶음을 놓을 자리 계산: [{text, x, y, w}] , 높이 */
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

function drawChips(x, lay, top) {
  for (const c of lay.chips) {
    roundRect(x, c.x, top + c.y, c.w, 22, 11);
    x.fillStyle = 'rgba(8,8,10,.7)'; x.fill();
    x.strokeStyle = 'rgba(255,255,255,.12)'; x.lineWidth = 1; x.stroke();
    x.font = `500 11px ${FONT}`; x.fillStyle = '#e2e2e2';
    x.fillText(c.text, c.x + 9, top + c.y + 15);
  }
}

/** 총 그림을 (cx, cy) 가운데에 maxW×maxH 안으로 맞춰 그린다 (원본보다 크게 키우지 않음) */
function drawGunImage(x, img, cx, cy, maxW, maxH) {
  const k = Math.min(maxW / img.width, maxH / img.height, 1);
  const w = img.width * k, h = img.height * k;
  x.save();
  x.imageSmoothingEnabled = true; x.imageSmoothingQuality = 'high';
  x.shadowColor = 'rgba(0,0,0,.65)'; x.shadowBlur = 12; x.shadowOffsetY = 4;
  x.drawImage(img, cx - w / 2, cy - h / 2, w, h);
  x.restore();
}

/** 무기 칸 하나 (주무기 / 보조무기) */
function drawGunSlot(x, pal, label, gun, img, l, top, w, h) {
  roundRect(x, l, top, w, h, 8);
  x.fillStyle = 'rgba(8,8,10,.72)'; x.fill();
  x.strokeStyle = 'rgba(255,255,255,.12)'; x.lineWidth = 1; x.stroke();

  x.font = `500 9px ${FONT}`; x.fillStyle = pal[3];
  x.fillText(label, l + 10, top + 17);
  x.fillStyle = '#8a8a92'; x.textAlign = 'right';
  x.fillText(gun.type, l + w - 10, top + 17); x.textAlign = 'left';

  const imgTop = top + 24, imgH = h - 24 - 34;
  if (img) {
    drawGunImage(x, img, l + w / 2, imgTop + imgH / 2, Math.min(w - 20, 170), imgH);
    x.font = `800 13px ${FONT}`; x.fillStyle = '#fff';
    x.fillText(gun.name, l + 10, top + h - 14);
  } else {
    // 그림이 없는 총: 이름만 크게 (아래 줄에 또 쓰지 않음)
    x.font = `800 20px ${FONT}`; x.fillStyle = '#fff'; x.textAlign = 'center';
    x.fillText(gun.name, l + w / 2, imgTop + imgH / 2 + 7); x.textAlign = 'left';
  }
  x.font = `800 10px ${FONT}`; x.fillStyle = YELLOW; x.textAlign = 'right';
  x.fillText(gun.share + '%', l + w - 10, top + h - 14); x.textAlign = 'left';
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

  // 본문: 무기 칸 → KDA·딜 줄 → 나의 키워드 → 전투 성향. 위에서부터 쌓고, 넘치면 무기 칸을 줄인다
  const keywords = layoutChips(x, look.tags, LEFT, RIGHT);
  const combat = layoutChips(x, card.combatTags, LEFT, RIGHT);
  const STATS = 26;                                          // KDA·딜 줄이 차지하는 높이
  const kwBlock = keywords.height ? 8 + keywords.height + ROW_GAP : 0;
  const cbBlock = combat.height ? 8 + combat.height : 0;
  const hasGun = !!card.mainGun;
  const slotH = hasGun
    ? Math.max(SLOT_MIN, Math.min(SLOT_MAX, BODY_BOTTOM - BODY_TOP - GAP_AFTER_SLOT - STATS - kwBlock - cbBlock))
    : 0;

  let y = BODY_TOP;
  if (hasGun) {
    if (card.subGun) {
      drawGunSlot(x, pal, '주무기', card.mainGun, A.gunImg.main, LEFT, y, 150, slotH);
      drawGunSlot(x, pal, '보조무기', card.subGun, A.gunImg.sub, RIGHT - 150, y, 150, slotH);
    } else {
      drawGunSlot(x, pal, '주무기', card.mainGun, A.gunImg.main, LEFT, y, RIGHT - LEFT, slotH);
    }
    y += slotH + GAP_AFTER_SLOT;
  } else {
    y += 14;
  }

  // KDA · 평균 딜
  let sx = LEFT + 2;
  const stat = (name, value) => {
    x.font = `500 11px ${FONT}`; x.fillStyle = '#a6a6ad';
    x.fillText(name, sx, y); sx += Math.ceil(x.measureText(name).width) + 6;
    x.font = `800 13px ${FONT}`; x.fillStyle = '#fff';
    x.fillText(value, sx, y); sx += Math.ceil(x.measureText(value).width) + 18;
  };
  stat('KDA', card.kda);
  stat('평균 딜', String(card.avgDamage));
  y += STATS - 8;

  const row = (name, lay) => {
    if (!lay.chips.length) return;
    x.font = `700 10px ${FONT}`; x.fillStyle = YELLOW;
    x.fillText(name, LEFT + 2, y + 2);
    drawChips(x, lay, y + 8);
    y += 8 + lay.height + ROW_GAP;
  };
  row('나의 키워드', keywords);
  row('전투 성향', combat);

  x.font = `500 9px ${FONT}`; x.fillStyle = '#8a8a92';
  x.fillText(`최근 ${card.scope === 'normal' ? '일반' : '경쟁전'} ${card.matches}경기 기준`, 34, 450);
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
