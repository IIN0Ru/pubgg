/*
 * 플레이어 카드 꾸미기 화면 (/card). 그리기는 card-render.js.
 *
 *  - 처음 모습: 주소에 꾸민 값이 있으면 그것, 없으면 사이트에 저장한 카드, 그것도 없으면 기본
 *  - 꾸민 값은 주소 뒤에도 붙인다 (?mic=o&aim=chicken,rank&find=duo&role=igl)
 *  - "사이트에 저장": 꾸민 값 + 배경 + 미리보기 PNG 를 서버에 저장 (POST /api/card).
 *    처음 저장하면 서버가 수정 열쇠를 주고, 이 브라우저(localStorage)에 보관한다. 다른 브라우저는 덮어쓸 수 없다
 *  - "공유 링크 복사": 저장한 뒤 전적 검색 주소를 복사 → 디스코드·카톡에 붙이면 카드 그림이 미리보기로 나온다
 *  - PNG 저장: 지금 보이는 카드 그대로 내려받기
 */
import {
  W, H, PICKS, loadAssets, tierGlow, randomBackground, backgroundImage,
  normalizeChoices, choicesToParams, styleTags, draw, play,
} from './card-render.js';

const canvas = document.querySelector('[data-player-card]');
const form = document.querySelector('[data-card-form]');
const status = document.querySelector('[data-card-status]');
const card = JSON.parse(canvas.dataset.card);
const saved = canvas.dataset.saved ? JSON.parse(canvas.dataset.saved) : null;
const platform = canvas.dataset.platform;

let A = null;        // 그림·표
let look = null;     // 지금 모습 { area, tags }
let choices = null;  // 지금 꾸민 값
let bg = null;       // 지금 배경 { map, sx, sy }

// ---------- 꾸민 값 ↔ 주소·화면 ----------

function initialChoices() {
  const q = new URLSearchParams(location.search);
  const fromUrl = ['mic', 'aim', 'find', 'role'].some((k) => q.has(k));
  if (!fromUrl && saved) return normalizeChoices((k) => saved.choices[k]);
  return normalizeChoices((k) => q.get(k));
}

function writeUrl(c) {
  const q = new URLSearchParams();
  q.set('platform', platform);
  q.set('nickname', card.nickname);
  Object.entries(choicesToParams(c)).forEach(([k, v]) => q.set(k, v));
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
  const many = (n) => [...form.querySelectorAll(`input[name="${n}"]:checked`)].map((el) => el.value);
  return normalizeChoices((k) => (PICKS[k] && k !== 'mic' ? many(k).join(',') : one(k)));
}

async function update(newBg) {
  const c = formChoices();
  writeUrl(c);
  if (newBg) bg = newBg;
  const area = newBg || !look ? await backgroundImage(A, bg) : look.area;
  choices = c;
  look = { area, tags: styleTags(card, c) };
}

// ---------- 내려받기 ----------

const fileBase = () => (card.nickname + '_pubgg').replace(/[\\/:*?"<>|]/g, '_');

function download(blob, name) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

function snapshot(scale) {
  const c = document.createElement('canvas');
  c.width = W * scale; c.height = H * scale;
  draw(c.getContext('2d'), A, card, look, 0, scale);
  return c;
}

function savePng() {
  snapshot(3).toBlob((b) => { download(b, fileBase() + '.png'); status.textContent = 'PNG 를 저장했어요.'; });
}

// ---------- 사이트에 저장 · 공유 ----------

const tokenKey = () => 'pubgg.cardToken:' + platform + ':' + card.nickname.toLowerCase();
const readToken = () => { try { return localStorage.getItem(tokenKey()); } catch (e) { return null; } };
const writeToken = (t) => { try { localStorage.setItem(tokenKey(), t); } catch (e) { /* 저장 못 해도 이번엔 저장됨 */ } };

/** @return 전적 검색 주소 (저장 못 했으면 null) */
async function saveToSite() {
  status.textContent = '저장하는 중…';
  const res = await fetch('/api/card', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      platform, nickname: card.nickname,
      choices: choicesToParams(choices), background: bg,
      png: snapshot(2).toDataURL('image/png'),   // 미리보기 그림 720×960
      token: readToken(),
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    status.textContent = body.message || '저장하지 못했어요.';
    return null;
  }
  writeToken(body.token);
  status.textContent = '사이트에 저장했어요. 전적 화면에 프로필로 보여요.';
  return body.link;
}

const searchLink = () => '/search?' + new URLSearchParams({ platform, nickname: card.nickname }).toString();

async function share() {
  const link = (await saveToSite()) || searchLink();
  const url = location.origin + link;
  try {
    await navigator.clipboard.writeText(url);
    status.textContent += ' 전적 링크를 복사했어요. 디스코드·카톡에 붙이면 카드가 미리보기로 나와요.';
  } catch (e) {
    status.textContent += ' 링크를 복사하지 못했어요: ' + url;
  }
}

// ---------- 시작 ----------

const run = (fn, fail) => () => Promise.resolve().then(fn).catch((e) => { status.textContent = fail; console.error(e); });

async function start() {
  A = await loadAssets(card);
  canvas.style.setProperty('--tier-glow', tierGlow(A, card));
  fillForm(initialChoices());
  bg = saved && saved.background ? saved.background : await randomBackground(A);
  await update(null);
  play(canvas, A, card, () => look);

  form.addEventListener('change', run(() => update(null), '바꾸지 못했어요.'));
  document.querySelector('[data-card-reroll]').addEventListener('click',
    run(async () => update(await randomBackground(A)), '배경을 바꾸지 못했어요.'));
  document.querySelector('[data-card-png]').addEventListener('click', run(savePng, 'PNG 를 만들지 못했어요.'));
  document.querySelector('[data-card-save]').addEventListener('click', run(saveToSite, '저장하지 못했어요.'));
  document.querySelector('[data-card-share]').addEventListener('click', run(share, '공유하지 못했어요.'));
}

start().catch((e) => {
  status.textContent = '카드를 그리지 못했어요. 새로고침해 주세요.';
  console.error(e);
});
