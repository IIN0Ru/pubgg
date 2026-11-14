import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { createShare } from '../../api/client';
import type { ShareCard, Shard } from '../../api/types';
import {
  PICKS,
  choicesToParams,
  normalizeChoices,
  styleTags,
  type Choices,
  type MultiPick,
  type SinglePick,
} from '../../lib/card/choices';
import {
  backgroundImage,
  loadAssets,
  play,
  randomBackground,
  snapshot,
  tierGlow,
  type Background,
  type CardAssets,
  type CardLook,
} from '../../lib/card/render';

/** 고르는 칸: 제목, 주소 이름, 보기 글자 (카드에 붙는 글자는 PICKS 에 따로 있다) */
const SINGLE_FIELDS: ReadonlyArray<{ key: SinglePick; legend: string; options: ReadonlyArray<[string, string]> }> = [
  { key: 'mic', legend: '마이크', options: [['', '안 고름'], ['o', '마이크 O'], ['x', '마이크 X'], ['listen', '듣기만']] },
];
/** labels: 보기 글자가 카드 태그 글자(PICKS)와 다를 때만 */
const MULTI_FIELDS: ReadonlyArray<{ key: MultiPick; legend: string; labels?: Record<string, string> }> = [
  {
    key: 'time',
    legend: '주로 하는 시간',
    labels: { dawn: '새벽', morning: '아침', lunch: '점심', evening: '저녁', night: '밤' },
  },
  { key: 'aim', legend: '지향' },
  { key: 'find', legend: '구함' },
  { key: 'role', legend: '역할' },
];

const fileBase = (nickname: string) => `${nickname}_pubgg`.replace(/[\\/:*?"<>|]/g, '_');

function download(blob: Blob, name: string) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

/**
 * 공유 카드(B카드) 꾸미기: 왼쪽 카드 캔버스 + 오른쪽 성향 태그 고르기.
 *  - 고른 값은 주소 뒤에도 붙인다 (?mic=o&time=night&aim=chicken,rank …) → 새로고침해도 이어진다
 *  - "공유 링크 만들기": 꾸민 값 + 배경 + 미리보기 PNG 를 서버에 올려 공유 주소(/s/{id})를 받고 복사한다
 *    같은 모습으로 다시 누르면 새로 만들지 않고 방금 만든 링크를 다시 복사한다
 *  - PNG 저장: 지금 보이는 카드 그대로 내려받기
 */
export function CardEditor({ card, shard }: { card: ShareCard; shard: Shard }) {
  const [params, setParams] = useSearchParams();
  const [choices, setChoices] = useState<Choices>(() => normalizeChoices((k) => params.get(k)));
  const [bg, setBg] = useState<Background | null>(null);
  const [status, setStatus] = useState('');
  const [ready, setReady] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const assets = useRef<CardAssets | null>(null);
  const look = useRef<CardLook | null>(null);
  const area = useRef<HTMLCanvasElement | null>(null);
  const lastShare = useRef<{ key: string; link: string } | null>(null);

  // 처음: 그림을 받고 랜덤 배경으로 그리기 시작
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    let stop: (() => void) | undefined;
    let cancelled = false;
    (async () => {
      const A = await loadAssets(card);
      const first = await randomBackground(A);
      area.current = await backgroundImage(A, first);
      if (cancelled) return;
      assets.current = A;
      canvas.style.setProperty('--tier-glow', tierGlow(A, card));
      setBg(first);
      setReady(true);
      stop = play(canvas, A, card, () => look.current);
    })().catch((e: unknown) => {
      if (!cancelled) setStatus('카드를 그리지 못했어요. 새로고침해 주세요.');
      console.error(e);
    });
    return () => {
      cancelled = true;
      stop?.();
    };
  }, [card]);

  // 꾸민 값이 바뀌면 카드 태그와 주소를 바꾼다
  useEffect(() => {
    if (area.current) look.current = { area: area.current, tags: styleTags(choices) };
    setParams({ platform: shard, nickname: card.nickname, ...choicesToParams(choices) }, { replace: true });
  }, [choices, ready, shard, card.nickname, setParams]);

  const pickOne = (key: SinglePick, value: string) => setChoices((c) => ({ ...c, [key]: value }));
  const toggleMany = (key: MultiPick, value: string) =>
    setChoices((c) => {
      const has = c[key].includes(value);
      const next = has ? c[key].filter((v) => v !== value) : [...c[key], value];
      // 카드에 붙는 순서는 늘 PICKS 순서 (누른 순서와 상관없이 같은 꾸밈이면 같은 카드)
      return { ...c, [key]: Object.keys(PICKS[key]).filter((v) => next.includes(v)) };
    });

  const reroll = async () => {
    const A = assets.current;
    if (!A) return;
    try {
      const next = await randomBackground(A);
      area.current = await backgroundImage(A, next);
      look.current = { area: area.current, tags: styleTags(choices) };
      setBg(next);
    } catch (e) {
      setStatus('배경을 바꾸지 못했어요.');
      console.error(e);
    }
  };

  const savePng = () => {
    const A = assets.current;
    if (!A || !look.current) return;
    snapshot(A, card, look.current, 3).toBlob((b) => {
      if (!b) {
        setStatus('PNG 를 만들지 못했어요.');
        return;
      }
      download(b, `${fileBase(card.nickname)}.png`);
      setStatus('PNG 를 저장했어요.');
    });
  };

  const share = async () => {
    const A = assets.current;
    if (!A || !look.current || !bg) return;
    const key = JSON.stringify([choicesToParams(choices), bg]);
    let link = lastShare.current?.key === key ? lastShare.current.link : null;
    if (!link) {
      setStatus('공유 링크를 만드는 중…');
      try {
        const result = await createShare({
          platform: shard,
          nickname: card.nickname,
          choices: choicesToParams(choices),
          background: bg,
          png: snapshot(A, card, look.current, 2).toDataURL('image/png'), // 미리보기 그림 720×960
        });
        link = result.link;
        lastShare.current = { key, link };
      } catch (e) {
        setStatus(e instanceof Error && e.message ? e.message : '공유 링크를 만들지 못했어요.');
        return;
      }
    }
    const url = window.location.origin + link;
    try {
      await navigator.clipboard.writeText(url);
      setStatus('공유 링크를 복사했어요. 디스코드·카톡에 붙이면 이 카드가 미리보기로 나와요.');
    } catch {
      setStatus(`링크를 복사하지 못했어요. 직접 복사해 주세요: ${url}`);
    }
  };

  return (
    <div className="pcard__layout">
      {/* 이 카드는 공유할 때만 쓰는 B카드다. 전적 화면의 A카드에는 성향 태그가 붙지 않는다 */}
      <figure className="pcard__stage">
        <canvas
          ref={canvasRef}
          className="pcard__canvas"
          width={720}
          height={960}
          role="img"
          aria-label={`${card.nickname} 공유 카드`}
        />
        <figcaption className="sr-only">
          {card.nickname} · {card.tierText} · KDA {card.kda} · 평균 딜 {card.avgDamage}
        </figcaption>
      </figure>

      <form className="pcard__edit" aria-label="카드 꾸미기" onSubmit={(e) => e.preventDefault()}>
        <h1 className="pcard__title">공유 카드</h1>
        <p className="pcard__lead">
          전투 태그는 최근 {card.scope === 'normal' ? '일반 경기' : '경쟁전'} 기록으로 자동으로 붙어요. 성향은 직접 골라
          주세요. 공유할 때만 보이고, 전적 화면에는 나오지 않아요.
        </p>

        {SINGLE_FIELDS.map((f) => (
          <fieldset className="pick" key={f.key}>
            <legend>{f.legend}</legend>
            {f.options.map(([value, text]) => (
              <label key={value}>
                <input
                  type="radio"
                  name={f.key}
                  value={value}
                  checked={choices[f.key] === value}
                  onChange={() => pickOne(f.key, value)}
                />{' '}
                {text}
              </label>
            ))}
          </fieldset>
        ))}
        {MULTI_FIELDS.map((f) => (
          <fieldset className="pick" key={f.key}>
            <legend>
              {f.legend} <small>여러 개 가능</small>
            </legend>
            {Object.entries(PICKS[f.key]).map(([value, text]) => (
              <label key={value}>
                <input
                  type="checkbox"
                  name={f.key}
                  value={value}
                  checked={choices[f.key].includes(value)}
                  onChange={() => toggleMany(f.key, value)}
                />{' '}
                {f.labels?.[value] ?? text}
              </label>
            ))}
          </fieldset>
        ))}

        <div className="pcard__actions">
          <button className="pcard__btn pcard__btn--primary" type="button" disabled={!ready} onClick={() => void share()}>
            공유 링크 만들기
          </button>
          <button className="pcard__btn" type="button" disabled={!ready} onClick={() => void reroll()}>
            배경 랜덤 설정
          </button>
          <button className="pcard__btn" type="button" disabled={!ready} onClick={savePng}>
            PNG 저장
          </button>
        </div>
        <p className="pcard__status" role="status">
          {status}
        </p>
        <p className="pcard__note">
          공유 링크를 디스코드·카톡에 붙이면 이 카드가 미리보기로 나오고, 링크를 열면 전적 화면이 나와요. 같은 닉네임으로
          새로 공유하면 이전 링크의 미리보기 그림은 사라져요.
        </p>
      </form>
    </div>
  );
}

