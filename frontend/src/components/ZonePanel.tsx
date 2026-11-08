import type { Zone, ZoneLuck } from '../api/types';

/**
 * 자기장 운영. 인게임 미니맵 느낌: 격자, 자기장 밖 빗금, 흰 원 = 안전구역.
 * 흰 점 = 한 판, 노란 화살표 = 평균. 중심에서의 거리만 의미 있고 방향은 의미 없다.
 */
export function ZoneMap({ zone }: { zone: Zone }) {
  const games = zone.matchDots.length;
  return (
    <div className="zone">
      <svg
        className="zone__map"
        viewBox="0 0 220 220"
        role="img"
        aria-label={`자기장이 줄어들기 시작할 때 흰 원 중심에서 평균 ${zone.edgePercent}퍼센트 거리, ${games}판 기준`}
      >
        <defs>
          <pattern id="zone-hatch" width="12" height="12" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <line className="zone__hatch" x1="0" y1="0" x2="0" y2="12" />
          </pattern>
          <mask id="zone-outside">
            <rect width="220" height="220" fill="white" />
            <circle cx="110" cy="110" r="92" fill="black" />
          </mask>
          <clipPath id="zone-frame">
            <rect width="220" height="220" rx="10" />
          </clipPath>
        </defs>
        <g clipPath="url(#zone-frame)">
          <rect className="zone__ground" width="220" height="220" />
          <path
            className="zone__grid"
            d="M27.5 0V220M55 0V220M82.5 0V220M110 0V220M137.5 0V220M165 0V220M192.5 0V220M0 27.5H220M0 55H220M0 82.5H220M0 110H220M0 137.5H220M0 165H220M0 192.5H220"
          />
          <rect width="220" height="220" fill="url(#zone-hatch)" mask="url(#zone-outside)" />
        </g>
        <circle className="zone__safe" cx="110" cy="110" r="92" />
        {zone.matchDots.map((d, i) => (
          <circle key={i} className="zone__match" r="7" cx={d.x} cy={d.y} />
        ))}
        <g transform={`translate(${zone.marker.x},${zone.marker.y})`}>
          <circle className="zone__halo" r="20" />
          <path className="zone__me" d="M0,-15 L11,11 L0,5.5 L-11,11 Z" />
        </g>
        <rect className="zone__border" x="0.5" y="0.5" width="219" height="219" rx="10" />
      </svg>
      <div className="zone__side">
        <p className="zone__value">
          <b>{zone.edgePercent}%</b>
          <span className="zone__verdict">{zone.verdict}</span>
        </p>
        <p className="zone__caption">
          줄어들기 시작할 때 흰 원 중심에서 거리
          <br />
          0% 중심 · 100% 경계
        </p>
        <p className="zone__legend">흰 점 한 판 ({games}판) · 노란 화살표 평균 · 빗금 자기장 밖</p>
      </div>
    </div>
  );
}

/** 자기장 운: 새 흰 원이 뜬 직후 내 위치. 100 = 중심, 0 = 경계·밖. 막대 하나가 한 판 */
export function ZoneLuckBars({ luck }: { luck: ZoneLuck }) {
  return (
    <div className="luck">
      <b className="luck__value">{luck.percent}%</b>
      <div className="luck__text">
        <p className="luck__title">자기장 운 · {luck.verdict}</p>
        <p className="luck__caption">새 원이 떴을 때 중심에 얼마나 가까웠나</p>
      </div>
      <div className="luck__bars" role="img" aria-label={`판별 자기장 운: ${luck.matchPercents.join('%, ')}%`}>
        {luck.bars.map((h, i) => (
          <span key={i} style={{ '--h': `${h}%` } as React.CSSProperties} />
        ))}
      </div>
    </div>
  );
}
