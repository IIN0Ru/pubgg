package gg.pub.card;

import gg.pub.domain.Platform;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * 플레이어 카드 저장·불러오기.
 *
 * 로그인이 없어서 "처음 저장한 브라우저만 수정"으로 막는다:
 *  - 처음 저장하면 무작위 수정 열쇠를 만들어 돌려주고 (브라우저가 localStorage 에 보관), 서버에는 열쇠의 해시만 남긴다
 *  - 다음 저장부터는 같은 열쇠를 보내야 덮어쓸 수 있다
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

    public Optional<SavedCard> find(Platform platform, String nickname) {
        return store.find(platform, nickname).map(CardStore.Stored::card);
    }

    public Optional<byte[]> image(Platform platform, String nickname) {
        return store.image(platform, nickname);
    }

    /**
     * 저장한다.
     *
     * @param token 이 브라우저가 갖고 있는 수정 열쇠 (처음이면 null)
     * @return 수정 열쇠 (처음 저장이면 새로 만든 것, 아니면 받은 것 그대로)
     * @throws CardLockedException 다른 브라우저가 먼저 저장한 카드일 때
     * @throws IllegalArgumentException 값이나 그림이 이상할 때
     */
    public String save(Platform platform, String nickname, Map<String, String> choices,
                       SavedCard.Background background, byte[] png, String token) {
        Map<String, String> clean = CardChoices.clean(choices);
        SavedCard.Background bg = CardChoices.background(background);
        requirePng(png);

        Optional<CardStore.Stored> existing = store.find(platform, nickname);
        String key = token;
        if (existing.isPresent()) {
            if (token == null || !hash(token).equals(existing.get().tokenHash())) {
                throw new CardLockedException();
            }
        } else {
            key = newToken();
        }
        SavedCard card = new SavedCard(platform.getShard(), nickname, clean, bg, clock.instant());
        store.write(platform, nickname, new CardStore.Stored(card, hash(key)), png);
        return key;
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

    private String newToken() {
        byte[] b = new byte[24];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
