import type { Card, Comparison, Season, Shard, Teammate } from '../api/types';

const ARROW = { 1: '▲', 0: '', [-1]: '▼' } as const;
const TREND_TEXT = { 1: '시즌보다 높음', 0: '시즌과 같음', [-1]: '시즌보다 낮음' } as const;

/** 최근 경쟁전 vs 이번 시즌 (경쟁전 시즌 기록과 카드가 모두 있을 때만) */
export function CompareBox({ rows, card, season }: { rows: Comparison[]; card: Card; season: Season }) {
  return (
    <section className="sidebox">
      <h2 className="sidebox__title">
        최근 vs 이번 시즌 <span>경쟁전만</span>
      </h2>
      <table className="compare">
        <thead>
          <tr>
            <th scope="col" />
            <th scope="col">
              최근 <small>{card.matches}판</small>
            </th>
            <th scope="col">
              시즌 <small>{season.rounds}판</small>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label}>
              <th scope="row">{row.label}</th>
              <td>
                <span>{row.recent}</span>
                {row.trend !== 0 && (
                  <span className={`compare__arrow${row.trend > 0 ? ' compare__arrow--up' : ''}`} aria-hidden="true">
                    {ARROW[row.trend]}
                  </span>
                )}
                <span className="sr-only">{TREND_TEXT[row.trend]}</span>
              </td>
              <td>{row.season}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {season.smallSample && <p className="compare__note">시즌 판 수가 적어서 비교가 정확하지 않을 수 있어요.</p>}
    </section>
  );
}

/** 자주 같이 한 팀원 (불러온 최근 경기 중 2판 이상) */
export function TeammatesBox({ teammates, shard }: { teammates: Teammate[]; shard: Shard }) {
  return (
    <section className="sidebox">
      <h2 className="sidebox__title">
        자주 같이 한 팀원 <span>최근 30경기</span>
      </h2>
      {teammates.length > 0 ? (
        <ol className="mates">
          {teammates.map((t) => (
            <li className="mate" key={t.name}>
              <a
                className="mate__name"
                href={`/search?${new URLSearchParams({ platform: shard, nickname: t.name }).toString()}`}
              >
                {t.name}
              </a>
              <span className="mate__games">{t.games}경기</span>
              <span className="mate__meta">
                <span>평균 #{t.avgRank.toFixed(1)}</span>
                {t.wins > 0 && <span>치킨 {t.wins}</span>}
              </span>
            </li>
          ))}
        </ol>
      ) : (
        <p className="sidebox__empty">2판 이상 같이 한 팀원이 없어요.</p>
      )}
    </section>
  );
}
