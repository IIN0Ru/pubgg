package gg.pub.card;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 공유 카드 만들기 횟수 제한 (같은 접속 주소가 짧은 시간에 너무 많이 만들지 못하게).
 * 로그인이 없고 그림이 DB 에 쌓이므로, 누가 반복해서 올려 DB 를 채우는 걸 막는 최소한의 장치다.
 * 서버 메모리에만 두는 단순한 방식이라 서버를 다시 켜면 초기화된다.
 */
public class ShareRateLimiter {

    private static final int CLEANUP_SIZE = 5_000;

    private final Clock clock;
    private final int max;
    private final Duration window;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** @param max 창 안에서 허용하는 횟수 (예: 10) @param window 창 길이 (예: 10분) */
    public ShareRateLimiter(Clock clock, int max, Duration window) {
        this.clock = clock;
        this.max = max;
        this.window = window;
    }

    /** @return 허용하면 true (횟수에 셈), 한도를 넘으면 false */
    public boolean tryAcquire(String who) {
        long now = clock.millis();
        long from = now - window.toMillis();
        if (hits.size() > CLEANUP_SIZE) {
            hits.values().removeIf(q -> {
                synchronized (q) {
                    return q.isEmpty() || q.peekLast() < from;
                }
            });
        }
        Deque<Long> q = hits.computeIfAbsent(who, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < from) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
