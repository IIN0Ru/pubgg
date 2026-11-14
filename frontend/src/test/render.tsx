import { render as rtlRender, type RenderOptions } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router';

/** 화면 안 링크(Link)는 라우터 안에서만 그려져서, 컴포넌트 테스트도 라우터로 감싼다 (rerender 해도 유지) */
export function render(ui: ReactElement, options?: Omit<RenderOptions, 'wrapper'>) {
  return rtlRender(ui, { wrapper: MemoryRouter, ...options });
}
