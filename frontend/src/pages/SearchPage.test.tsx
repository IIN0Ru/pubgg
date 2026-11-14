import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { PlayerView } from './SearchPage';
import { card, match, page, season, shareCard } from '../test/fixtures';
import { RECENT_KEY, usePlayerLists } from '../store/players';

vi.mock('../lib/cardRenderer', () => ({ drawProfileCard: vi.fn(() => Promise.resolve(() => undefined)) }));

function json(status: number, body: unknown) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PlayerView target={{ shard: 'steam', nickname: 'Sample-01' }} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

beforeEach(() => {
  localStorage.clear();
  usePlayerLists.getState().reload();
});

describe('전적 화면', () => {
  it('잘못된 주소면 안내한다', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <PlayerView target={null} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('닉네임과 플랫폼을 확인해 주세요.');
  });

  it('전적을 받아 카드 자리와 경기 목록을 보여준다', async () => {
    vi.stubGlobal('fetch', vi.fn(() => json(200, page({ cardStatus: 'pending', matches: [match()] }))));
    renderApp();
    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중');
    expect(await screen.findByRole('heading', { name: 'Sample-01' })).toBeInTheDocument();
    expect(screen.getByText(/플레이 카드를 분석하는 중이에요/)).toBeInTheDocument();
    expect(screen.getByText('최근 1경기')).toBeInTheDocument();
  });

  it('카드가 있으면 상세 패널과 시즌 비교·팀원 상자까지 그린다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        json(
          200,
          page({
            cardStatus: 'ready',
            card: card(),
            shareCard,
            season,
            seasonComparison: [{ label: 'KDA', recent: '3.25', season: '2.76', trend: 1 }],
            teammates: [{ name: 'Buddy', games: 5, wins: 1, avgRank: 4.1 }],
            matches: [match()],
          }),
        ),
      ),
    );
    renderApp();
    expect(await screen.findByText('주무기')).toBeInTheDocument();
    expect(screen.getByText('자기장 운 · 보통')).toBeInTheDocument();
    expect(screen.getByText(/최근 vs 이번 시즌/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Buddy' })).toBeInTheDocument();
  });

  it('카드가 없으면 상세 패널과 시즌 비교는 그리지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn(() => json(200, page({ cardStatus: 'pending', matches: [match()] }))));
    renderApp();
    await screen.findByRole('heading', { name: 'Sample-01' });
    expect(screen.queryByText('주무기')).not.toBeInTheDocument();
    expect(screen.queryByText(/최근 vs 이번 시즌/)).not.toBeInTheDocument();
  });

  it('전적을 받으면 최근 검색에 서버가 알려준 이름으로 기록한다', async () => {
    vi.stubGlobal('fetch', vi.fn(() => json(200, page({ nickname: 'Sample-01' }))));
    renderApp();
    await screen.findByRole('heading', { name: 'Sample-01' });
    expect(JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]')).toEqual([
      { platform: 'steam', nickname: 'Sample-01', label: '스팀' },
    ]);
  });

  it('없는 플레이어(404)는 서버 문구를 그대로 보여준다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => json(404, { code: 'PLAYER_NOT_FOUND', message: "'Sample-01' 플레이어를 찾지 못했어요." })),
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

  it('서버 오류 뒤 "다시 시도"를 누르면 같은 주소를 다시 불러와 전적을 보여준다', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() => json(502, { code: 'UPSTREAM_ERROR', message: 'PUBG 서버가 응답하지 않아요.' }))
      .mockImplementation(() => json(200, page({ matches: [match()] })));
    vi.stubGlobal('fetch', fetchMock);
    renderApp();
    const retry = await screen.findByRole('button', { name: '다시 시도' });
    fireEvent.click(retry);
    expect(await screen.findByRole('heading', { name: 'Sample-01' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('네트워크가 끊기면 연결 안내를 보여준다', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))));
    renderApp();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('서버에 연결하지 못했어요'));
  });
});
