import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import type { Shard } from '../../api/types';
import { NICKNAME_MAX, PLATFORMS } from '../../lib/search';
import { usePlayerLists } from '../../store/players';
import { PlayerLists } from './PlayerLists';
import { ThemeToggle } from './ThemeToggle';
import { useSearchSubmit } from './useSearchSubmit';

interface Props {
  /** 지금 보고 있는 플랫폼·닉네임 (검색창에 미리 채움. 오타를 고쳐 다시 검색하기 쉽게) */
  shard?: Shard;
  nickname?: string;
}

/** 모든 화면(첫 화면 빼고) 상단 바: 로고 + 닉네임 검색 + 다크/라이트 */
export function Topbar({ shard = 'steam', nickname = '' }: Props) {
  // 다른 플레이어로 넘어가면 검색창도 그 값으로 다시 채운다
  return <TopbarInner key={`${shard}:${nickname}`} shard={shard} nickname={nickname} />;
}

function TopbarInner({ shard, nickname }: Required<Props>) {
  const form = useSearchSubmit(shard, nickname);
  const [open, setOpen] = useState(false);
  const fieldRef = useRef<HTMLDivElement>(null);
  const hasSaved = usePlayerLists((s) => s.favorites.length > 0 || s.recent.length > 0);

  // 보여줄 게 없으면 열지 않는다
  const tryOpen = () => setOpen(hasSaved);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  return (
    <header className="topbar">
      <div className="topbar__inner">
        <Link className="topbar__home" to="/" aria-label="펍지지 첫 화면">
          <span className="logo logo--small">
            PUB<span className="logo__dot">.</span>GG
          </span>
        </Link>

        <form className="topsearch" role="search" onSubmit={form.submit}>
          <fieldset className="topsearch__platform">
            <legend className="sr-only">플랫폼</legend>
            {PLATFORMS.map((p) => (
              <label key={p.shard}>
                <input
                  type="radio"
                  name="platform"
                  value={p.shard}
                  checked={form.shard === p.shard}
                  onChange={() => form.setShard(p.shard)}
                />
                <span>{p.label}</span>
              </label>
            ))}
          </fieldset>
          <div className="topsearch__field" ref={fieldRef}>
            <label htmlFor="top-nickname" className="sr-only">
              배그 닉네임
            </label>
            <input
              id="top-nickname"
              className="topsearch__input"
              name="nickname"
              type="text"
              placeholder="배그 닉네임"
              maxLength={NICKNAME_MAX}
              autoComplete="off"
              spellCheck={false}
              required
              value={form.nickname}
              aria-invalid={form.error !== null || undefined}
              title={form.error ?? undefined}
              // 검색창에 지금 닉네임이 채워져 있으면 목록은 열지 않고, 비어 있을 때만 연다
              onFocus={() => {
                if (form.nickname === '') tryOpen();
              }}
              onChange={(e) => {
                form.setNickname(e.target.value);
                if (e.target.value === '') tryOpen();
                else setOpen(false);
              }}
              onBlur={() => {
                // 목록 안의 링크를 누를 시간을 준다
                setTimeout(() => {
                  if (!fieldRef.current?.contains(document.activeElement)) setOpen(false);
                }, 150);
              }}
            />
            {/* 입력창을 누르면 즐겨찾기·최근 검색이 펼쳐짐. 목록에서 고르면 닫힌다 */}
            <div className="pdrop" hidden={!open} onClick={(e) => (e.target as HTMLElement).closest('a') && setOpen(false)}>
              {open && <PlayerLists />}
            </div>
          </div>
          <button className="topsearch__button" type="submit">
            분석하기
          </button>
        </form>

        <div className="topbar__tools">
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
