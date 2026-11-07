package gg.pub.card;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 폴더에 공유 카드를 저장했다가 다시 읽기 */
class FileCardStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @TempDir
    Path dir;

    FileCardStore store;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        store = new FileCardStore(dir.toString(), mapper);
    }

    private static SharedCard card(String id, String platform, String nickname, Instant at) {
        return new SharedCard(id, platform, nickname, Map.of("mic", "o"),
                new SharedCard.Background("taego", 10, 20), at);
    }

    @Test
    void 공유한_카드와_그림을_다시_읽는다() {
        SharedCard card = card("id-aaaaaa1", "kakao", "헬멧3렙장인", NOW);

        store.create(card, new byte[]{1, 2, 3});

        assertThat(store.find("id-aaaaaa1")).contains(card);
        assertThat(store.image("id-aaaaaa1").orElseThrow()).containsExactly(1, 2, 3);
        assertThat(store.hasImage("id-aaaaaa1")).isTrue();
        assertThat(store.find("id-nothere1")).isEmpty();
        assertThat(store.hasImage("id-nothere1")).isFalse();
    }

    @Test
    void 경로를_거슬러_올라가는_ID는_빈_값() {
        assertThat(store.find("../secret")).isEmpty();
        assertThat(store.image("../secret")).isEmpty();
        assertThat(store.hasImage("../secret")).isFalse();
    }

    @Test
    void 같은_플레이어가_새로_공유하면_이전_그림만_지워지고_카드는_남는다() {
        store.create(card("id-old00001", "steam", "N0-RU", NOW), new byte[]{1});
        store.create(card("id-other001", "steam", "다른사람", NOW), new byte[]{2});
        store.create(card("id-new00002", "steam", "n0-ru", NOW.plusSeconds(60)), new byte[]{3});

        assertThat(store.find("id-old00001")).isPresent();
        assertThat(store.hasImage("id-old00001")).isFalse();
        assertThat(store.hasImage("id-new00002")).isTrue();
        assertThat(store.hasImage("id-other001")).isTrue();
    }

    @Test
    void 보관_기간이_지난_공유는_새로_공유할_때_지워진다() {
        store.create(card("id-ancient1", "steam", "오래된", NOW.minus(CardStore.RETENTION).minusSeconds(1)),
                new byte[]{1});

        store.create(card("id-fresh001", "steam", "새로", NOW), new byte[]{2});

        assertThat(store.find("id-ancient1")).isEmpty();
        assertThat(store.hasImage("id-ancient1")).isFalse();
        assertThat(store.find("id-fresh001")).isPresent();
    }
}
