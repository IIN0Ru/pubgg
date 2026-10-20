package gg.pub.pubgapi;

import gg.pub.config.PubgApiProperties;
import gg.pub.pubgapi.dto.MatchResponse;
import gg.pub.pubgapi.dto.PlayerResponse;
import gg.pub.domain.Platform;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * PUBG 공식 API 를 호출하는 클래스. (외부 데이터 담당)
 *
 * 하는 일은 딱 두 가지:
 *  1. 닉네임으로 플레이어 찾기  → findPlayer
 *  2. 경기 ID로 경기 기록 받기  → getMatch
 *
 * 받은 데이터를 계산하거나 화면에 맞게 바꾸는 일은 여기서 하지 않고 Service 가 한다.
 *
 * 참고: 플레이어 조회는 1분에 10번까지만 가능하다 (PUBG API 제한).
 *       경기 조회는 횟수 제한이 없다.
 */
@Component
public class PubgApiClient {

    private static final String JSON_API = "application/vnd.api+json";

    private final RestClient restClient;
    private final PubgApiProperties properties;

    /** RestClient.Builder 는 Spring Boot 가 미리 만들어서 넣어준다 */
    public PubgApiClient(RestClient.Builder builder, PubgApiProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, JSON_API)
                .build();
    }

    /** 닉네임으로 플레이어를 찾는다. 없으면 PlayerNotFoundException. */
    public PlayerResponse.Player findPlayer(Platform platform, String nickname) {
        requireKey();
        try {
            PlayerResponse response = restClient.get()
                    .uri(uri -> uri.path("/shards/{shard}/players")
                            .queryParam("filter[playerNames]", nickname)
                            .build(platform.getShard()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.key())
                    .retrieve()
                    .onStatus(s -> s.value() == HttpStatus.NOT_FOUND.value(), (req, res) -> {
                        throw new PlayerNotFoundException(nickname);
                    })
                    .onStatus(s -> s.isError(), (req, res) -> {
                        throw errorFor(res.getStatusCode().value());
                    })
                    .body(PlayerResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new PlayerNotFoundException(nickname);
            }
            return response.data().get(0);

        } catch (ResourceAccessException e) {
            throw new PubgApiException("PUBG 서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.", e);
        }
    }

    /** 경기 ID로 경기 기록을 받는다. */
    public MatchResponse getMatch(Platform platform, String matchId) {
        requireKey();
        try {
            return restClient.get()
                    .uri("/shards/{shard}/matches/{id}", platform.getShard(), matchId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.key())
                    .retrieve()
                    .onStatus(s -> s.isError(), (req, res) -> {
                        throw errorFor(res.getStatusCode().value());
                    })
                    .body(MatchResponse.class);

        } catch (ResourceAccessException e) {
            throw new PubgApiException("PUBG 서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.", e);
        }
    }

    private void requireKey() {
        if (!properties.hasKey()) {
            throw new PubgApiException("API 키가 설정되지 않았어요. 환경 변수 PUBG_API_KEY 를 등록하고 IntelliJ를 다시 시작해 주세요.");
        }
    }

    /** HTTP 상태 코드를 사용자에게 보여줄 문장으로 바꾼다 */
    private PubgApiException errorFor(int status) {
        return switch (status) {
            case 401 -> new PubgApiException("API 키가 올바르지 않아요. 환경 변수 PUBG_API_KEY 값을 확인해 주세요.");
            case 429 -> new PubgApiException("검색이 너무 많아요. 1분 뒤에 다시 시도해 주세요.");
            default -> new PubgApiException("PUBG 서버에서 오류가 났어요. (상태 코드 " + status + ")");
        };
    }
}
