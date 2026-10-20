package gg.pub.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.TelemetryStats;
import gg.pub.domain.WeaponCatalog;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 텔레메트리(경기 이벤트 기록) JSON 을 읽어서 한 사람의 기록을 뽑는다.
 *
 * 텔레메트리는 이벤트가 시간순으로 들어 있는 큰 배열이다:
 *   [ { "_T": "LogItemEquip", "_D": "2026-10-19T14:12:31Z", "character": {...}, "item": {...} }, ... ]
 *
 * 여기서 보는 이벤트:
 *  - LogItemEquip / LogItemUnequip : 주무기를 슬롯에 넣은 시각 / 뺀 시각 → 넣고 있던 시간
 *  - LogPlayerUseThrowable         : 투척무기를 던짐 → 개수
 *  - LogPlayerKillV2               : 내가 죽은 시각 → 그때까지 들고 있던 총의 시간을 마감
 *
 * 이벤트를 하나씩 흘려 읽어서(stream) 수 MB 짜리 파일도 메모리를 적게 쓴다.
 */
@Component
public class TelemetryAnalyzer {

    private final ObjectMapper mapper;

    public TelemetryAnalyzer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public TelemetryStats analyze(InputStream json, String accountId) {
        Tracker tracker = new Tracker(accountId);

        try (JsonParser parser = mapper.getFactory().createParser(json)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("텔레메트리 형식이 배열이 아니에요");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode event = mapper.readTree(parser);
                tracker.handle(event);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return tracker.result();
    }

    /** 이벤트를 하나씩 받아서 기록을 쌓아가는 내부 도우미 */
    private static final class Tracker {

        private final String me;
        // 지금 슬롯에 들어 있는 총: itemId → 넣은 시각들 (같은 총 두 자루일 수도 있어서 여러 개)
        private final Map<String, Deque<Instant>> equipped = new HashMap<>();
        private final Map<String, Long> seconds = new HashMap<>();
        private int throwables;
        private Instant deathTime;
        private Instant lastTime;

        Tracker(String accountId) {
            this.me = accountId;
        }

        void handle(JsonNode e) {
            Instant time = parseTime(e.path("_D").asText(null));
            if (time != null) {
                lastTime = time;
            }
            if (deathTime != null || time == null) {
                return;   // 죽은 뒤 기록은 보지 않는다
            }

            switch (e.path("_T").asText("")) {
                case "LogItemEquip" -> {
                    if (isMe(e.path("character"))) {
                        String itemId = e.path("item").path("itemId").asText("");
                        if (WeaponCatalog.isPrimary(itemId)) {
                            equipped.computeIfAbsent(itemId, k -> new ArrayDeque<>()).push(time);
                        }
                    }
                }
                case "LogItemUnequip" -> {
                    if (isMe(e.path("character"))) {
                        String itemId = e.path("item").path("itemId").asText("");
                        Deque<Instant> starts = equipped.get(itemId);
                        if (starts != null && !starts.isEmpty()) {
                            add(itemId, starts.pop(), time);
                        }
                    }
                }
                case "LogPlayerUseThrowable" -> {
                    if (isMe(e.path("attacker"))) {
                        throwables++;
                    }
                }
                case "LogPlayerKillV2" -> {
                    if (isMe(e.path("victim"))) {
                        deathTime = time;
                    }
                }
                default -> {
                    // 다른 이벤트는 지금은 쓰지 않는다
                }
            }
        }

        TelemetryStats result() {
            // 죽을 때(또는 경기 끝날 때)까지 슬롯에 남아 있던 총의 시간을 마감
            Instant end = deathTime != null ? deathTime : lastTime;
            if (end != null) {
                equipped.forEach((itemId, starts) -> starts.forEach(start -> add(itemId, start, end)));
            }
            equipped.clear();
            return new TelemetryStats(Map.copyOf(seconds), throwables);
        }

        private void add(String itemId, Instant from, Instant to) {
            long s = Math.max(0, Duration.between(from, to).toSeconds());
            seconds.merge(itemId, s, Long::sum);
        }

        private boolean isMe(JsonNode character) {
            return me.equals(character.path("accountId").asText(null));
        }

        private static Instant parseTime(String text) {
            try {
                return text == null ? null : Instant.parse(text);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
