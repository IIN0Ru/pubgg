package gg.pub.pubgapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
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
 *  - roster: 한 팀 (팀 수 = roster 개수)
 *  - asset: 텔레메트리 파일 주소 (2D 리플레이 만들 때 사용 예정)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchResponse(Match data, List<Included> included) {

    /** 이 경기에 참가한 팀 수 */
    public int teamCount() {
        return (int) safeIncluded().stream()
                .filter(i -> "roster".equals(i.type()))
                .count();
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
    public record Included(String type, String id, IncludedAttributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncludedAttributes(Stats stats) {
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
                        int winPlace) {                     // 등수
    }
}
