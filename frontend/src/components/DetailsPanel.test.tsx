import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DetailsPanel } from './DetailsPanel';
import { card } from '../test/fixtures';

describe('DetailsPanel', () => {
  it('자기장: 평균 거리·판정·판 수를 보여주고, 미니맵에 점을 판마다 하나씩 그린다', () => {
    const { container } = render(<DetailsPanel card={card()} />);
    expect(screen.getByText('38%')).toBeInTheDocument();
    expect(screen.getByText('센터 운영')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /평균 38퍼센트 거리, 2판 기준/ })).toBeInTheDocument();
    expect(container.querySelectorAll('.zone__match')).toHaveLength(2);
    expect(screen.getByText(/흰 점 한 판 \(2판\)/)).toBeInTheDocument();
  });

  it('자기장 운: 값·판정과 판별 막대 높이를 보여준다', () => {
    const { container } = render(<DetailsPanel card={card()} />);
    expect(screen.getByText('55%')).toBeInTheDocument();
    expect(screen.getByText('자기장 운 · 보통')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '판별 자기장 운: 40%, 70%' })).toBeInTheDocument();
    const bars = container.querySelectorAll<HTMLElement>('.luck__bars span');
    expect([...bars].map((b) => b.style.getPropertyValue('--h'))).toEqual(['40%', '70%']);
  });

  it('3페이즈까지 못 간 기록이면 자기장 대신 안내', () => {
    render(<DetailsPanel card={card({ zone: null, zoneLuck: null })} />);
    expect(screen.getByText(/3페이즈 자기장이 줄어들 때까지 살아남은 기록이 없어요/)).toBeInTheDocument();
    expect(screen.queryByText('자기장 운 · 보통')).not.toBeInTheDocument();
  });

  it('주무기와 주 투척', () => {
    render(<DetailsPanel card={card()} />);
    expect(screen.getByText('M416')).toBeInTheDocument();
    expect(screen.getByText('48%')).toBeInTheDocument();
    expect(screen.getByText('수류탄')).toBeInTheDocument();
    expect(screen.getByText('판당 1.1번')).toBeInTheDocument();
  });

  it('무기·투척 기록이 없으면 각각 안내한다', () => {
    render(<DetailsPanel card={card({ mainWeapons: [], mainThrowable: null })} />);
    expect(screen.getByText('주무기 기록을 불러오지 못했어요.')).toBeInTheDocument();
    expect(screen.getByText('투척을 거의 쓰지 않았어요.')).toBeInTheDocument();
  });

  it('파밍: 성향과 3페이즈 가방', () => {
    render(<DetailsPanel card={card()} />);
    expect(screen.getByText('대도시파밍')).toBeInTheDocument();
    expect(screen.getByText('7/10판 대도시에서 파밍')).toBeInTheDocument();
    expect(screen.getByText('7.5')).toBeInTheDocument();
    expect(screen.getByText('214')).toBeInTheDocument();
    expect(screen.getByText('살아남은 8경기 평균')).toBeInTheDocument();
  });

  it('파밍 기록이 전혀 없으면 안내한다', () => {
    render(<DetailsPanel card={card({ farming: null })} />);
    expect(screen.getByText('파밍 기록이 없어요.')).toBeInTheDocument();
  });

  it('상세 기록을 일부 경기만 불러왔으면 그 경기 수를 알려준다', () => {
    const { rerender } = render(<DetailsPanel card={card({ analyzedMatches: 8 })} />);
    expect(screen.getByText(/상세 기록을 불러온 8경기 기준이에요/)).toBeInTheDocument();
    rerender(<DetailsPanel card={card({ analyzedMatches: 10 })} />);
    expect(screen.queryByText(/상세 기록을 불러온/)).not.toBeInTheDocument();
  });
});
