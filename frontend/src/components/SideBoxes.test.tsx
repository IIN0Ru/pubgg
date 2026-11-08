import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CompareBox, TeammatesBox } from './SideBoxes';
import { card, season } from '../test/fixtures';

describe('CompareBox', () => {
  const rows = [
    { label: 'KDA', recent: '3.25', season: '2.76', trend: 1 as const },
    { label: '평균 등수', recent: '#6.0', season: '#5.2', trend: -1 as const },
    { label: '평균 딜량', recent: '380', season: '380', trend: 0 as const },
  ];

  it('최근·시즌 판 수와 값을 보여주고, 화살표는 색뿐 아니라 글자로도 알려준다', () => {
    const { container } = render(<CompareBox rows={rows} card={card()} season={season} />);
    expect(screen.getByRole('columnheader', { name: '최근 10판' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '시즌 120판' })).toBeInTheDocument();
    const kda = screen.getByRole('row', { name: /KDA/ });
    expect(within(kda).getByText('시즌보다 높음')).toHaveClass('sr-only');
    expect(container.querySelectorAll('.compare__arrow--up')).toHaveLength(1);
    expect(container.querySelectorAll('.compare__arrow')).toHaveLength(2); // 같음은 화살표 없음
    expect(screen.getByText('시즌과 같음')).toBeInTheDocument();
  });

  it('시즌 판 수가 적으면 주의 문구', () => {
    const { rerender } = render(<CompareBox rows={rows} card={card()} season={{ ...season, smallSample: true }} />);
    expect(screen.getByText(/비교가 정확하지 않을 수 있어요/)).toBeInTheDocument();
    rerender(<CompareBox rows={rows} card={card()} season={season} />);
    expect(screen.queryByText(/비교가 정확하지 않을 수 있어요/)).not.toBeInTheDocument();
  });
});

describe('TeammatesBox', () => {
  it('같이 한 팀원의 경기 수·평균 등수·치킨을 보여주고 전적으로 이어진다', () => {
    render(
      <TeammatesBox
        shard="steam"
        teammates={[
          { name: 'Duo', games: 12, wins: 2, avgRank: 4.3 },
          { name: 'Trio', games: 3, wins: 0, avgRank: 9 },
        ]}
      />,
    );
    expect(screen.getByRole('link', { name: 'Duo' })).toHaveAttribute('href', '/search?platform=steam&nickname=Duo');
    expect(screen.getByText('12경기')).toBeInTheDocument();
    expect(screen.getByText('평균 #4.3')).toBeInTheDocument();
    expect(screen.getByText('치킨 2')).toBeInTheDocument();
    expect(screen.getAllByText(/^치킨/)).toHaveLength(1); // 치킨 0은 표시하지 않음
  });

  it('없으면 안내한다', () => {
    render(<TeammatesBox shard="steam" teammates={[]} />);
    expect(screen.getByText('2판 이상 같이 한 팀원이 없어요.')).toBeInTheDocument();
  });
});
