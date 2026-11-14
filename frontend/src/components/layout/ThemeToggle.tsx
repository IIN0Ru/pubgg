import { useState } from 'react';

const THEME_KEY = 'pubgg.theme';
type Theme = 'light' | 'dark';

/** 지금 모드. 처음 모드는 app.html 의 <head> 스크립트가 화면을 그리기 전에 이미 정해 둔다 (깜빡임 방지) */
function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark';
}

/**
 * 다크/라이트 전환 버튼 (해 = 라이트로 바꾸기, 달 = 다크로 바꾸기. 어느 그림이 보일지는 main.css 가 data-theme 로 정한다).
 * 고른 모드는 브라우저에 저장해서 다음에 와도 그대로 (저장이 막힌 브라우저면 이번 화면에서만).
 */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme);

  const toggle = () => {
    const next: Theme = currentTheme() === 'light' ? 'dark' : 'light';
    document.documentElement.dataset.theme = next;
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      // 저장이 막힌 환경이면 이번 화면에서만
    }
    setTheme(next);
  };

  return (
    <button
      className="theme-toggle"
      type="button"
      aria-label={theme === 'light' ? '다크 모드로 바꾸기' : '라이트 모드로 바꾸기'}
      onClick={toggle}
    >
      <svg className="theme-toggle__sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
      </svg>
      <svg className="theme-toggle__moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
      </svg>
    </button>
  );
}
