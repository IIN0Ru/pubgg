import { Link } from 'react-router';
import type { Match, MatchPlayer, Shard } from '../api/types';
import { formatPlayedAt, formatRelativeTime, formatSurvived, matchRankClass, resultBadge } from '../lib/format';
import { replayPath, searchPath } from '../lib/search';

interface Props {
  match: Match;
  shard: Shard;
  nickname: string;
}

/** 경기 한 줄. 누르면 펼쳐져서 세부 기록과 팀원 기록, 2D 리플레이 링크가 나온다 */
export function MatchItem({ match: m, shard, nickname }: Props) {
  const badge = resultBadge(m.rank);
  const replayHref = replayPath(shard, m.matchId, m.me.accountId, nickname);

  return (
    <li className={`match ${matchRankClass(m.rank)}`.trim()}>
      <details>
        <summary className="match__row">
          <div className="match__rank">
            <strong>#{m.rank}</strong>
            <span>/ {m.teams}</span>
          </div>

          <div className="match__info">
            <p className="match__mode">
              <span>{m.modeLabel}</span>
              <span>{m.mapLabel}</span>
              {m.typeLabel !== '' && <span className="match__type">{m.typeLabel}</span>}
              {badge !== '' && <span className={`match__badge${m.rank === 1 ? ' match__badge--win' : ''}`}>{badge}</span>}
            </p>
            <p className="match__date">
              <time dateTime={m.playedAt} title={formatPlayedAt(m.playedAt)}>
                {formatRelativeTime(m.playedAt)}
              </time>
            </p>
          </div>

          <dl className="match__stats">
            <div>
              <dt>킬</dt>
              <dd>{m.me.kills}</dd>
            </div>
            <div>
              <dt>기절</dt>
              <dd>{m.me.knocks}</dd>
            </div>
            <div>
              <dt>딜량</dt>
              <dd>{m.me.damage}</dd>
            </div>
            <div>
              <dt>생존</dt>
              <dd>{formatSurvived(m.me.survivedSeconds)}</dd>
            </div>
          </dl>
          <span className="match__chevron" aria-hidden="true" />
        </summary>

        <div className="match__more">
          <ul className="match__facts">
            <li>
              <span>헤드샷 킬</span>
              <strong>{m.me.headshotKills}</strong>
            </li>
            <li>
              <span>최장 킬</span>
              <strong>{m.me.longestKill}m</strong>
            </li>
            <li>
              <span>어시스트</span>
              <strong>{m.me.assists}</strong>
            </li>
            <li>
              <span>팀원 부활</span>
              <strong>{m.me.revives}</strong>
            </li>
            <li>
              <span>결과</span>
              <strong>{m.me.deathTypeLabel}</strong>
            </li>
          </ul>

          {m.teammates.length > 0 ? (
            <table className="team">
              <caption className="sr-only">이 경기 팀원 기록</caption>
              <thead>
                <tr>
                  <th scope="col">팀</th>
                  <th scope="col">킬</th>
                  <th scope="col">기절</th>
                  <th scope="col">딜량</th>
                  <th scope="col">생존</th>
                  <th scope="col">결과</th>
                </tr>
              </thead>
              <tbody>
                <TeamRow className="team__me" player={m.me} name={nickname} />
                {m.teammates.map((t) => (
                  <TeamRow
                    key={t.accountId}
                    player={t}
                    name={
                      <Link to={searchPath(shard, t.name)}>
                        {t.name}
                      </Link>
                    }
                  />
                ))}
              </tbody>
            </table>
          ) : (
            <p className="match__solo">혼자 한 경기예요.</p>
          )}

          {m.replayAvailable && (
            <Link className="match__replay" to={replayHref}>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M7 4.5v15l12-7.5z" fill="currentColor" />
              </svg>
              2D 리플레이 보기
            </Link>
          )}
        </div>
      </details>
    </li>
  );
}

function TeamRow({ player, name, className }: { player: MatchPlayer; name: React.ReactNode; className?: string }) {
  return (
    <tr className={className}>
      <th scope="row">{name}</th>
      <td>{player.kills}</td>
      <td>{player.knocks}</td>
      <td>{player.damage}</td>
      <td>{formatSurvived(player.survivedSeconds)}</td>
      <td>{player.deathTypeLabel}</td>
    </tr>
  );
}
