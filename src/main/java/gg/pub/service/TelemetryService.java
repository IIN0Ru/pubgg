package gg.pub.service;

import gg.pub.domain.TelemetryStats;
import gg.pub.pubgapi.PubgApiClient;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

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
 * 저장은 두 겹이다. 메모리 캐시(@Cacheable)가 먼저, 없으면 오래 보관하는 곳(TelemetryStatsStore, 운영은 DB).
 * 메모리는 서버를 다시 켜면 비워지지만 DB 에 있는 결과는 남아서, 재배포·잠들었다 깬 뒤에도 다시 분석하지 않는다.
 * DB 가 안 되더라도 검색은 되어야 해서, 읽기·쓰기 오류는 로그만 남기고 분석으로 넘어간다.
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final PubgApiClient client;
    private final TelemetryAnalyzer analyzer;
    private final TelemetryStatsStore store;

    public TelemetryService(PubgApiClient client, TelemetryAnalyzer analyzer, TelemetryStatsStore store) {
        this.client = client;
        this.analyzer = analyzer;
        this.store = store;
    }

    @Cacheable(cacheNames = "telemetry", key = "#matchId + ':' + #accountId")
    public TelemetryStats analyze(String matchId, String telemetryUrl, String accountId) {
        // 메모리 캐시에 없을 때만 여기까지 온다. 먼저 오래 보관한 결과가 있는지 본다
        Optional<TelemetryStats> saved = findSaved(matchId, accountId);
        if (saved.isPresent()) {
            return saved.get();
        }

        // 실제로 받아서 분석하는 경기. 시간을 재서 남긴다.
        long start = System.nanoTime();
        long[] waited = {0};   // 파일 내용이 올 때까지 기다린 시간 (나머지는 우리 쪽 계산)
        TelemetryStats stats = client.readTelemetry(telemetryUrl,
                body -> analyzer.analyze(new TimedInputStream(body, waited), accountId));
        long total = (System.nanoTime() - start) / 1_000_000;
        long wait = waited[0] / 1_000_000;
        log.info("텔레메트리 한 판 {}ms = 받는 데 기다린 시간 {}ms + 계산 {}ms", total, wait, total - wait);
        saveQuietly(matchId, accountId, stats);
        return stats;
    }

    private Optional<TelemetryStats> findSaved(String matchId, String accountId) {
        try {
            Optional<TelemetryStats> saved = store.find(matchId, accountId);
            if (saved.isPresent()) {
                log.info("텔레메트리 저장본 사용 {}", matchId);
            }
            return saved;
        } catch (RuntimeException e) {
            log.warn("저장된 분석 결과를 못 읽음, 새로 분석함: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void saveQuietly(String matchId, String accountId, TelemetryStats stats) {
        try {
            store.save(matchId, accountId, stats);
        } catch (RuntimeException e) {
            log.warn("분석 결과를 저장하지 못함 (검색에는 영향 없음): {}", e.getMessage());
        }
    }

    /** 읽는 데 걸린 시간을 합산하는 스트림 */
    private static final class TimedInputStream extends FilterInputStream {
        private final long[] waitedNanos;

        TimedInputStream(InputStream in, long[] waitedNanos) {
            super(in);
            this.waitedNanos = waitedNanos;
        }

        @Override
        public int read() throws IOException {
            long t = System.nanoTime();
            try {
                return super.read();
            } finally {
                waitedNanos[0] += System.nanoTime() - t;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            long t = System.nanoTime();
            try {
                return super.read(b, off, len);
            } finally {
                waitedNanos[0] += System.nanoTime() - t;
            }
        }
    }
}
