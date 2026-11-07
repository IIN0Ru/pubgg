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

/** 공유 카드 만들기: ID, 값 검사 */
class CardServiceTest {

    /** 메모리에만 저장하는 가짜 저장소 */
    static class MemoryStore implements CardStore {
        final Map<String, SharedCard> cards = new HashMap<>();
        final Map<String, byte[]> images = new HashMap<>();

        @Override
        public void create(SharedCard card, byte[] png) {
            cards.put(card.id(), card);
            images.put(card.id(), png);
        }

        @Override
        public Optional<SharedCard> find(String id) {
            return Optional.ofNullable(cards.get(id));
        }

        @Override
        public Optional<byte[]> image(String id) {
            return Optional.ofNullable(images.get(id));
        }

        @Override
        public boolean hasImage(String id) {
            return images.containsKey(id);
        }
    }

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0};
    static final SharedCard.Background BG = new SharedCard.Background("erangel", 100, 200);

    final MemoryStore store = new MemoryStore();
    final CardService service = new CardService(store,
            Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void 공유하면_ID를_주고_그_ID로_꾸민_값과_그림을_다시_읽는다() {
        String id = service.share(Platform.STEAM, "N0-RU", Map.of("mic", "o", "time", "night"), BG, PNG);

        assertThat(SharedCard.isValidId(id)).isTrue();
        SharedCard card = service.find(id).orElseThrow();
        assertThat(card.id()).isEqualTo(id);
        assertThat(card.platform()).isEqualTo("steam");
        assertThat(card.nickname()).isEqualTo("N0-RU");
        assertThat(card.choices()).containsEntry("mic", "o").containsEntry("time", "night");
        assertThat(card.background()).isEqualTo(BG);
        assertThat(card.createdAt()).isEqualTo(Instant.parse("2026-11-07T12:00:00Z"));
        assertThat(service.hasImage(id)).isTrue();
        assertThat(service.image(id).orElseThrow()).isEqualTo(PNG);
    }

    @Test
    void 공유할_때마다_다른_ID가_나온다() {
        String a = service.share(Platform.STEAM, "N0-RU", Map.of(), BG, PNG);
        String b = service.share(Platform.STEAM, "N0-RU", Map.of(), BG, PNG);

        assertThat(a).isNotEqualTo(b);
        assertThat(service.find(a)).isPresent();   // 서비스는 지우지 않는다 (이전 그림 비우기는 저장소가 한다)
        assertThat(service.find(b)).isPresent();
    }

    @Test
    void 없는_ID는_빈_값() {
        assertThat(service.find("nothere123")).isEmpty();
        assertThat(service.image("nothere123")).isEmpty();
        assertThat(service.hasImage("nothere123")).isFalse();
    }

    @Test
    void 꾸민_값은_아는_이름만_남기고_이상한_값은_거절() {
        Map<String, String> raw = new HashMap<>();
        raw.put("aim", "chicken,rank");
        raw.put("mic", "O");
        raw.put("time", "Evening");
        raw.put("outer", "hoodie");   // 캐릭터 옷은 없어졌다: 예전 값이 와도 조용히 버린다
        raw.put("tee", "c8423a");
        raw.put("hack", "<script>");
        raw.put("find", "");
        assertThat(CardChoices.clean(raw))
                .containsExactlyInAnyOrderEntriesOf(Map.of("aim", "chicken,rank", "mic", "o", "time", "evening"));

        assertThatThrownBy(() -> CardChoices.clean(Map.of("role", "igl,boss")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CardChoices.clean(Map.of("mic", "yes")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 접속_시간대는_하나만_고르고_아는_값만_받는다() {
        for (String t : new String[]{"dawn", "morning", "lunch", "evening", "night"}) {
            assertThat(CardChoices.clean(Map.of("time", t))).containsEntry("time", t);
        }
        assertThatThrownBy(() -> CardChoices.clean(Map.of("time", "night,dawn")))   // 여러 개는 안 됨
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CardChoices.clean(Map.of("time", "afternoon")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 배경과_그림을_검사한다() {
        assertThatThrownBy(() -> service.share(Platform.STEAM, "A", Map.of(),
                new SharedCard.Background("sanhok", 0, 0), PNG)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.share(Platform.STEAM, "A", Map.of(),
                new SharedCard.Background("erangel", 5000, 0), PNG)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.share(Platform.STEAM, "A", Map.of(), BG, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.share(Platform.STEAM, "A", Map.of(), BG, new byte[CardService.MAX_PNG_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.cards).isEmpty();   // 검사에 걸리면 아무것도 저장하지 않는다
    }

    @Test
    void ID_모양을_검사한다() {
        assertThat(SharedCard.isValidId("abc123XYZ_-")).isTrue();
        assertThat(SharedCard.isValidId("../etc/passwd")).isFalse();
        assertThat(SharedCard.isValidId("a/b")).isFalse();
        assertThat(SharedCard.isValidId("short")).isFalse();
        assertThat(SharedCard.isValidId("x".repeat(33))).isFalse();
        assertThat(SharedCard.isValidId(null)).isFalse();
    }
}
