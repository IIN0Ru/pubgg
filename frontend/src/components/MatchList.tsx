import type { Match, Shard } from '../api/types';
import { formatSummary, summarizeMatches } from '../lib/matchSummary';
import {
  filterMatches,
  useMatchFilters,
  type PerspectiveFilter,
  type TypeFilter,
} from '../store/matchFilters';
import { MatchItem } from './MatchItem';

interface Props {
  matches: Match[];
  shard: Shard;
  nickname: string;
}

const TYPE_TABS: { value: TypeFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'competitive', label: '경쟁' },
  { value: 'official', label: '일반' },
];

const PERSPECTIVE_TABS: { value: PerspectiveFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'fpp', label: 'FPP' },
  { value: 'tpp', label: 'TPP' },
];

/** 최근 경기 목록: 경기 종류·시점 필터, 필터에 걸린 경기 요약, 20경기씩 더 보기 */
export function MatchList({ matches, shard, nickname }: Props) {
  const { type, perspective, shown, setType, setPerspective, showMore } = useMatchFilters();

  if (matches.length === 0) {
    return (
      <section className="notice">
        <p className="notice__text">최근 14일 안에 한 경기가 없어요. PUBG API는 최근 14일 경기만 알려줘요.</p>
      </section>
    );
  }

  const hits = filterMatches(matches, type, perspective);
  const summary = summarizeMatches(hits);
  const left = hits.length - shown;

  return (
    <section className="recent">
      <div className="recent__head">
        <h2 className="section-title">최근 {hits.length}경기</h2>
        <div className="filters" role="group" aria-label="경기 필터">
          <Tabs label="경기 종류" tabs={TYPE_TABS} value={type} onChange={setType} />
          <Tabs label="시점" tabs={PERSPECTIVE_TABS} value={perspective} onChange={setPerspective} />
        </div>
      </div>

      <p className="recent__summary" aria-live="polite">
        {summary ? formatSummary(summary) : ''}
      </p>

      <ol className="matches">
        {hits.slice(0, shown).map((m) => (
          <MatchItem key={m.matchId} match={m} shard={shard} nickname={nickname} />
        ))}
      </ol>

      {hits.length === 0 && <p className="recent__empty">이 조건에 맞는 경기가 없어요.</p>}
      {left > 0 && (
        <button type="button" className="more" onClick={showMore}>
          더 보기 ({left}경기 남음)
        </button>
      )}
    </section>
  );
}

function Tabs<T extends string>({
  label,
  tabs,
  value,
  onChange,
}: {
  label: string;
  tabs: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="tabs" role="group" aria-label={label}>
      {tabs.map((t) => (
        <button
          key={t.value}
          type="button"
          className={`tab${t.value === value ? ' is-active' : ''}`}
          aria-pressed={t.value === value}
          onClick={() => onChange(t.value)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
