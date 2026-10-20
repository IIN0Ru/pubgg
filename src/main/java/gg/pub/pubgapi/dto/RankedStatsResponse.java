package gg.pub.pubgapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * GET /shards/{platform}/players/{accountId}/seasons/{seasonId}/ranked 의 응답.
 * 한 시즌 경쟁전 전체 기록. 모드(squad, squad-fpp)별로 나뉘어 있다.
 *
 * {
 *   "data": {
 *     "type": "rankedplayerstats",
 *     "attributes": {
 *       "rankedGameModeStats": {
 *         "squad-fpp": {
 *           "currentTier": { "tier": "Platinum", "subTier": "3" },
 *           "currentRankPoint": 2450,
 *           "roundsPlayed": 120, "kills": 210, "deaths": 105, "assists": 80,
 *           "kda": 2.76, "damageDealt": 39000.5, "avgRank": 7.4,
 *           "top10Ratio": 0.61, "winRatio": 0.08, "wins": 10
 *         }
 *       }
 *     }
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RankedStatsResponse(Data data) {

    /** 모드 이름 → 기록. 기록이 없으면 빈 Map */
    public Map<String, ModeStats> modes() {
        if (data == null || data.attributes() == null || data.attributes().rankedGameModeStats() == null) {
            return Map.of();
        }
        return data.attributes().rankedGameModeStats();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(Attributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(Map<String, ModeStats> rankedGameModeStats) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModeStats(Tier currentTier,
                            int currentRankPoint,
                            int roundsPlayed,
                            int kills,
                            int deaths,
                            int assists,
                            double kda,
                            double damageDealt,   // 시즌 전체 합계
                            double avgRank,
                            double top10Ratio,    // 0 ~ 1
                            double winRatio,      // 0 ~ 1
                            int wins) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tier(String tier, String subTier) {
    }
}
