// 서버는 원시값만 주고, 화면에 보일 글자는 여기서 만든다.

/** "방금 전", "3분 전", "2시간 전", "1일 전" (이전 Thymeleaf 화면과 같은 기준) */
export function formatRelativeTime(iso: string, now: number = Date.now()): string {
  const diffMs = now - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return '방금 전';
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  return `${Math.floor(hours / 24)}일 전`;
}

/** 2450 -> "2,450 RP" */
export function formatRankPoint(rankPoint: number): string {
  return `${rankPoint.toLocaleString('en-US')} RP`;
}

/** 3.4167 -> "3.42" */
export function formatKda(kda: number): string {
  return kda.toFixed(2);
}

/** 1등은 치킨, 10등 안은 TOP 10. 글자로도 구분한다 (색만으로 구분하지 않기 위해) */
export function resultBadge(rank: number): '치킨' | 'TOP 10' | '' {
  if (rank === 1) return '치킨';
  return rank >= 2 && rank <= 10 ? 'TOP 10' : '';
}

/** 1500 -> "25분 0초" */
export function formatSurvived(seconds: number): string {
  return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`;
}

/** 갱신 대기 시간: 95 -> "1:35" */
export function formatCountdown(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** "10월 19일 21:34" (한국 시간). 마우스를 올렸을 때 보이는 정확한 시각 */
export function formatPlayedAt(iso: string): string {
  const parts = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(new Date(iso));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('month')}월 ${get('day')}일 ${get('hour')}:${get('minute')}`;
}

/** 줄 왼쪽 띠 색을 정하는 클래스: 치킨 / 2등 / 3등 / TOP 10 (main.css) */
export function matchRankClass(rank: number): string {
  if (rank === 1) return 'match--win';
  if (rank === 2) return 'match--second';
  if (rank === 3) return 'match--third';
  return rank >= 4 && rank <= 10 ? 'match--top10' : '';
}
