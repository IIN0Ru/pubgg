package gg.pub.pubgapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * GET /shards/{platform}/matches/{matchId} 의 응답.
 *
 * 실제 응답 모양 (필요한 부분만):
 * {
 *   "data": {
 *     "id": "경기ID",
 *     "attributes": { "createdAt": "2026-10-19T12:00:00Z", "gameMode": "squad-fpp",
 *                     "mapName": "Baltic_Main", "matchType": "official", "duration": 1800 }
 *   },
 *   "included": [
 *     { "type": "participant", "attributes": { "stats": { "name": ..., "kills": ..., ... } } },
 *     { "type": "roster",      "attributes": { "stats": { "rank": 1, "teamId": 3 } } },
 *     { "type": "asset",       "attributes": { "URL": "텔레메트리 주소" } }
 *   ]
 * }
 *
 * included 에는 종류가 다른 항목이 섞여 있어서 type 으로 구분한다.
 *  - participant: 경기에 나온 플레이어 한 명의 기록
 *  - roster: 한 팀 (팀 수 = roster 개수). relationships.participants 에 팀원 participant ID 들
 *  - asset: 텔레메트리 파일 주소 (주무기·투척 계산, 나중에 2D 리플레이에 사용)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchResponse(Match data, List<Included> included) {

    /** 이 경기에 참가한 팀 수 */
    public int teamCount() {
        return (int) safeIncluded().stream()
                .filter(i -> "roster".equals(i.type()))
                .count();
    }

    /** 경쟁전인지 */
    public boolean isCompetitive() {
        return data != null && data.attributes() != null
                && "competitive".equals(data.attributes().matchType());
    }

    /** FPP 경기인지 (gameMode 가 "-fpp" 로 끝남) */
    public boolean isFpp() {
        return data != null && data.attributes() != null
                && data.attributes().gameMode() != null
                && data.attributes().gameMode().endsWith("-fpp");
    }

    /** 텔레메트리 파일 주소 (asset 항목의 URL). 없으면 빈 값 */
    public Optional<String> telemetryUrl() {
        return safeIncluded().stream()
                .filter(i -> "asset".equals(i.type()))
                .map(Included::attributes)
                .filter(a -> a != null && a.url() != null)
                .map(IncludedAttributes::url)
                .findFirst();
    }

    /**
     * 나와 같은 팀(roster)인 팀원들의 기록 (나는 빼고).
     * 순서: 내 participant 를 찾고 → 그 participant 가 들어 있는 roster 를 찾고 → 그 roster 의 다른 participant 들
     */
    public List<Stats> teammatesOf(String accountId) {
        Map<String, Stats> statsById = new HashMap<>();
        String myParticipantId = null;
        for (Included i : safeIncluded()) {
            if ("participant".equals(i.type()) && i.attributes() != null && i.attributes().stats() != null) {
                statsById.put(i.id(), i.attributes().stats());
                if (accountId.equals(i.attributes().stats().playerId())) {
                    myParticipantId = i.id();
                }
            }
        }
        if (myParticipantId == null) {
            return List.of();
        }
        for (Included i : safeIncluded()) {
            if (!"roster".equals(i.type()) || i.relationships() == null
                    || i.relationships().participants() == null
                    || i.relationships().participants().data() == null) {
                continue;
            }
            List<String> memberIds = i.relationships().participants().data().stream().map(Ref::id).toList();
            if (memberIds.contains(myParticipantId)) {
                String me = myParticipantId;
                return memberIds.stream()
                        .filter(id -> !id.equals(me))
                        .map(statsById::get)
                        .filter(Objects::nonNull)
                        .toList();
            }
        }
        return List.of();
    }

    /** accountId(account.xxx)로 이 경기에서의 내 기록을 찾는다 */
    public Optional<Stats> statsOf(String accountId) {
        return safeIncluded().stream()
                .filter(i -> "participant".equals(i.type()))
                .map(Included::attributes)
                .filter(a -> a != null && a.stats() != null)
                .map(IncludedAttributes::stats)
                .filter(s -> accountId.equals(s.playerId()))
                .findFirst();
    }

    private List<Included> safeIncluded() {
        return included == null ? List.of() : included;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(String id, MatchAttributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchAttributes(String createdAt,
                                  int duration,
                                  String gameMode,
                                  String mapName,
                                  String matchType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Included(String type, String id, IncludedAttributes attributes,
                           IncludedRelationships relationships) {   // roster 에만 있음
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncludedRelationships(RefList participants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefList(List<Ref> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ref(String type, String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncludedAttributes(Stats stats,
                                     @JsonProperty("URL") String url) {   // asset 에만 있음
    }

    /**
     * participant 의 경기 기록.
     * roster 항목에도 stats 가 있지만 모양이 달라서(rank, teamId) 여기 값들은 0 으로 채워진다.
     * 그래서 participant 인 것만 골라서 쓴다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(String name,
                        String playerId,
                        int kills,
                        @JsonProperty("DBNOs") int dbnos,   // 기절시킨 수
                        int assists,
                        int headshotKills,
                        double damageDealt,
                        double longestKill,
                        int revives,
                        int heals,
                        int boosts,
                        double walkDistance,
                        double rideDistance,
                        double timeSurvived,               // 초
                        int winPlace,                       // 등수
                        String deathType) {                 // alive, byplayer, byzone, suicide, logout
    }
}
