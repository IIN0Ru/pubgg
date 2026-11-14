import { useSearchParams } from 'react-router';
import { PlayerLists } from '../components/layout/PlayerLists';
import { useDocumentTitle } from '../components/layout/PageFrame';
import { SiteFooter } from '../components/layout/SiteFooter';
import { ThemeToggle } from '../components/layout/ThemeToggle';
import { useSearchSubmit } from '../components/layout/useSearchSubmit';
import { NICKNAME_MAX, PLATFORMS } from '../lib/search';

/** 서버가 /search 주소가 이상할 때 돌려보내며 붙이는 ?error= (HomeController) */
const SERVER_ERRORS: Record<string, string> = {
  nickname: '닉네임을 입력해 주세요.',
  platform: '스팀이나 카카오 중 하나를 골라 주세요.',
};

/** 첫 화면: 로고 + 플랫폼 + 닉네임 검색 + 즐겨찾기·최근 검색 */
export function HomePage() {
  useDocumentTitle('펍지지 PUB.GG');
  const [params] = useSearchParams();
  const form = useSearchSubmit('steam', '');
  const error = form.error ?? SERVER_ERRORS[params.get('error') ?? ''] ?? null;

  return (
    <main className="hero">
      <p className="band">스팀·카카오 닉네임 하나로 내 배그 플레이 스타일 확인</p>

      {/* 다크/라이트 전환: 노란 띠를 가리지 않게 띠 아래 오른쪽 */}
      <div className="hero__tools">
        <ThemeToggle />
      </div>

      <div className="hero__inner">
        <h1 className="logo">
          PUB<span className="logo__dot">.</span>GG
        </h1>
        <p className="logo-ko">펍지지</p>
        {/* 다른 전적 사이트와 다른 점을 한 줄로 */}
        <p className="hero__lead">자기장 운영 · 파밍 · 주무기로 보는 플레이 스타일 카드</p>

        <form className="search" onSubmit={form.submit}>
          <fieldset className="platform">
            <legend className="sr-only">플랫폼</legend>
            {PLATFORMS.map((p) => (
              <label className="platform__option" key={p.shard}>
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

          <div className="search__field">
            <label htmlFor="nickname" className="sr-only">
              배그 닉네임
            </label>
            <input
              id="nickname"
              name="nickname"
              type="text"
              placeholder="배그 닉네임"
              maxLength={NICKNAME_MAX}
              autoComplete="off"
              spellCheck={false}
              required
              value={form.nickname}
              onChange={(e) => form.setNickname(e.target.value)}
            />
            <button type="submit">분석하기</button>
          </div>

          {error !== null && (
            <p className="search__error" role="alert">
              {error}
            </p>
          )}
        </form>

        {/* 즐겨찾기·최근 검색 (브라우저에 저장) */}
        <div className="home-lists">
          <PlayerLists />
        </div>
      </div>

      <SiteFooter />
    </main>
  );
}
