package gg.pub.card;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import gg.pub.domain.Platform;
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
 * DB 에 저장했다가 다시 읽기.
 * 테스트용 메모리 DB(H2, PostgreSQL 모드)에 운영과 같은 db/schema.sql 로 테이블을 만들어서 쓴다.
 * 스프링 서버를 띄우지 않고 직접 연결해서 빠르다 (FileCardStoreTest 와 같은 방식).
 */
class JdbcCardStoreTest {

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

    private static SavedCard card(String nickname, String mic) {
        return new SavedCard("kakao", nickname, Map.of("mic", mic),
                new SavedCard.Background("taego", 10, 20), Instant.parse("2026-10-28T12:00:00Z"));
    }

    @Test
    void 저장한_카드와_그림을_다시_읽는다() {
        SavedCard card = card("헬멧3렙장인", "o");

        store.write(Platform.KAKAO, "헬멧3렙장인", new CardStore.Stored(card, "hash"), new byte[]{1, 2, 3});

        CardStore.Stored found = store.find(Platform.KAKAO, "헬멧3렙장인").orElseThrow();
        assertThat(found.card()).isEqualTo(card);
        assertThat(found.tokenHash()).isEqualTo("hash");
        assertThat(store.image(Platform.KAKAO, "헬멧3렙장인").orElseThrow()).containsExactly(1, 2, 3);

        // 플랫폼이 다르거나 닉네임이 다르면 다른 카드
        assertThat(store.find(Platform.STEAM, "헬멧3렙장인")).isEmpty();
        assertThat(store.find(Platform.KAKAO, "다른사람")).isEmpty();
        assertThat(store.image(Platform.KAKAO, "다른사람")).isEmpty();
    }

    @Test
    void 대소문자가_달라도_같은_카드다() {
        store.write(Platform.STEAM, "N0-RU", new CardStore.Stored(card("N0-RU", "o"), "hash"), new byte[]{1});

        assertThat(store.find(Platform.STEAM, "n0-ru")).isPresent();
        assertThat(store.image(Platform.STEAM, "n0-RU")).isPresent();
    }

    @Test
    void 다시_저장하면_새_값으로_덮어쓰고_행은_하나만_남는다() {
        store.write(Platform.KAKAO, "닉네임", new CardStore.Stored(card("닉네임", "o"), "hash-1"), new byte[]{1});
        store.write(Platform.KAKAO, "닉네임", new CardStore.Stored(card("닉네임", "x"), "hash-2"), new byte[]{9, 9});

        CardStore.Stored found = store.find(Platform.KAKAO, "닉네임").orElseThrow();
        assertThat(found.card().choices()).containsEntry("mic", "x");
        assertThat(found.tokenHash()).isEqualTo("hash-2");
        assertThat(store.image(Platform.KAKAO, "닉네임").orElseThrow()).containsExactly(9, 9);

        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM player_card", Integer.class);
        assertThat(rows).isEqualTo(1);
    }
}
