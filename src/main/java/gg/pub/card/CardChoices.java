package gg.pub.card;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 공유할 꾸민 값 검사. 화면(player-card.js)의 주소 값과 같은 이름·값만 받는다.
 * 모르는 이름은 버리고, 아는 이름에 이상한 값이 오면 IllegalArgumentException.
 */
public final class CardChoices {

    private CardChoices() {
    }

    /** 하나만 고르는 것 */
    private static final Map<String, Set<String>> ONE = Map.of(
            "mic", Set.of("o", "x", "listen"),
            "time", Set.of("dawn", "morning", "lunch", "evening", "night"));

    /** 여러 개 고르는 것 (쉼표로 이어 붙임) */
    private static final Map<String, Set<String>> MANY = Map.of(
            "aim", Set.of("chicken", "rank", "social"),
            "find", Set.of("duo", "squad", "clan"),
            "role", Set.of("igl", "front", "second", "turret"));

    public static final Set<String> MAPS = Set.of("erangel", "miramar", "taego", "rondo");

    /** 맵 이미지 크기(2048) − 카드 배경 구역 크기(336×456) */
    static final int MAX_SX = 2048 - 336;
    static final int MAX_SY = 2048 - 456;

    public static Map<String, String> clean(Map<String, String> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        raw.forEach((k, v) -> {
            if (v == null || v.isBlank()) {
                return;
            }
            String value = v.trim().toLowerCase();
            if (ONE.containsKey(k)) {
                require(ONE.get(k).contains(value), k);
            } else if (MANY.containsKey(k)) {
                require(Arrays.stream(value.split(",")).allMatch(MANY.get(k)::contains), k);
            } else {
                return;   // 모르는 이름은 버림 (예전 카드에 저장된 옷 값도 여기서 버려진다)
            }
            out.put(k, value);
        });
        return out;
    }

    public static SharedCard.Background background(SharedCard.Background b) {
        require(b != null && MAPS.contains(b.map()), "background");
        require(b.sx() >= 0 && b.sx() <= MAX_SX && b.sy() >= 0 && b.sy() <= MAX_SY, "background");
        return b;
    }

    private static void require(boolean ok, String name) {
        if (!ok) {
            throw new IllegalArgumentException("카드 값이 올바르지 않아요: " + name);
        }
    }
}
