package gg.pub.service;

import gg.pub.domain.TelemetryStats;
import gg.pub.pubgapi.PubgApiClient;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

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
        long[] waited = {0};   // 파일 내용이 올 때까지 기다린 시간 (나머지는 우리 쪽 계산)
        TelemetryStats stats = client.readTelemetry(telemetryUrl,
                body -> analyzer.analyze(new TimedInputStream(body, waited), accountId));
        long total = (System.nanoTime() - start) / 1_000_000;
        long wait = waited[0] / 1_000_000;
        log.info("텔레메트리 한 판 {}ms = 받는 데 기다린 시간 {}ms + 계산 {}ms", total, wait, total - wait);
        return stats;
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
