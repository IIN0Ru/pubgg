package gg.pub.web;

import java.util.Arrays;
import java.util.Optional;

/**
 * 검색 가능한 배그 플랫폼.
 * shard 값은 PUBG API 요청 주소에 그대로 들어가는 값이다. (예: /shards/steam/players)
 */
public enum Platform {

    STEAM("steam", "스팀"),
    KAKAO("kakao", "카카오");

    private final String shard;
    private final String label;

    Platform(String shard, String label) {
        this.shard = shard;
        this.label = label;
    }

    public String getShard() {
        return shard;
    }

    public String getLabel() {
        return label;
    }

    /** 폼에서 넘어온 값("steam", "kakao")을 enum으로 바꾼다. 모르는 값이면 빈 값. */
    public static Optional<Platform> fromShard(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(p -> p.shard.equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
