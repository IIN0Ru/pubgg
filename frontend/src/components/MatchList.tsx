import type { Match, Shard } from '../api/types';
import { MatchItem } from './MatchItem';

interface Props {
  matches: Match[];
  shard: Shard;
  nickname: string;
}

/** 최근 경기 목록. (필터·더 보기·요약은 3주차에 붙인다) */
export function MatchList({ matches, shard, nickname }: Props) {
  if (matches.length === 0) {
    return (
      <section className="notice">
        <p className="notice__text">최근 14일 안에 한 경기가 없어요. PUBG API는 최근 14일 경기만 알려줘요.</p>
      </section>
    );
  }
  return (
    <section className="recent">
      <div className="recent__head">
        <h2 className="section-title">최근 {matches.length}경기</h2>
      </div>
      <ol className="matches">
        {matches.map((m) => (
          <MatchItem key={m.matchId} match={m} shard={shard} nickname={nickname} />
        ))}
      </ol>
    </section>
  );
}
