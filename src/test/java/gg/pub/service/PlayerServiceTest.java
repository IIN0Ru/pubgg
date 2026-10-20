package gg.pub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.config.PubgApiProperties;
import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import gg.pub.pubgapi.PlayerNotFoundException;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.pubgapi.dto.MatchResponse;
import gg.pub.pubgapi.dto.PlayerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlayerService 가 API 응답을 MatchRecord 로 제대로 바꾸는지 확인한다.
 * 실제 PUBG 서버는 부르지 않고, 가짜 Client(mock)가 예시 JSON 을 돌려주게 한다.
 * (단위 테스트라 캐시는 꺼진 상태: MatchRecordService 를 직접 만들어서 쓴다)
 */
class PlayerServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private PubgApiClient client;
    private PlayCardService playCardService;
    private PlayerService service;

    @BeforeEach
    void setUp() {
        client = mock(PubgApiClient.class);
        playCardService = mock(PlayCardService.class);
        when(playCardService.build(any(), anyString())).thenReturn(Optional.empty());
        service = new PlayerService(client, new MatchRecordService(client), playCardService,
                new PubgApiProperties("test-key", "https://api.pubg.com", 10));
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

        MatchRecord first = result.matches().get(0);
        assertThat(first.mode()).isEqualTo("스쿼드 FPP");
        assertThat(first.map()).isEqualTo("에란겔");
        assertThat(first.matchTypeLabel()).isEqualTo("경쟁");
        assertThat(first.typeKey()).isEqualTo("competitive");
        assertThat(first.perspectiveKey()).isEqualTo("fpp");
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.teams()).isEqualTo(2);
        assertThat(first.kills()).isEqualTo(8);
        assertThat(first.knocks()).isEqualTo(9);
        assertThat(first.damage()).isEqualTo(1024);       // 소수점 버림
        assertThat(first.survivedText()).isEqualTo("31분 2초");
        assertThat(first.playedAtText()).isEqualTo("10월 19일 23:12");  // UTC 14:12 → 한국 23:12
        assertThat(first.isWin()).isTrue();
        assertThat(first.telemetryUrl()).isEqualTo("https://telemetry-cdn.pubg.com/example-telemetry.json");
    }

    @Test
    void 같은_팀_팀원_기록도_담는다() throws IOException {
        PlayerResponse player = read("/pubg/player.json", PlayerResponse.class);
        when(client.findPlayer(Platform.STEAM, "N0-RU")).thenReturn(player.data().get(0));
        when(client.getMatch(any(), anyString())).thenReturn(read("/pubg/match.json", MatchResponse.class));

        MatchRecord first = service.getRecentMatches(Platform.STEAM, "N0-RU").matches().get(0);

        assertThat(first.me().headshotKills()).isEqualTo(3);
        assertThat(first.me().longestKill()).isEqualTo(212);
        assertThat(first.me().deathTypeLabel()).isEqualTo("끝까지 생존");

        assertThat(first.teammates()).hasSize(1);   // OtherPlayer 는 다른 팀이라 빠진다
        MatchPlayer mate = first.teammates().get(0);
        assertThat(mate.name()).isEqualTo("DuoFriend");
        assertThat(mate.kills()).isEqualTo(2);
        assertThat(mate.damage()).isEqualTo(410);
        assertThat(mate.revives()).isEqualTo(2);
        assertThat(mate.deathTypeLabel()).isEqualTo("적에게 사망");
    }

    @Test
    void 경쟁전_경기만_플레이_카드_계산에_넘긴다() throws IOException {
        PlayerResponse player = read("/pubg/player.json", PlayerResponse.class);
        MatchResponse competitive = read("/pubg/match.json", MatchResponse.class);   // 예시 경기는 경쟁전
        when(client.findPlayer(Platform.STEAM, "N0-RU")).thenReturn(player.data().get(0));
        when(client.getMatch(any(), anyString())).thenReturn(competitive);

        service.getRecentMatches(Platform.STEAM, "N0-RU");

        verify(playCardService).build(argThat(list -> list.size() == 2), eq("account.test0001"));
    }

    @Test
    void 경기_하나를_못_받아도_나머지로_보여준다() throws IOException {
        PlayerResponse player = read("/pubg/player.json", PlayerResponse.class);
        when(client.findPlayer(Platform.STEAM, "N0-RU")).thenReturn(player.data().get(0));
        when(client.getMatch(any(), eq("match-0001"))).thenReturn(read("/pubg/match.json", MatchResponse.class));
        when(client.getMatch(any(), eq("match-0002"))).thenThrow(new PubgApiException("일시적 오류"));

        PlayerMatches result = service.getRecentMatches(Platform.STEAM, "N0-RU");

        assertThat(result.matches()).hasSize(1);
    }

    @Test
    void 플레이어가_없으면_예외가_그대로_전달된다() {
        when(client.findPlayer(Platform.STEAM, "nobody")).thenThrow(new PlayerNotFoundException("nobody"));

        assertThatThrownBy(() -> service.getRecentMatches(Platform.STEAM, "nobody"))
                .isInstanceOf(PlayerNotFoundException.class);
    }
}
