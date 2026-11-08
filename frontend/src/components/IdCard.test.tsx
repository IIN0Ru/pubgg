import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { IdCard } from './IdCard';
import { page, shareCard } from '../test/fixtures';
import { drawProfileCard } from '../lib/cardRenderer';

vi.mock('../lib/cardRenderer', () => ({ drawProfileCard: vi.fn(() => Promise.resolve(() => undefined)) }));

const noop = () => undefined;
const season = {
  gameMode: 'squad-fpp',
  modeLabel: '스쿼드 FPP',
  tier: 'Platinum',
  subTier: '3',
  tierLabel: '플래티넘 3',
  placed: true,
  rankPoint: 2450,
  rounds: 120,
  kda: 2.76,
  avgDamage: 380,
  avgRank: 5.2,
  top10Percent: 41,
  smallSample: false,
};

describe('IdCard', () => {
  it('카드가 있으면 캔버스를 그리고, 카드 아래에 플랫폼과 RP 를 보여준다', () => {
    render(<IdCard player={page({ cardStatus: 'ready', shareCard, season })} refreshing={false} refreshMessage={null} onRefresh={noop} />);
    expect(screen.getByRole('img', { name: 'N0-RU 플레이어 카드' })).toBeInTheDocument();
    expect(drawProfileCard).toHaveBeenCalled();
    expect(screen.getByText('스팀')).toBeInTheDocument();
    expect(screen.getByText('2,450 RP')).toBeInTheDocument();
    // 카드 그림과 아래 버튼, 두 곳 모두 공유 카드 꾸미기 화면으로 간다
    const links = screen.getAllByRole('link', { name: '공유 카드 만들기' });
    expect(links).toHaveLength(2);
    links.forEach((l) => expect(l).toHaveAttribute('href', '/card?platform=steam&nickname=N0-RU'));
  });

  it('카드 계산 중이면 닉네임·티어와 안내를 보여준다', () => {
    render(<IdCard player={page({ cardStatus: 'pending', season })} refreshing={false} refreshMessage={null} onRefresh={noop} />);
    expect(screen.getByRole('heading', { name: 'N0-RU' })).toBeInTheDocument();
    expect(screen.getByText('플래티넘 3')).toBeInTheDocument();
    expect(screen.getByText('이번 시즌 스쿼드 FPP 120판')).toBeInTheDocument();
    expect(screen.getByText(/플레이 카드를 분석하는 중이에요/)).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('만들 기록이 없으면 그 이유를 알려준다', () => {
    render(<IdCard player={page({ cardStatus: 'none' })} refreshing={false} refreshMessage={null} onRefresh={noop} />);
    expect(screen.getByText(/플레이 카드를 만들지 못했어요/)).toBeInTheDocument();
  });

  it('갱신 가능하면 버튼을 눌러 갱신한다', () => {
    const onRefresh = vi.fn();
    render(<IdCard player={page({ refreshAvailableInSec: 0 })} refreshing={false} refreshMessage={null} onRefresh={onRefresh} />);
    fireEvent.click(screen.getByRole('button', { name: '전적 갱신' }));
    expect(onRefresh).toHaveBeenCalledOnce();
  });

  it('갱신 대기 중이면 버튼이 막히고 남은 시간을 알려준다', () => {
    render(<IdCard player={page({ refreshAvailableInSec: 95 })} refreshing={false} refreshMessage={null} onRefresh={noop} />);
    const button = screen.getByRole('button', { name: '전적 갱신' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('title', '1:35 뒤에 다시 갱신할 수 있어요');
  });

  it('갱신 중이거나 안내 문구가 있으면 보여준다', () => {
    render(<IdCard player={page()} refreshing refreshMessage="방금 갱신했어요." onRefresh={noop} />);
    expect(screen.getByText('· 갱신 중…')).toBeInTheDocument();
    expect(screen.getByText('방금 갱신했어요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '전적 갱신' })).toBeDisabled();
  });
});
