package gg.pub.card;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import gg.pub.domain.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 폴더에 저장했다가 다시 읽기 */
class FileCardStoreTest {

    @TempDir
    Path dir;

    @Test
    void 저장한_카드와_그림을_다시_읽는다() {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        FileCardStore store = new FileCardStore(dir.toString(), mapper);
        SavedCard card = new SavedCard("kakao", "헬멧3렙장인", Map.of("mic", "o"),
                new SavedCard.Background("taego", 10, 20), Instant.parse("2026-10-28T12:00:00Z"));
        byte[] png = {1, 2, 3};

        store.write(Platform.KAKAO, "헬멧3렙장인", new CardStore.Stored(card, "hash"), png);

        // 대소문자·플랫폼이 같으면 같은 카드
        assertThat(store.find(Platform.KAKAO, "헬멧3렙장인").orElseThrow().card()).isEqualTo(card);
        assertThat(store.image(Platform.KAKAO, "헬멧3렙장인").orElseThrow()).containsExactly(1, 2, 3);
        assertThat(store.find(Platform.STEAM, "헬멧3렙장인")).isEmpty();
        assertThat(store.find(Platform.KAKAO, "다른사람")).isEmpty();
    }
}
