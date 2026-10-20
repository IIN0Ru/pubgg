package gg.pub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.config.PubgApiProperties;
import gg.pub.domain.MatchSummary;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import gg.pub.pubgapi.PlayerNotFoundException;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.dto.MatchResponse;
import gg.pub.pubgapi.dto.PlayerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PlayerService 가 API 응답을 화면용 MatchSummary 로 제대로 바꾸는지 확인한다.
 * 실제 PUBG 서버는 부르지 않고, 가짜 Client(mock)가 예시 JSON 을 돌려주게 한다.
 */
class PlayerServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private PubgApiClient client;
    private PlayerService service;

    @BeforeEach
    void setUp() {
        client = mock(PubgApiClient.class);
        service = new PlayerService(client, new PubgApiProperties("test-key", "https://api.pubg.com", 10));
    }

    private <T> T read(String path, Class<T> type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return mapper.readValue(in, type);
        }
    }

    @Test
    void 최근_경기를_화면용으로_정리한다() throws IOException {
        PlayerResponse player = read("/pubg/player.json", PlayerResponse.class);
        MatchResponse match = read("/pubg/match.json", MatchResponse.class);
        when(client.findPlayer(Platform.STEAM, "N0-RU")).thenReturn(player.data().get(0));
        when(client.getMatch(any(), anyString())).thenReturn(match);

        PlayerMatches result = service.getRecentMatches(Platform.STEAM, "N0-RU");

        assertThat(result.nickname()).isEqualTo("N0-RU");
        assertThat(result.matches()).hasSize(2);   // 예시 플레이어의 경기 ID가 2개

        MatchSummary first = result.matches().get(0);
        assertThat(first.mode()).isEqualTo("스쿼드 FPP");
        assertThat(first.map()).isEqualTo("에란겔");
        assertThat(first.matchType()).isEqualTo("경쟁");
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.teams()).isEqualTo(2);
        assertThat(first.kills()).isEqualTo(8);
        assertThat(first.knocks()).isEqualTo(9);
        assertThat(first.damage()).isEqualTo(1024);       // 소수점 버림
        assertThat(first.survivedText()).isEqualTo("31분 2초");
        assertThat(first.playedAtText()).isEqualTo("10월 19일 23:12");  // UTC 14:12 → 한국 23:12
        assertThat(first.isWin()).isTrue();
    }

    @Test
    void 플레이어가_없으면_예외가_그대로_전달된다() {
        when(client.findPlayer(Platform.STEAM, "nobody")).thenThrow(new PlayerNotFoundException("nobody"));

        assertThatThrownBy(() -> service.getRecentMatches(Platform.STEAM, "nobody"))
                .isInstanceOf(PlayerNotFoundException.class);
    }
}
