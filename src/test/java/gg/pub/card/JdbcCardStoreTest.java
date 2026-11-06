package gg.pub.card;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 에 공유 카드를 저장했다가 다시 읽기.
 * 테스트용 메모리 DB(H2, PostgreSQL 모드)에 운영과 같은 db/schema.sql 로 테이블을 만들어서 쓴다.
 * 스프링 서버를 띄우지 않고 직접 연결해서 빠르다 (FileCardStoreTest 와 같은 방식).
 */
class JdbcCardStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private JdbcTemplate jdbc;
    private JdbcCardStore store;

    @BeforeEach
    void setUp() {
        // 테스트마다 이름이 다른 새 메모리 DB. 테스트끼리 데이터가 섞이지 않는다
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);

        jdbc = new JdbcTemplate(dataSource);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        store = new JdbcCardStore(jdbc, mapper);
    }

    private static SharedCard card(String id, String platform, String nickname, String mic, Instant at) {
        return new SharedCard(id, platform, nickname, Map.of("mic", mic),
                new SharedCard.Background("taego", 10, 20), at);
    }

    @Test
    void 공유한_카드와_그림을_다시_읽는다() {
        SharedCard card = card("id-aaaaaa1", "kakao", "헬멧3렙장인", "o", NOW);

        store.create(card, new byte[]{1, 2, 3});

        assertThat(store.find("id-aaaaaa1")).contains(card);
        assertThat(store.image("id-aaaaaa1").orElseThrow()).containsExactly(1, 2, 3);
        assertThat(store.hasImage("id-aaaaaa1")).isTrue();

        assertThat(store.find("id-nothere1")).isEmpty();
        assertThat(store.image("id-nothere1")).isEmpty();
        assertThat(store.hasImage("id-nothere1")).isFalse();
    }

    @Test
    void 이상한_ID는_DB를_묻지_않고_빈_값() {
        assertThat(store.find("a'; DROP TABLE share_card;--")).isEmpty();
        assertThat(store.image("../x")).isEmpty();
        assertThat(store.hasImage("")).isFalse();
    }

    @Test
    void 같은_플레이어가_새로_공유하면_이전_그림만_비워지고_이전_링크는_열린다() {
        store.create(card("id-old00001", "kakao", "닉네임", "o", NOW), new byte[]{1});
        store.create(card("id-new00002", "kakao", "닉네임", "x", NOW.plusSeconds(60)), new byte[]{9, 9});

        // 이전 카드: 행은 남고 그림만 비워짐
        assertThat(store.find("id-old00001")).isPresent();
        assertThat(store.hasImage("id-old00001")).isFalse();
        assertThat(store.image("id-old00001")).isEmpty();
        // 새 카드: 그림이 있음
        assertThat(store.hasImage("id-new00002")).isTrue();
        assertThat(store.image("id-new00002").orElseThrow()).containsExactly(9, 9);
    }

    @Test
    void 대소문자가_달라도_같은_플레이어로_본다() {
        store.create(card("id-old00001", "steam", "N0-RU", "o", NOW), new byte[]{1});
        store.create(card("id-new00002", "steam", "n0-ru", "x", NOW.plusSeconds(60)), new byte[]{2});

        assertThat(store.hasImage("id-old00001")).isFalse();
        assertThat(store.hasImage("id-new00002")).isTrue();
    }

    @Test
    void 다른_플레이어나_다른_플랫폼의_그림은_건드리지_않는다() {
        store.create(card("id-other001", "steam", "다른사람", "o", NOW), new byte[]{1});
        store.create(card("id-kakao001", "kakao", "닉네임", "o", NOW), new byte[]{2});
        store.create(card("id-steam001", "steam", "닉네임", "o", NOW.plusSeconds(60)), new byte[]{3});

        assertThat(store.hasImage("id-other001")).isTrue();
        assertThat(store.hasImage("id-kakao001")).isTrue();   // 같은 닉네임이어도 플랫폼이 다르면 다른 플레이어
        assertThat(store.hasImage("id-steam001")).isTrue();
    }

    @Test
    void 보관_기간이_지난_공유는_새로_공유할_때_지워진다() {
        store.create(card("id-ancient1", "steam", "오래된", "o", NOW.minus(CardStore.RETENTION).minusSeconds(1)),
                new byte[]{1});
        store.create(card("id-recent01", "steam", "최근", "o", NOW.minus(CardStore.RETENTION).plusSeconds(60)),
                new byte[]{2});

        store.create(card("id-fresh001", "steam", "새로", "o", NOW), new byte[]{3});

        assertThat(store.find("id-ancient1")).isEmpty();      // 30일이 지나서 통째로 삭제
        assertThat(store.find("id-recent01")).isPresent();    // 아직 기간 안
        assertThat(store.find("id-fresh001")).isPresent();
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM share_card", Integer.class);
        assertThat(rows).isEqualTo(2);
    }
}
