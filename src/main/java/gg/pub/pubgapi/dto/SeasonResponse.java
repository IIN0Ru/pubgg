package gg.pub.pubgapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Optional;

/**
 * GET /shards/{platform}/seasons 의 응답. 지금까지의 모든 시즌 목록.
 *
 * {
 *   "data": [
 *     { "type": "season", "id": "division.bro.official.pc-2018-36",
 *       "attributes": { "isCurrentSeason": true, "isOffseason": false } },
 *     ...
 *   ]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeasonResponse(List<Season> data) {

    /** isCurrentSeason 이 true 인 시즌 */
    public Optional<String> currentSeasonId() {
        if (data == null) {
            return Optional.empty();
        }
        return data.stream()
                .filter(s -> s.attributes() != null && s.attributes().isCurrentSeason())
                .map(Season::id)
                .findFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(String id, Attributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(boolean isCurrentSeason, boolean isOffseason) {
    }
}
