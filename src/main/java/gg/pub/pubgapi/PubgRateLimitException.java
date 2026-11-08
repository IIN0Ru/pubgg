package gg.pub.pubgapi;

/**
 * PUBG API 가 "요청이 너무 많다"(429)고 돌려줬을 때.
 * 기본 한도가 1분에 10번이라서 1분쯤 기다리면 다시 된다. JSON API 는 이걸 429 로 내려보낸다.
 */
public class PubgRateLimitException extends PubgApiException {

    /** 다시 시도하라고 안내하는 시간 (초) */
    public static final int RETRY_AFTER_SECONDS = 60;

    public PubgRateLimitException() {
        super("검색이 너무 많아요. 1분 뒤에 다시 시도해 주세요.");
    }
}
