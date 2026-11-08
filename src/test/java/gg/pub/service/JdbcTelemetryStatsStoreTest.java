package gg.pub.service;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import gg.pub.domain.TelemetryStats;
import gg.pub.domain.TelemetryStats.Bag;
import gg.pub.domain.TelemetryStats.Landing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 텔레메트리 분석 결과를 DB 에 저장했다가 다시 읽기.
 * 테스트용 메모리 DB(H2, PostgreSQL 모드)에 운영과 같은 db/schema.sql 로 테이블을 만들어서 쓴다.
 */
class JdbcTelemetryStatsStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-08T03:00:00Z");

    private JdbcTemplate jdbc;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        mapper = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }

    private JdbcTelemetryStatsStore store(Instant at) {
        return new JdbcTelemetryStatsStore(jdbc, mapper, Clock.fixed(at, ZoneOffset.UTC));
    }

    private static TelemetryStats fullStats() {
        return new TelemetryStats(
                Map.of("Item_Weapon_HK416_C", 200L, "Item_Weapon_Kar98k_C", 60L),
                3, 1.25, 2, 0.75, 3,
                new Landing(List.of("zone_a", "zone_b"), 123456.5, 654321.25),
                new Bag(4, 2, 90),
                new Landing(List.of("zone_c"), 1.5, 2.5),
                Map.of("Item_Weapon_Grenade_C", 2, "Item_Weapon_SmokeBomb_C", 1));
    }

    @Test
    void 저장한_분석_결과를_그대로_다시_읽는다() {
        JdbcTelemetryStatsStore store = store(NOW);
        TelemetryStats stats = fullStats();

        store.save("match-1", "account.me", stats);

        assertThat(store.find("match-1", "account.me")).contains(stats);
        assertThat(store.find("match-1", "account.other")).isEmpty();   // 같은 경기라도 다른 사람 결과는 따로
        assertThat(store.find("match-2", "account.me")).isEmpty();
    }

    @Test
    void 착지_가방_파밍이_없는_기록도_읽는다() {
        JdbcTelemetryStatsStore store = store(NOW);
        TelemetryStats stats = new TelemetryStats(Map.of(), 0);

        store.save("match-1", "account.me", stats);

        assertThat(store.find("match-1", "account.me")).contains(stats);
    }

    @Test
    void 같은_경기를_다시_저장하면_덮어쓴다() {
        JdbcTelemetryStatsStore store = store(NOW);

        store.save("match-1", "account.me", new TelemetryStats(Map.of(), 1));
        store.save("match-1", "account.me", new TelemetryStats(Map.of(), 7));

        assertThat(store.find("match-1", "account.me").orElseThrow().throwables()).isEqualTo(7);
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM match_telemetry", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void 분석_버전이_다른_저장본은_없는_것으로_보고_다시_저장하면_바뀐다() {
        JdbcTelemetryStatsStore store = store(NOW);
        store.save("match-1", "account.me", new TelemetryStats(Map.of(), 1));
        // 예전 버전으로 저장된 행인 척
        jdbc.update("UPDATE match_telemetry SET version = ?", TelemetryAnalyzer.VERSION - 1);

        assertThat(store.find("match-1", "account.me")).isEmpty();

        store.save("match-1", "account.me", new TelemetryStats(Map.of(), 9));
        assertThat(store.find("match-1", "account.me").orElseThrow().throwables()).isEqualTo(9);
    }

    @Test
    void 보관_기간이_지난_결과는_저장할_때_지워진다() {
        store(NOW.minus(JdbcTelemetryStatsStore.RETENTION).minusSeconds(1))
                .save("match-old", "account.me", new TelemetryStats(Map.of(), 1));
        store(NOW.minus(JdbcTelemetryStatsStore.RETENTION).plusSeconds(60))
                .save("match-recent", "account.me", new TelemetryStats(Map.of(), 1));

        JdbcTelemetryStatsStore store = store(NOW);
        store.save("match-new", "account.me", new TelemetryStats(Map.of(), 1));

        assertThat(store.find("match-old", "account.me")).isEmpty();
        assertThat(store.find("match-recent", "account.me")).isPresent();
        assertThat(store.find("match-new", "account.me")).isPresent();
    }
}
