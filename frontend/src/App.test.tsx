import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { match, page } from './test/fixtures';
import { RECENT_KEY, usePlayerLists } from './store/players';

vi.mock('./lib/cardRenderer', () => ({ drawProfileCard: vi.fn(() => Promise.resolve(() => undefined)) }));

function json(status: number, body: unknown) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } });
  render(
    <QueryClientProvider client={client}>
      <App target={{ shard: 'steam', nickname: 'N0-RU' }} />
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

beforeEach(() => {
  localStorage.clear();
  usePlayerLists.getState().reload();
});

describe('App', () => {
  it('잘못된 주소면 안내한다', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <App target={null} />
      </QueryClientProvider>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('닉네임과 플랫폼을 확인해 주세요.');
  });

  it('전적을 받아 카드 자리와 경기 목록을 보여준다', async () => {
    vi.stubGlobal('fetch', vi.fn(() => json(200, page({ cardStatus: 'pending', matches: [match()] }))));
    renderApp();
    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중');
    expect(await screen.findByRole('heading', { name: 'N0-RU' })).toBeInTheDocument();
    expect(screen.getByText(/플레이 카드를 분석하는 중이에요/)).toBeInTheDocument();
    expect(screen.getByText('최근 1경기')).toBeInTheDocument();
  });

  it('전적을 받으면 최근 검색에 서버가 알려준 이름으로 기록한다', async () => {
    vi.stubGlobal('fetch', vi.fn(() => json(200, page({ nickname: 'N0-RU' }))));
    renderApp();
    await screen.findByRole('heading', { name: 'N0-RU' });
    expect(JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]')).toEqual([
      { platform: 'steam', nickname: 'N0-RU', label: '스팀' },
    ]);
  });

  it('없는 플레이어(404)는 서버 문구를 그대로 보여준다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => json(404, { code: 'PLAYER_NOT_FOUND', message: "'N0-RU' 플레이어를 찾지 못했어요." })),
    );
    renderApp();
    expect(await screen.findByRole('alert')).toHaveTextContent('플레이어를 찾지 못했어요');
  });

  it('요청 한도(429)는 다시 시도할 시간을 같이 보여준다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => json(429, { code: 'RATE_LIMITED', message: '검색이 너무 많아요.', retryAfterSec: 60 })),
    );
    renderApp();
    expect(await screen.findByRole('alert')).toHaveTextContent('60초 뒤');
  });

  it('네트워크가 끊기면 연결 안내를 보여준다', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))));
    renderApp();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('서버에 연결하지 못했어요'));
  });
});
