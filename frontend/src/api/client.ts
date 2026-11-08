import type { ApiErrorBody, ApiErrorCode, PlayerPage, RefreshResult, Shard } from './types';

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
