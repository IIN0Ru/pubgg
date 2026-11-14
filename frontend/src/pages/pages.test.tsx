import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { FAVORITES_KEY, RECENT_KEY, usePlayerLists } from '../store/players';
import { page, shareCard } from '../test/fixtures';

vi.mock('../lib/cardRenderer', () => ({ drawProfileCard: vi.fn(() => Promise.resolve(() => undefined)) }));
// 카드 그리기는 그림 파일을 받아야 해서 테스트에서는 바로 끝나는 가짜로
vi.mock('../lib/card/render', () => ({
  loadAssets: vi.fn(() => Promise.resolve({})),
  randomBackground: vi.fn(() => Promise.resolve({ map: 'erangel', sx: 1, sy: 2 })),
  backgroundImage: vi.fn(() => Promise.resolve(document.createElement('canvas'))),
  tierGlow: vi.fn(() => '#FFD02F'),
  play: vi.fn(() => () => undefined),
  snapshot: vi.fn(() => ({ toDataURL: () => 'data:image/png;base64,AAAA' })),
}));

let location = { pathname: '', search: '' };
function LocationSpy() {
  const l = useLocation();
  location = { pathname: l.pathname, search: l.search };
  return null;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <App />
        <LocationSpy />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function json(status: number, body: unknown) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

/** 주소별 가짜 서버 응답 */
function serve(routes: Record<string, () => Promise<Response>>) {
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = String(input);
    const key = Object.keys(routes).find((k) => url.startsWith(k));
    return key ? routes[key]!() : json(404, { message: '없음' });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const saved = (nickname: string) => ({ platform: 'steam' as const, nickname, label: '스팀' });

beforeEach(() => {
  localStorage.clear();
  usePlayerLists.getState().reload();
  window.scrollTo = vi.fn();
});
afterEach(() => vi.unstubAllGlobals());

describe('첫 화면', () => {
  it('닉네임을 넣고 분석하기를 누르면 빈칸을 지우고 전적 화면으로 넘어간다', async () => {
    serve({ '/api/players/kakao/': () => json(200, page({ nickname: 'Sample-01' })) });
    renderAt('/');
    fireEvent.click(screen.getByLabelText('카카오'));
    fireEvent.change(screen.getByLabelText('배그 닉네임'), { target: { value: '  Sample-01 ' } });
    fireEvent.click(screen.getByRole('button', { name: '분석하기' }));
    expect(location).toEqual({ pathname: '/search', search: '?platform=kakao&nickname=Sample-01' });
    expect(await screen.findByRole('status')).toHaveTextContent('전적을 불러오는 중');
  });

  it('빈칸만 넣으면 안내하고 넘어가지 않는다', () => {
    renderAt('/');
    fireEvent.change(screen.getByLabelText('배그 닉네임'), { target: { value: '   ' } });
    fireEvent.submit(screen.getByRole('button', { name: '분석하기' }).closest('form')!);
    expect(screen.getByRole('alert')).toHaveTextContent('닉네임을 입력해 주세요.');
    expect(location.pathname).toBe('/');
  });

  it('서버가 돌려보낸 ?error= 안내를 보여준다', () => {
    renderAt('/?error=platform');
    expect(screen.getByRole('alert')).toHaveTextContent('스팀이나 카카오 중 하나를 골라 주세요.');
  });

  it('즐겨찾기·최근 검색을 보여주고, 즐겨찾기는 지운 뒤 되돌릴 수 있다', () => {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify([saved('Fav-1'), saved('Fav-2')]));
    localStorage.setItem(RECENT_KEY, JSON.stringify([saved('Recent-1')]));
    usePlayerLists.getState().reload();
    renderAt('/');

    expect(screen.getByRole('link', { name: /Recent-1/ })).toHaveAttribute('href', '/search?platform=steam&nickname=Recent-1');
    fireEvent.click(screen.getByRole('button', { name: 'Fav-1 지우기' }));
    expect(screen.queryByRole('link', { name: /Fav-1/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '되돌리기' }));
    // 원래 자리(맨 앞)로
    const favs = JSON.parse(localStorage.getItem(FAVORITES_KEY)!) as Array<{ nickname: string }>;
    expect(favs.map((p) => p.nickname)).toEqual(['Fav-1', 'Fav-2']);

    fireEvent.click(screen.getByRole('button', { name: 'Recent-1 지우기' }));
    expect(screen.getByText('아직 검색한 플레이어가 없어요.')).toBeInTheDocument();
  });

  it('다크/라이트 버튼은 모드를 바꾸고 저장한다', () => {
    document.documentElement.dataset.theme = 'dark';
    renderAt('/');
    fireEvent.click(screen.getByRole('button', { name: '라이트 모드로 바꾸기' }));
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(localStorage.getItem('pubgg.theme')).toBe('light');
    expect(screen.getByRole('button', { name: '다크 모드로 바꾸기' })).toBeInTheDocument();
  });
});

describe('상단 바', () => {
  it('지금 보는 플레이어로 채워져 있고, 비우면 즐겨찾기·최근 검색이 펼쳐진다', async () => {
    localStorage.setItem(RECENT_KEY, JSON.stringify([saved('Recent-1')]));
    usePlayerLists.getState().reload();
    serve({ '/api/players/steam/': () => json(200, page()) });
    renderAt('/search?platform=steam&nickname=Sample-01');

    const input = screen.getByLabelText('배그 닉네임');
    expect(input).toHaveValue('Sample-01');
    const banner = screen.getByRole('banner');
    expect(within(banner).queryByRole('link', { name: /Recent-1/ })).not.toBeInTheDocument();

    fireEvent.change(input, { target: { value: '' } });
    fireEvent.click(within(banner).getByRole('link', { name: /Recent-1/ }));
    expect(location.search).toBe('?platform=steam&nickname=Recent-1');
    // 다른 플레이어로 넘어가면 검색창도 그 닉네임으로
    await waitFor(() => expect(screen.getByLabelText('배그 닉네임')).toHaveValue('Recent-1'));
  });
});

describe('공유 카드 꾸미기', () => {
  it('카드 값을 받아 그리고, 고른 성향을 주소에 붙이고, 공유 링크를 만들어 복사한다', async () => {
    const writeText = vi.fn(() => Promise.resolve());
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
    const fetchMock = serve({
      '/api/card/steam/sample-01': () =>
        json(200, { nickname: 'Sample-01', platform: { shard: 'steam', label: '스팀' }, card: shareCard }),
      '/api/share': () => json(200, { id: 'abc', link: '/s/abc' }),
    });
    renderAt('/card?platform=steam&nickname=sample-01&mic=o');

    expect(await screen.findByRole('heading', { name: '공유 카드' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '← 전적으로 돌아가기' })).toHaveAttribute(
      'href',
      '/search?platform=steam&nickname=Sample-01',
    );
    expect(screen.getByLabelText('마이크 O')).toBeChecked();

    fireEvent.click(screen.getByLabelText('IGL'));
    fireEvent.click(screen.getByLabelText('듀오 구함'));
    await waitFor(() => expect(location.search).toBe('?platform=steam&nickname=Sample-01&mic=o&find=duo&role=igl'));

    const shareButton = screen.getByRole('button', { name: '공유 링크 만들기' });
    await waitFor(() => expect(shareButton).toBeEnabled());
    await act(async () => {
      fireEvent.click(shareButton);
    });
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/s/abc`));
    const call = fetchMock.mock.calls.find(([url]) => String(url) === '/api/share') as unknown as [string, RequestInit];
    expect(JSON.parse(String(call[1].body))).toMatchObject({
      platform: 'steam',
      nickname: 'Sample-01',
      choices: { mic: 'o', find: 'duo', role: 'igl' },
      background: { map: 'erangel', sx: 1, sy: 2 },
    });

    // 같은 모습으로 다시 누르면 새로 만들지 않는다
    await act(async () => {
      fireEvent.click(shareButton);
    });
    expect(fetchMock.mock.calls.filter(([url]) => String(url) === '/api/share')).toHaveLength(1);
  });

  it('기록이 없거나 플레이어가 없으면 안내만', async () => {
    serve({
      '/api/card/steam/nobody': () => json(404, { code: 'PLAYER_NOT_FOUND', message: '플레이어를 찾지 못했어요.' }),
    });
    renderAt('/card?platform=steam&nickname=nobody');
    expect(await screen.findByText('플레이어를 찾지 못했어요.')).toBeInTheDocument();
  });

  it('주소가 이상하면 첫 화면으로', () => {
    renderAt('/card?platform=xbox&nickname=abc');
    expect(location.pathname).toBe('/');
  });
});
