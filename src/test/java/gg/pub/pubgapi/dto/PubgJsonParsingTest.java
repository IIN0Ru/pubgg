package gg.pub.pubgapi.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PUBG API 응답 JSON 을 우리 DTO 로 제대로 읽어오는지 확인한다.
 * 예시 JSON 은 src/test/resources/pubg/ 에 있다 (실제 응답과 같은 모양, 값은 가짜).
 */
class PubgJsonParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    static <T> T read(ObjectMapper mapper, String path, Class<T> type) throws IOException {
        try (InputStream in = PubgJsonParsingTest.class.getResourceAsStream(path)) {
            return mapper.readValue(in, type);
        }
    }

    @Test
    void 플레이어_응답에서_계정ID와_경기목록을_읽는다() throws IOException {
        PlayerResponse response = read(mapper, "/pubg/player.json", PlayerResponse.class);

        PlayerResponse.Player player = response.data().get(0);
        assertThat(player.id()).isEqualTo("account.test0001");
        assertThat(player.attributes().name()).isEqualTo("N0-RU");
        assertThat(player.matchIds()).containsExactly("match-0001", "match-0002");
    }

    @Test
    void 경기_응답에서_내_기록과_팀수를_읽는다() throws IOException {
        MatchResponse match = read(mapper, "/pubg/match.json", MatchResponse.class);

        assertThat(match.data().attributes().gameMode()).isEqualTo("squad-fpp");
        assertThat(match.data().attributes().mapName()).isEqualTo("Baltic_Main");
        assertThat(match.teamCount()).isEqualTo(2);

        MatchResponse.Stats me = match.statsOf("account.test0001").orElseThrow();
        assertThat(me.kills()).isEqualTo(8);
        assertThat(me.dbnos()).isEqualTo(9);
        assertThat(me.damageDealt()).isEqualTo(1024.73);
        assertThat(me.winPlace()).isEqualTo(1);

        assertThat(match.isCompetitive()).isTrue();
        assertThat(match.isFpp()).isTrue();
        assertThat(match.telemetryUrl()).contains("https://telemetry-cdn.pubg.com/example-telemetry.json");
    }

    @Test
    void 경기에_없는_계정이면_빈_값() throws IOException {
        MatchResponse match = read(mapper, "/pubg/match.json", MatchResponse.class);

        assertThat(match.statsOf("account.nobody")).isEmpty();
    }
}
