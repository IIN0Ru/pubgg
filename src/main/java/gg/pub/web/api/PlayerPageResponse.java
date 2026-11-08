package gg.pub.web.api;

import gg.pub.domain.Clan;
import gg.pub.domain.Farming;
import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.PlayCard;
import gg.pub.domain.PlayerMatches;
import gg.pub.domain.SeasonRank;
import gg.pub.domain.ShareCard;
import gg.pub.domain.Teammate;
import gg.pub.service.PlayerLookupService.Lookup;

import java.time.Instant;
import java.util.List;

/**
 * 전적 화면(React)이 한 번에 받는 JSON. 도메인 기록(PlayerMatches)을 화면용으로 옮겨 담은 것이다.
 *
 * 규칙
 *  - 값은 원시값(숫자, ISO 시각)으로 준다. "3분 전", "2,450 RP", "치킨" 같은 표시 글자는 프론트가 만든다.
 *  - 단, 서버 규칙으로 정해진 이름은 같이 준다 (티어 한글 이름, 맵·모드 이름, 자기장 판정 등).
 *  - 도메인 record 를 그대로 내보내지 않는다. 내부 모양이 바뀌어도 화면 계약은 안 바뀌게 하려고 따로 정의한다.
 *
 * 프론트의 타입 정의는 frontend/src/api/types.ts 에 같은 모양으로 있다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
 *
 * @param refreshAvailableInSec 다시 갱신할 수 있을 때까지 남은 초 (0 이면 지금 가능)
 * @param cardStatus            ready(카드 있음) / pending(계산 중, 같은 주소를 다시 부르면 됨) / none(만들 기록이 없음)
 * @param shareCard             A카드 캔버스(720x960)에 그릴 값. 카드가 없으면 null
 */
public record PlayerPageResponse(String nickname,
                             PlatformDto platform,
                             Instant updatedAt,
                             long refreshAvailableInSec,
                             String cardStatus,
                             SeasonDto season,
                             CardDto card,
                             ShareCard shareCard,
                             List<ComparisonDto> seasonComparison,
                             List<MatchDto> matches,
                             List<TeammateDto> teammates,
                             ClanDto clan) {

    public static final String READY = "ready";
    public static final String PENDING = "pending";
    public static final String NONE = "none";

    public static PlayerPageResponse from(Lookup lookup, long refreshAvailableInSec) {
        PlayerMatches r = lookup.result();
        String status = lookup.cardPending() ? PENDING : (r.hasCard() ? READY : NONE);
        return new PlayerPageResponse(
                r.nickname(),
                new PlatformDto(r.platform().getShard(), r.platform().getLabel()),
                lookup.updatedAt(),
                refreshAvailableInSec,
                status,
                r.season() == null ? null : SeasonDto.from(r.season()),
                r.card() == null ? null : CardDto.from(r.card()),
                ShareCard.from(r).orElse(null),
                r.seasonComparison().stream().map(ComparisonDto::from).toList(),
                r.matches().stream().map(MatchDto::from).toList(),
                r.teammates().stream().map(TeammateDto::from).toList(),
                r.clan() == null ? null : ClanDto.from(r.clan()));
    }

    public record PlatformDto(String shard, String label) {
    }

    /** 이번 시즌 경쟁전. tierLabel 은 "플래티넘 3" (배치 전이면 "배치 중") */
    public record SeasonDto(String gameMode, String modeLabel, String tier, String subTier, String tierLabel,
                            boolean placed, int rankPoint, int rounds, double kda, int avgDamage,
                            double avgRank, int top10Percent, boolean smallSample) {
        static SeasonDto from(SeasonRank s) {
            return new SeasonDto(s.gameMode(), s.modeLabel(), s.tier(), s.subTier(), s.tierLabel(),
                    s.isPlaced(), s.rankPoint(), s.rounds(), s.kda(), s.avgDamage(),
                    s.avgRank(), s.top10Percent(), s.isSmallSample());
        }
    }

    /** 최근 경기 vs 시즌 비교 한 줄. trend: 최근이 높으면 1, 낮으면 -1, 같으면 0 */
    public record ComparisonDto(String label, String recent, String season, int trend) {
        static ComparisonDto from(SeasonRank.Comparison c) {
            return new ComparisonDto(c.label(), c.recent(), c.season(), c.trend());
        }
    }

    /** 플레이 카드 (최근 경쟁전, 없으면 일반 경기). scope: competitive / normal */
    public record CardDto(String scope, int matches, int analyzedMatches, String perspective,
                          int kills, int assists, int deaths, double kda, int avgDamage,
                          List<String> styleTags,
                          List<WeaponDto> mainWeapons,
                          ThrowableDto mainThrowable, double avgThrowables,
                          ZoneDto zone, ZoneLuckDto zoneLuck, FarmingDto farming) {
        static CardDto from(PlayCard c) {
            return new CardDto(c.scope(), c.matches(), c.analyzedMatches(), c.perspective(),
                    c.kills(), c.assists(), c.deaths(), c.kda(), c.avgDamage(),
                    c.styleTags(),
                    c.mainWeapons().stream()
                            .map(w -> new WeaponDto(w.name(), w.typeLabel(), w.sharePercent()))
                            .toList(),
                    c.mainThrowable() == null ? null
                            : new ThrowableDto(c.mainThrowable().name(), c.mainThrowable().perMatch()),
                    c.avgThrowables(),
                    c.hasZoneGauge() ? ZoneDto.from(c) : null,
                    c.hasZoneLuck() ? ZoneLuckDto.from(c) : null,
                    c.farming() == null ? null : FarmingDto.from(c.farming()));
        }
    }

    public record WeaponDto(String name, String typeLabel, int sharePercent) {
    }

    public record ThrowableDto(String name, double perMatch) {
    }

    /**
     * 자기장 운영. edgePercent: 0 = 중심, 100 = 경계.
     * matchDots·marker 는 220x220 미니맵 위 좌표다 (안전구역 중심 110,110 · 반지름 92).
     */
    public record ZoneDto(int edgePercent, String verdict, List<Dot> matchDots, Dot marker) {
        static ZoneDto from(PlayCard c) {
            return new ZoneDto(c.zoneEdgePercent(), c.zoneVerdict(),
                    c.zoneMatchDots().stream().map(d -> new Dot(d.x(), d.y())).toList(),
                    new Dot(c.zoneMarker().x(), c.zoneMarker().y()));
        }
    }

    public record Dot(double x, double y) {
    }

    /** 자기장 운. percent: 100 = 중심, 0 = 경계·밖. bars 는 판마다 막대 높이(%) */
    public record ZoneLuckDto(int percent, String verdict, List<Integer> bars) {
        static ZoneLuckDto from(PlayCard c) {
            return new ZoneLuckDto(c.zoneLuckPercent(), c.zoneLuckVerdict(), c.zoneLuckBars());
        }
    }

    public record FarmingDto(String farmStyle, String mainCity, int bigCityFarms, int farmedMatches,
                             int phase3Matches, double avgHeals, double avgThrowables, int avgAmmo) {
        static FarmingDto from(Farming f) {
            return new FarmingDto(f.farmStyle(), f.mainCity(), f.bigCityFarms(), f.farmedMatches(),
                    f.phase3Matches(), f.avgHeals(), f.avgThrowables(), f.avgAmmo());
        }
    }

    /**
     * 경기 하나. type: competitive / official / other, perspective: fpp / tpp.
     * replayAvailable 은 2D 리플레이를 볼 수 있는 경기(경쟁전)인지.
     * rank 는 내 팀 등수, teams 는 참가 팀 수("#3 / 25").
     * rank 가 1 이면 치킨, 10 이하면 TOP 10 — 표시 글자는 프론트가 정한다.
     */
    public record MatchDto(String matchId, Instant playedAt, String type, String typeLabel, String perspective,
                           String gameMode, String modeLabel, String mapName, String mapLabel,
                           int rank, int teams, boolean replayAvailable, MatchPlayerDto me, List<MatchPlayerDto> teammates) {
        static MatchDto from(MatchRecord m) {
            return new MatchDto(m.matchId(), m.playedAt(), m.typeKey(), m.matchTypeLabel(), m.perspectiveKey(),
                    m.gameMode(), m.mode(), m.mapName(), m.map(),
                    m.rank(), m.teams(), m.isCompetitive(),
                    MatchPlayerDto.from(m.me()),
                    m.teammates().stream().map(MatchPlayerDto::from).toList());
        }
    }

    public record MatchPlayerDto(String name, String accountId, int kills, int knocks, int assists, int damage,
                                 int survivedSeconds, int headshotKills, int longestKill, int revives,
                                 String deathType, String deathTypeLabel) {
        static MatchPlayerDto from(MatchPlayer p) {
            return new MatchPlayerDto(p.name(), p.accountId(), p.kills(), p.knocks(), p.assists(), p.damage(),
                    p.survivedSeconds(), p.headshotKills(), p.longestKill(), p.revives(),
                    p.deathType(), p.deathTypeLabel());
        }
    }

    public record TeammateDto(String name, int games, int wins, double avgRank) {
        static TeammateDto from(Teammate t) {
            return new TeammateDto(t.name(), t.games(), t.wins(), t.avgRank());
        }
    }

    public record ClanDto(String name, String tag, String label) {
        static ClanDto from(Clan c) {
            return new ClanDto(c.name(), c.tag(), c.label());
        }
    }
}
