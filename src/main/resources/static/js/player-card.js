/*
 * 공유 카드(B카드) 꾸미기 화면 (/card). 그리기는 card-render.js.
 *
 *  - 성향 태그(마이크·접속 시간대·지향·구함·역할)를 고르고 배경을 정한다. 고른 값은 주소 뒤에도 붙인다
 *    (?mic=o&time=night&aim=chicken,rank&find=duo&role=igl) → 새로고침해도 이어진다
 *  - "공유 링크 만들기": 꾸민 값 + 배경 + 미리보기 PNG 를 서버에 올려 공유 주소(/s/{id})를 받고 복사한다
 *    (POST /api/share). 디스코드·카톡에 붙이면 이 카드가 미리보기로 나오고, 링크를 열면 전적 화면이 나온다
 *  - 같은 모습으로 다시 누르면 새로 만들지 않고 방금 만든 링크를 다시 복사한다
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
const platform = canvas.dataset.platform;

let A = null;        // 그림·표
let look = null;     // 지금 모습 { area, tags }
let choices = null;  // 지금 꾸민 값
let bg = null;       // 지금 배경 { map, sx, sy }
let lastShare = null;  // 방금 만든 공유 { key, link } — 같은 모습이면 다시 만들지 않는다

// ---------- 꾸민 값 ↔ 주소·화면 ----------

function initialChoices() {
  const q = new URLSearchParams(location.search);
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
  const single = ['mic', 'time'];   // 하나만 고르는 것
  return normalizeChoices((k) => (PICKS[k] && !single.includes(k) ? many(k).join(',') : one(k)));
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

// ---------- 공유 ----------

/** 같은 모습인지 알아보는 값 (꾸민 값 + 배경) */
const lookKey = () => JSON.stringify([choicesToParams(choices), bg]);

/** @return 공유 주소 /s/{id} (만들지 못했으면 null) */
async function createShare() {
  const key = lookKey();
  if (lastShare && lastShare.key === key) return lastShare.link;

  status.textContent = '공유 링크를 만드는 중…';
  const res = await fetch('/api/share', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      platform, nickname: card.nickname,
      choices: choicesToParams(choices), background: bg,
      png: snapshot(2).toDataURL('image/png'),   // 미리보기 그림 720×960
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    status.textContent = body.message || '공유 링크를 만들지 못했어요.';
    return null;
  }
  lastShare = { key, link: body.link };
  return body.link;
}

async function share() {
  const link = await createShare();
  if (!link) return;
  const url = location.origin + link;
  try {
    await navigator.clipboard.writeText(url);
    status.textContent = '공유 링크를 복사했어요. 디스코드·카톡에 붙이면 이 카드가 미리보기로 나와요.';
  } catch (e) {
    status.textContent = '링크를 복사하지 못했어요. 직접 복사해 주세요: ' + url;
  }
}

// ---------- 시작 ----------

const run = (fn, fail) => () => Promise.resolve().then(fn).catch((e) => { status.textContent = fail; console.error(e); });

async function start() {
  A = await loadAssets(card);
  canvas.style.setProperty('--tier-glow', tierGlow(A, card));
  fillForm(initialChoices());
  bg = await randomBackground(A);
  await update(null);
  play(canvas, A, card, () => look);

  form.addEventListener('change', run(() => update(null), '바꾸지 못했어요.'));
  document.querySelector('[data-card-reroll]').addEventListener('click',
    run(async () => update(await randomBackground(A)), '배경을 바꾸지 못했어요.'));
  document.querySelector('[data-card-png]').addEventListener('click', run(savePng, 'PNG 를 만들지 못했어요.'));
  document.querySelector('[data-card-share]').addEventListener('click', run(share, '공유 링크를 만들지 못했어요.'));
}

start().catch((e) => {
  status.textContent = '카드를 그리지 못했어요. 새로고침해 주세요.';
  console.error(e);
});
