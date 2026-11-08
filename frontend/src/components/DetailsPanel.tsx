import type { Card } from '../api/types';
import { ZoneLuckBars, ZoneMap } from './ZonePanel';

/** 상세 분석 패널: 자기장 / 주무기·주 투척 / 파밍. 카드를 만든 경기(경쟁전, 없으면 일반) 기준 */
export function DetailsPanel({ card }: { card: Card }) {
  const f = card.farming;
  const hasBag = f !== null && f.phase3Matches > 0;
  const hasFarmStyle = f !== null && f.farmStyle !== null;

  return (
    <section className="details">
      <article className="detail">
        <h2 className="detail__title">
          자기장 <span>3페이즈부터</span>
        </h2>
        {card.zone ? (
          <ZoneMap zone={card.zone} />
        ) : (
          <p className="detail__empty">3페이즈 자기장이 줄어들 때까지 살아남은 기록이 없어요.</p>
        )}
        {card.zoneLuck && <ZoneLuckBars luck={card.zoneLuck} />}
      </article>

      <article className="detail">
        <h2 className="detail__title">주무기</h2>
        {card.mainWeapons.length > 0 ? (
          <ul className="weapons">
            {card.mainWeapons.map((w) => (
              <li className="weapon" key={w.name}>
                <span className="weapon__name">{w.name}</span>
                <span className="weapon__type">{w.typeLabel}</span>
                <span className="weapon__share">{w.sharePercent}%</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="detail__empty">주무기 기록을 불러오지 못했어요.</p>
        )}
        {card.mainThrowable ? (
          <ul className="weapons weapons--throw">
            <li className="weapon">
              <span className="weapon__name">{card.mainThrowable.name}</span>
              <span className="weapon__type">주 투척</span>
              <span className="weapon__share">판당 {card.mainThrowable.perMatch.toFixed(1)}번</span>
            </li>
          </ul>
        ) : (
          <p className="detail__sub">투척을 거의 쓰지 않았어요.</p>
        )}
      </article>

      <article className="detail detail--wide">
        <h2 className="detail__title">
          파밍 <span>3페이즈 시작 때 가방</span>
        </h2>
        {hasFarmStyle && (
          <p className="farming__style">
            <b>{f.farmStyle}</b>
            <span>
              {f.bigCityFarms}/{f.farmedMatches}판 대도시에서 파밍
            </span>
          </p>
        )}
        {hasBag && (
          <>
            <dl className="farming__bag">
              <div>
                <dt>힐템</dt>
                <dd>
                  <span>{f.avgHeals.toFixed(1)}</span>
                  <small>개</small>
                </dd>
              </div>
              <div>
                <dt>투척</dt>
                <dd>
                  <span>{f.avgThrowables.toFixed(1)}</span>
                  <small>개</small>
                </dd>
              </div>
              <div>
                <dt>주운 총알</dt>
                <dd>
                  <span>{f.avgAmmo}</span>
                  <small>발</small>
                </dd>
              </div>
            </dl>
            <p className="detail__sub">살아남은 {f.phase3Matches}경기 평균</p>
          </>
        )}
        {!hasFarmStyle && !hasBag && <p className="detail__empty">파밍 기록이 없어요.</p>}
      </article>

      {card.analyzedMatches < card.matches && (
        <p className="details__note">
          자기장·주무기·파밍은 상세 기록을 불러온 {card.analyzedMatches}경기 기준이에요.
        </p>
      )}
    </section>
  );
}
