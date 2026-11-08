package gg.pub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.TelemetryStats;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 텔레메트리 분석 결과를 DB(match_telemetry 테이블)에 저장한다. 운영(prod 프로필)에서 쓴다.
 * 한 행 = 한 경기 × 한 플레이어. 결과는 작은 JSON 이라 수백 바이트~1KB 정도다.
 * 보관 기간({@link #RETENTION})이 지난 행은 저장할 때 가끔 지운다 (무료 DB 용량이 작아서).
 */
@Component
@Profile("prod")
public class JdbcTelemetryStatsStore implements TelemetryStatsStore {

    static final Duration RETENTION = Duration.ofDays(60);
    /** 오래된 행을 지우는 일은 이 간격으로만 한다 (저장할 때마다 하지 않는다) */
    private static final Duration PURGE_EVERY = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AtomicReference<Instant> lastPurge = new AtomicReference<>(Instant.EPOCH);

    public JdbcTelemetryStatsStore(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<TelemetryStats> find(String matchId, String accountId) {
        List<String> rows = jdbc.query(
                "SELECT stats_json FROM match_telemetry WHERE match_id = ? AND account_id = ? AND version = ?",
                (rs, rowNum) -> rs.getString("stats_json"),
                matchId, accountId, TelemetryAnalyzer.VERSION);
        return rows.stream().findFirst().map(this::fromJson);
    }

    @Override
    public void save(String matchId, String accountId, TelemetryStats stats) {
        Instant now = clock.instant();
        String json = toJson(stats);
        // 있으면 덮어쓰고(버전이 달랐던 행), 없으면 넣는다. 동시에 같은 경기를 저장해서 부딪히면 먼저 들어간 것을 둔다
        int updated = jdbc.update(
                "UPDATE match_telemetry SET version = ?, stats_json = ?, created_at = ? WHERE match_id = ? AND account_id = ?",
                TelemetryAnalyzer.VERSION, json, Timestamp.from(now), matchId, accountId);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO match_telemetry (match_id, account_id, version, stats_json, created_at)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        matchId, accountId, TelemetryAnalyzer.VERSION, json, Timestamp.from(now));
            } catch (DuplicateKeyException ignored) {
                // 다른 요청이 방금 같은 결과를 저장했다
            }
        }
        purgeOld(now);
    }

    private void purgeOld(Instant now) {
        Instant last = lastPurge.get();
        if (now.isAfter(last.plus(PURGE_EVERY)) && lastPurge.compareAndSet(last, now)) {
            jdbc.update("DELETE FROM match_telemetry WHERE created_at < ?", Timestamp.from(now.minus(RETENTION)));
        }
    }

    private String toJson(TelemetryStats stats) {
        try {
            return mapper.writeValueAsString(stats);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TelemetryStats fromJson(String json) {
        try {
            return mapper.readValue(json, TelemetryStats.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
