package gg.pub.pubgapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET /shards/{platform}/players?filter[playerNames]=닉네임 의 응답.
 *
 * 실제 응답 모양 (필요한 부분만):
 * {
 *   "data": [{
 *     "id": "account.abc123",
 *     "attributes": { "name": "N0-RU", "shardId": "steam" },
 *     "relationships": {
 *       "matches": { "data": [ { "type": "match", "id": "경기ID" }, ... ] }
 *     }
 *   }]
 * }
 *
 * JSON 안에 있는 필드 중 여기에 없는 것은 무시한다 (ignoreUnknown).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerResponse(List<Player> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(String id, Attributes attributes, Relationships relationships) {

        /** 최근 경기 ID 목록 (최신순). 경기가 없으면 빈 목록. */
        public List<String> matchIds() {
            if (relationships == null || relationships.matches() == null
                    || relationships.matches().data() == null) {
                return List.of();
            }
            return relationships.matches().data().stream()
                    .map(Ref::id)
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(String name, String shardId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relationships(RefList matches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefList(List<Ref> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ref(String type, String id) {
    }
}
