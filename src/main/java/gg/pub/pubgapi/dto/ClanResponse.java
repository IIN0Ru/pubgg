package gg.pub.pubgapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /shards/{platform}/clans/{clanId} 의 응답.
 *
 * {
 *   "data": {
 *     "type": "clan", "id": "clan.abc",
 *     "attributes": { "clanName": "헬멧연구소", "clanTag": "HLMT", "clanLevel": 5, "clanMemberCount": 30 }
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClanResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String id, Attributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(String clanName, String clanTag) {
    }
}
