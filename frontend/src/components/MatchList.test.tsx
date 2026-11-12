import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { useMatchFilters } from '../store/matchFilters';
import { MatchList } from './MatchList';
import { match, player } from '../test/fixtures';

beforeEach(() => useMatchFilters.getState().reset());

function renderList(matches = [match()]) {
  render(<MatchList matches={matches} shard="steam" nickname="Sample-01" />);
}

describe('MatchList', () => {
  it('경기가 없으면 14일 안내를 보여준다', () => {
    renderList([]);
    expect(screen.getByText(/최근 14일 안에 한 경기가 없어요/)).toBeInTheDocument();
  });

  it('등수·팀 수·모드·맵·킬·딜량을 보여준다', () => {
    renderList();
    expect(screen.getByText('#1')).toBeInTheDocument();
    expect(screen.getByText('/ 25')).toBeInTheDocument();
    expect(screen.getByText('스쿼드 FPP')).toBeInTheDocument();
    expect(screen.getByText('에란겔')).toBeInTheDocument();
    const stats = within(screen.getByText('딜량', { selector: 'dt' }).closest('dl') as HTMLElement);
    expect(stats.getByText('412')).toBeInTheDocument();
    expect(stats.getByText('25분 0초')).toBeInTheDocument();
  });

  it('1등은 치킨 글자와 노란 띠 클래스, 2·3등은 각자의 띠를 쓴다', () => {
    renderList([
      match({ matchId: 'a', rank: 1 }),
      match({ matchId: 'b', rank: 2 }),
      match({ matchId: 'c', rank: 3 }),
      match({ matchId: 'd', rank: 7 }),
      match({ matchId: 'e', rank: 20 }),
    ]);
    const rows = screen.getAllByRole('listitem').filter((li) => li.classList.contains('match'));
    expect(rows.map((r) => r.className.replace('match', '').trim())).toEqual([
      'match--win',
      'match--second',
      'match--third',
      'match--top10',
      '',
    ]);
    expect(screen.getByText('치킨')).toBeInTheDocument();
    expect(screen.getAllByText('TOP 10')).toHaveLength(3);
  });

  it('펼치면 팀원 기록이 있고, 팀원 닉네임은 그 사람의 전적으로 이어진다', () => {
    renderList();
    const table = screen.getByRole('table', { name: '이 경기 팀원 기록', hidden: true });
    const link = within(table).getByRole('link', { name: 'Duo', hidden: true });
    expect(link).toHaveAttribute('href', '/search?platform=steam&nickname=Duo');
  });

  it('혼자 한 경기는 안내 문구', () => {
    renderList([match({ teammates: [] })]);
    expect(screen.getByText('혼자 한 경기예요.')).toBeInTheDocument();
  });

  it('리플레이 링크는 경쟁전에만 있고, 계정 ID 를 담는다', () => {
    renderList([match({ matchId: 'm1' }), match({ matchId: 'm2', replayAvailable: false, type: 'official' })]);
    const links = screen.getAllByRole('link', { name: '2D 리플레이 보기', hidden: true });
    expect(links).toHaveLength(1);
    const href = links[0]?.getAttribute('href') ?? '';
    expect(href).toContain('/replay?');
    expect(href).toContain('match=m1');
    expect(href).toContain('player=account.me');
  });

  it('세부 기록을 보여준다', () => {
    renderList([match({ me: player({ headshotKills: 4, longestKill: 212 }) })]);
    expect(screen.getByText('212m')).toBeInTheDocument();
    expect(screen.getByText('끝까지 생존', { selector: 'strong' })).toBeInTheDocument();
  });

  describe('필터·요약·더 보기', () => {
    const mixed = [
      match({ matchId: 'a', type: 'competitive', perspective: 'fpp', rank: 1 }),
      match({ matchId: 'b', type: 'competitive', perspective: 'tpp', rank: 5 }),
      match({ matchId: 'c', type: 'official', perspective: 'fpp', rank: 20 }),
    ];
    const rows = () => screen.getAllByRole('listitem').filter((li) => li.classList.contains('match'));

    it('처음엔 전체 경기와 요약을 보여준다', () => {
      renderList(mixed);
      expect(screen.getByText('최근 3경기')).toBeInTheDocument();
      expect(screen.getByText('3판 · 평균 #8.7 · 치킨 1 · TOP 10 67%')).toBeInTheDocument();
    });

    it('경기 종류 필터를 누르면 목록과 요약이 바뀐다', () => {
      renderList(mixed);
      fireEvent.click(within(screen.getByRole('group', { name: '경기 종류' })).getByRole('button', { name: '일반' }));
      expect(rows()).toHaveLength(1);
      expect(screen.getByText('최근 1경기')).toBeInTheDocument();
      expect(screen.getByText('1판 · 평균 #20.0 · 치킨 0 · TOP 10 0%')).toBeInTheDocument();
      expect(
        within(screen.getByRole('group', { name: '경기 종류' })).getByRole('button', { name: '일반' }),
      ).toHaveAttribute('aria-pressed', 'true');
    });

    it('종류와 시점 필터를 같이 걸 수 있다', () => {
      renderList(mixed);
      fireEvent.click(within(screen.getByRole('group', { name: '경기 종류' })).getByRole('button', { name: '경쟁' }));
      fireEvent.click(within(screen.getByRole('group', { name: '시점' })).getByRole('button', { name: 'TPP' }));
      expect(rows()).toHaveLength(1);
    });

    it('조건에 맞는 경기가 없으면 안내한다', () => {
      renderList([match({ type: 'competitive', perspective: 'fpp' })]);
      fireEvent.click(within(screen.getByRole('group', { name: '시점' })).getByRole('button', { name: 'TPP' }));
      expect(screen.getByText('이 조건에 맞는 경기가 없어요.')).toBeInTheDocument();
      expect(screen.queryByText(/평균 #/)).not.toBeInTheDocument();
    });

    it('20경기씩 보여주고 더 보기로 이어서 보여준다', () => {
      renderList(Array.from({ length: 30 }, (_, i) => match({ matchId: `m${i}`, rank: i + 1 })));
      expect(rows()).toHaveLength(20);
      fireEvent.click(screen.getByRole('button', { name: '더 보기 (10경기 남음)' }));
      expect(rows()).toHaveLength(30);
      expect(screen.queryByRole('button', { name: /더 보기/ })).not.toBeInTheDocument();
    });
  });
});
