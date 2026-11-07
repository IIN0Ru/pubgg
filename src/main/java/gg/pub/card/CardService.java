package gg.pub.card;

import gg.pub.domain.Platform;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * B카드(공유 카드) 만들기·불러오기.
 *
 * A카드는 전적 화면에 늘 나오는 카드라 저장하지 않는다 (볼 때마다 최신 기록으로 계산).
 * B카드는 공유할 때만 만든다. 성향 태그를 고른 값과 그림을 서버에 두고, 공유 주소 /s/{id} 가 가리킨다.
 * 로그인이 없으니 "누가 만들었는지"는 따지지 않는다. 대신 같은 플레이어가 새로 공유하면 이전 그림은 비워진다 (CardStore 참고).
 */
@Service
public class CardService {

    /** 미리보기 그림 최대 크기 (720×960 PNG 는 보통 1MB 안쪽) */
    static final int MAX_PNG_BYTES = 4 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private final CardStore store;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CardService(CardStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * B카드를 만든다.
     *
     * @return 공유 주소에 쓸 ID
     * @throws IllegalArgumentException 값이나 그림이 이상할 때
     */
    public String share(Platform platform, String nickname, Map<String, String> choices,
                        SharedCard.Background background, byte[] png) {
        Map<String, String> clean = CardChoices.clean(choices);
        SharedCard.Background bg = CardChoices.background(background);
        requirePng(png);

        String id = newId();
        store.create(new SharedCard(id, platform.getShard(), nickname, clean, bg, clock.instant()), png);
        return id;
    }

    public Optional<SharedCard> find(String id) {
        return store.find(id);
    }

    public Optional<byte[]> image(String id) {
        return store.image(id);
    }

    public boolean hasImage(String id) {
        return store.hasImage(id);
    }

    static void requirePng(byte[] png) {
        if (png == null || png.length < PNG_SIGNATURE.length || png.length > MAX_PNG_BYTES) {
            throw new IllegalArgumentException("카드 그림이 올바르지 않아요");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (png[i] != PNG_SIGNATURE[i]) {
                throw new IllegalArgumentException("카드 그림이 PNG 가 아니에요");
            }
        }
    }

    /** 무작위 8바이트 → 11글자 (URL 에 그대로 쓸 수 있는 글자만) */
    private String newId() {
        byte[] b = new byte[8];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
