import type { ApiErrorBody, ApiErrorCode, CardPage, Match, PlayerPage, RefreshResult, ShareRequest, ShareResult, Shard } from './types';
import type { ReplayData } from '../lib/replay/types';

/**
 * 서버가 돌려준 실패. 화면은 code 로 안내를 나눈다.
 *  - PLAYER_NOT_FOUND: 닉네임·플랫폼을 다시 확인하라고 안내
 *  - RATE_LIMITED: retryAfterSec 초 뒤에 다시 시도하라고 안내
 *  - UPSTREAM_ERROR / NETWORK: 잠시 뒤 다시 시도
 */
export class ApiError extends Error {
  constructor(
    readonly code: ApiErrorCode | 'NETWORK',
    message: string,
    readonly status: number,
    readonly retryAfterSec?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function isErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiErrorBody).code === 'string' &&
    typeof (value as ApiErrorBody).message === 'string'
  );
}

/** 전적 API 가 아닌 주소(공유·리플레이)는 { message } 만 준다. 상태 코드로 종류를 나눈다 */
function hasMessage(value: unknown): value is { message: string } {
  return typeof value === 'object' && value !== null && typeof (value as { message?: unknown }).message === 'string';
}

function codeFromStatus(status: number): ApiErrorCode {
  if (status === 400) return 'BAD_REQUEST';
  if (status === 429) return 'RATE_LIMITED';
  return 'UPSTREAM_ERROR';
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, init);
  } catch {
    throw new ApiError('NETWORK', '서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.', 0);
  }

  const body: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    if (isErrorBody(body)) {
      throw new ApiError(body.code, body.message, response.status, body.retryAfterSec);
    }
    if (hasMessage(body)) {
      throw new ApiError(codeFromStatus(response.status), body.message, response.status);
    }
    // 서버가 우리 형식이 아닌 응답을 줬다 (예: 배포 중이거나 프록시 오류)
    throw new ApiError('UPSTREAM_ERROR', '서버에서 오류가 났어요. 잠시 뒤에 다시 시도해 주세요.', response.status);
  }
  return body as T;
}

function playerPath(shard: Shard, nickname: string): string {
  // 닉네임에 한글·특수문자가 있어도 주소로 안전하게 보낸다
  return `/api/players/${shard}/${encodeURIComponent(nickname)}`;
}

export function fetchPlayer(shard: Shard, nickname: string): Promise<PlayerPage> {
  return request<PlayerPage>(playerPath(shard, nickname));
}

export function refreshPlayer(shard: Shard, nickname: string): Promise<RefreshResult> {
  return request<RefreshResult>(`${playerPath(shard, nickname)}/refresh`, { method: 'POST' });
}

/** 공유 카드 꾸미기 화면 값 (카드 계산이 끝날 때까지 기다렸다가 준다) */
export function fetchCard(shard: Shard, nickname: string): Promise<CardPage> {
  return request<CardPage>(`/api/card/${shard}/${encodeURIComponent(nickname)}`);
}

/** 공유 링크 만들기 (같은 접속 주소에서 10분에 10번까지) */
export function createShare(body: ShareRequest): Promise<ShareResult> {
  return request<ShareResult>('/api/share', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

function replayQuery(shard: Shard, matchId: string, accountId: string): string {
  return new URLSearchParams({ platform: shard, match: matchId, player: accountId }).toString();
}

/** 리플레이 머리 줄 (맵·등수·킬…). 경기 기록만 읽어서 빠르다 */
export function fetchReplaySummary(shard: Shard, matchId: string, accountId: string): Promise<Match> {
  return request<Match>(`/api/replay/summary?${replayQuery(shard, matchId, accountId)}`);
}

/** 2D 리플레이 데이터. 처음 여는 경기는 텔레메트리를 읽느라 몇 초 걸린다 */
export function fetchReplay(shard: Shard, matchId: string, accountId: string): Promise<ReplayData> {
  return request<ReplayData>(`/api/replay?${replayQuery(shard, matchId, accountId)}`);
}
