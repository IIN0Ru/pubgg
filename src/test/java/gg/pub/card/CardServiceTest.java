package gg.pub.card;

import gg.pub.domain.Platform;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 카드 저장: 수정 열쇠, 값 검사 */
class CardServiceTest {

    /** 메모리에만 저장하는 가짜 저장소 */
    static class MemoryStore implements CardStore {
        final Map<String, Stored> cards = new HashMap<>();
        final Map<String, byte[]> images = new HashMap<>();

        private static String key(Platform p, String n) {
            return p.getShard() + ":" + n.toLowerCase();
        }

        @Override
        public Optional<Stored> find(Platform platform, String nickname) {
            return Optional.ofNullable(cards.get(key(platform, nickname)));
        }

        @Override
        public Optional<byte[]> image(Platform platform, String nickname) {
            return Optional.ofNullable(images.get(key(platform, nickname)));
        }

        @Override
        public void write(Platform platform, String nickname, Stored card, byte[] png) {
            cards.put(key(platform, nickname), card);
            images.put(key(platform, nickname), png);
        }
    }

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0};
    static final SavedCard.Background BG = new SavedCard.Background("erangel", 100, 200);

    final MemoryStore store = new MemoryStore();
    final CardService service = new CardService(store,
            Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void 처음_저장하면_열쇠를_주고_같은_열쇠로만_다시_저장된다() {
        String token = service.save(Platform.STEAM, "N0-RU", Map.of("mic", "o"), BG, PNG, null);

        assertThat(token).isNotBlank();
        assertThat(store.cards.get("steam:n0-ru").tokenHash()).isEqualTo(CardService.hash(token));   // 열쇠 자체는 저장 안 함
        assertThat(service.find(Platform.STEAM, "N0-RU").orElseThrow().choices()).containsEntry("mic", "o");

        assertThat(service.save(Platform.STEAM, "N0-RU", Map.of("mic", "x"), BG, PNG, token)).isEqualTo(token);
        assertThat(service.find(Platform.STEAM, "n0-ru").orElseThrow().choices()).containsEntry("mic", "x");

        assertThatThrownBy(() -> service.save(Platform.STEAM, "N0-RU", Map.of(), BG, PNG, "다른열쇠"))
                .isInstanceOf(CardLockedException.class);
        assertThatThrownBy(() -> service.save(Platform.STEAM, "N0-RU", Map.of(), BG, PNG, null))
                .isInstanceOf(CardLockedException.class);
    }

    @Test
    void 꾸민_값은_아는_이름만_남기고_이상한_값은_거절() {
        Map<String, String> raw = new HashMap<>();
        raw.put("aim", "chicken,rank");
        raw.put("tee", "C8423A");
        raw.put("outer", "hoodie");
        raw.put("hack", "<script>");
        raw.put("bc", "");
        assertThat(CardChoices.clean(raw))
                .containsExactlyInAnyOrderEntriesOf(Map.of("aim", "chicken,rank", "tee", "c8423a", "outer", "hoodie"));

        assertThatThrownBy(() -> CardChoices.clean(Map.of("suit", "bear")))   // 북극곰은 뺐음
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CardChoices.clean(Map.of("role", "igl,boss")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CardChoices.clean(Map.of("oc", "red")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 배경과_그림을_검사한다() {
        assertThatThrownBy(() -> service.save(Platform.STEAM, "A", Map.of(),
                new SavedCard.Background("sanhok", 0, 0), PNG, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(Platform.STEAM, "A", Map.of(),
                new SavedCard.Background("erangel", 5000, 0), PNG, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(Platform.STEAM, "A", Map.of(), BG, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
