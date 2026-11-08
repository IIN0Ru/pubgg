import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { ErrorScreen, errorKind } from './ErrorScreen';

afterEach(() => vi.useRealTimers());

describe('errorKind', () => {
  it('서버 code 를 화면 종류로 나눈다 (연결 오류는 서버 오류와 같은 화면)', () => {
    expect(errorKind(new ApiError('PLAYER_NOT_FOUND', '', 404))).toBe('not-found');
    expect(errorKind(new ApiError('RATE_LIMITED', '', 429, 60))).toBe('rate-limited');
    expect(errorKind(new ApiError('UPSTREAM_ERROR', '', 502))).toBe('upstream');
    expect(errorKind(new ApiError('NETWORK', '', 0))).toBe('upstream');
    expect(errorKind(new ApiError('BAD_REQUEST', '', 400))).toBe('bad-request');
  });
});

describe('ErrorScreen', () => {
  it('없는 플레이어: 확인 방법과 첫 화면 링크만 있고 다시 시도 버튼은 없다', () => {
    render(<ErrorScreen error={new ApiError('PLAYER_NOT_FOUND', "'x' 플레이어를 찾지 못했어요.", 404)} onRetry={vi.fn()} retrying={false} />);
    expect(screen.getByRole('heading', { name: '플레이어를 찾지 못했어요' })).toBeInTheDocument();
    expect(screen.getByText(/대소문자와 플랫폼/)).toBeInTheDocument();
    expect(screen.queryByText("'x' 플레이어를 찾지 못했어요.")).not.toBeInTheDocument(); // 제목과 같은 말은 반복하지 않는다
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '첫 화면으로' })).toHaveAttribute('href', '/');
  });

  it('요청 한도: 남은 초를 세고, 0 이 되면 다시 시도할 수 있다', () => {
    vi.useFakeTimers();
    const onRetry = vi.fn();
    render(<ErrorScreen error={new ApiError('RATE_LIMITED', '검색이 너무 많아요.', 429, 3)} onRetry={onRetry} retrying={false} />);
    const button = screen.getByRole('button', { name: '다시 시도' });
    expect(button).toBeDisabled();
    expect(screen.getByText('3초 뒤에 다시 시도할 수 있어요.')).toBeInTheDocument();

    act(() => void vi.advanceTimersByTime(1000));
    expect(screen.getByText('2초 뒤에 다시 시도할 수 있어요.')).toBeInTheDocument();

    act(() => void vi.advanceTimersByTime(2000));
    expect(screen.getByText('이제 다시 시도할 수 있어요.')).toBeInTheDocument();
    expect(button).toBeEnabled();
    fireEvent.click(button);
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('서버·연결 오류: 바로 다시 시도할 수 있고, 불러오는 중에는 누를 수 없다', () => {
    const onRetry = vi.fn();
    const { rerender } = render(<ErrorScreen error={new ApiError('UPSTREAM_ERROR', '서버에서 오류가 났어요.', 502)} onRetry={onRetry} retrying={false} />);
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(onRetry).toHaveBeenCalledTimes(1);

    rerender(<ErrorScreen error={new ApiError('UPSTREAM_ERROR', '서버에서 오류가 났어요.', 502)} onRetry={onRetry} retrying />);
    expect(screen.getByRole('button', { name: '불러오는 중…' })).toBeDisabled();
  });
});
