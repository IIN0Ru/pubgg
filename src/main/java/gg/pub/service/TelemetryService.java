package gg.pub.service;

import gg.pub.domain.TelemetryStats;
import gg.pub.pubgapi.PubgApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 텔레메트리를 받아서 분석하고, 결과를 저장(캐시)해 둔다.
 *
 * 끝난 경기의 기록은 바뀌지 않으니, 한 번 분석한 (경기, 플레이어) 결과는 메모리에 저장해 두고
 * 같은 사람을 다시 검색하면 텔레메트리를 또 받지 않는다.
 * 저장하는 건 분석 결과(작은 숫자 몇 개)뿐이고, 수 MB 짜리 원본은 저장하지 않는다.
 *
 * 주의: 지금 캐시는 서버 메모리에 있어서 서버를 다시 켜면 비워진다. 배포할 때 다시 검토.
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final PubgApiClient client;
    private final TelemetryAnalyzer analyzer;

    public TelemetryService(PubgApiClient client, TelemetryAnalyzer analyzer) {
        this.client = client;
        this.analyzer = analyzer;
    }

    @Cacheable(cacheNames = "telemetry", key = "#matchId + ':' + #accountId")
    public TelemetryStats analyze(String matchId, String telemetryUrl, String accountId) {
        // 이 줄은 캐시에 없을 때만 실행된다 = 실제로 받아서 분석한 경기. 시간을 재서 남긴다.
        long start = System.nanoTime();
        TelemetryStats stats = client.readTelemetry(telemetryUrl, body -> analyzer.analyze(body, accountId));
        log.info("텔레메트리 한 판 받기+분석 {}ms", (System.nanoTime() - start) / 1_000_000);
        return stats;
    }
}
