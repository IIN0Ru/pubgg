package gg.pub.pubgapi;

import gg.pub.config.PubgApiProperties;
import gg.pub.pubgapi.dto.MatchResponse;
import gg.pub.pubgapi.dto.PlayerResponse;
import gg.pub.pubgapi.dto.RankedStatsResponse;
import gg.pub.pubgapi.dto.SeasonResponse;
import gg.pub.domain.Platform;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

/**
 * PUBG 공식 API 를 호출하는 클래스. (외부 데이터 담당)
 *
 * 하는 일:
 *  1. 닉네임으로 플레이어 찾기  → findPlayer
 *  2. 경기 ID로 경기 기록 받기  → getMatch
 *  3. 텔레메트리(경기 상세 기록 파일) 읽기 → readTelemetry
 *  4. 시즌 목록 받기 → getSeasons
 *  5. 한 시즌 경쟁전 기록 받기 → getRankedStats
 *
 * 받은 데이터를 계산하거나 화면에 맞게 바꾸는 일은 여기서 하지 않고 Service 가 한다.
 *
 * 참고: 플레이어·시즌·경쟁전 기록 조회는 합쳐서 1분에 10번까지만 가능하다 (PUBG API 제한).
 *       경기 조회와 텔레메트리는 횟수 제한이 없다.
 */
@Component
public class PubgApiClient {

    private static final String JSON_API = "application/vnd.api+json";

    private final RestClient restClient;      // api.pubg.com 용
    private final RestClient telemetryClient; // 텔레메트리 파일 주소용 (다른 서버, 키 필요 없음)
    private final PubgApiProperties properties;

    /** RestClient.Builder 는 Spring Boot 가 미리 만들어서 넣어준다 */
    public PubgApiClient(RestClient.Builder builder, PubgApiProperties properties) {
        this.properties = properties;
        this.restClient = builder.clone()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, JSON_API)
                .build();
        this.telemetryClient = builder.clone().build();
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

    /** 시즌 목록 (지금 시즌을 찾는 데 씀) */
    public SeasonResponse getSeasons(Platform platform) {
        return getJson("/shards/{shard}/seasons", SeasonResponse.class, platform.getShard());
    }

    /**
     * 한 시즌 경쟁전 기록.
     * 이번 시즌 경쟁전을 한 판도 안 했으면 PUBG 가 404 를 줄 수 있어서, 그때는 빈 기록을 돌려준다.
     */
    public RankedStatsResponse getRankedStats(Platform platform, String accountId, String seasonId) {
        requireKey();
        try {
            return restClient.get()
                    .uri("/shards/{shard}/players/{id}/seasons/{season}/ranked",
                            platform.getShard(), accountId, seasonId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.key())
                    .exchange((req, res) -> {
                        if (res.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                            return new RankedStatsResponse(null);
                        }
                        if (res.getStatusCode().isError()) {
                            throw errorFor(res.getStatusCode().value());
                        }
                        RankedStatsResponse body = res.bodyTo(RankedStatsResponse.class);
                        return body != null ? body : new RankedStatsResponse(null);
                    });
        } catch (ResourceAccessException e) {
            throw new PubgApiException("PUBG 서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.", e);
        }
    }

    /** 단순 GET (오류는 상태 코드별 문장으로) */
    private <T> T getJson(String path, Class<T> type, Object... vars) {
        requireKey();
        try {
            return restClient.get()
                    .uri(path, vars)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.key())
                    .retrieve()
                    .onStatus(s -> s.isError(), (req, res) -> {
                        throw errorFor(res.getStatusCode().value());
                    })
                    .body(type);
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

    /**
     * 텔레메트리 파일을 내려받아 reader 에게 넘긴다.
     *
     * 텔레메트리는 한 경기에 수 MB 짜리 JSON 이라 통째로 메모리에 올리지 않고
     * 흘려보내면서(stream) 읽는다. 파일이 gzip 으로 압축되어 오면 풀어서 넘긴다.
     *
     * @param url    경기 응답(asset)에 들어 있는 텔레메트리 주소
     * @param reader 내용을 읽어서 결과를 만드는 함수 (TelemetryAnalyzer)
     */
    public <T> T readTelemetry(String url, Function<InputStream, T> reader) {
        try {
            return telemetryClient.get()
                    .uri(URI.create(url))
                    .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            throw new PubgApiException("텔레메트리를 받지 못했어요. (상태 코드 " + res.getStatusCode().value() + ")");
                        }
                        try (InputStream body = unzipIfNeeded(res.getBody())) {
                            return reader.apply(body);
                        }
                    });
        } catch (ResourceAccessException | UncheckedIOException e) {
            throw new PubgApiException("텔레메트리를 받는 중 연결이 끊겼어요.", e);
        }
    }

    /** 앞 2바이트가 gzip 표시(0x1f 0x8b)면 압축을 풀고, 아니면 그대로 돌려준다 */
    static InputStream unzipIfNeeded(InputStream raw) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw);
        in.mark(2);
        int b1 = in.read();
        int b2 = in.read();
        in.reset();
        if (b1 == 0x1f && b2 == 0x8b) {
            return new GZIPInputStream(in);
        }
        return in;
    }
}
