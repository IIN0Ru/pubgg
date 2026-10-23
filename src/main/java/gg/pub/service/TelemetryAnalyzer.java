package gg.pub.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.TelemetryStats;
import gg.pub.domain.TelemetryStats.Bag;
import gg.pub.domain.TelemetryStats.Landing;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 텔레메트리(경기 이벤트 기록) JSON 을 읽어서 한 사람의 기록을 뽑는다.
 *
 * 텔레메트리는 이벤트가 시간순으로 들어 있는 큰 배열이다:
 *   [ { "_T": "LogItemEquip", "_D": "2026-10-19T14:12:31Z", "common": { "isGame": 1.5 }, ... }, ... ]
 *
 * 보는 이벤트와 계산하는 것:
 *  - LogItemEquip / LogItemUnequip           → 주무기를 슬롯에 넣고 있던 시간
 *  - LogPlayerUseThrowable                   → 투척 개수
 *  - LogPlayerPosition                       → 자기장 대비 내 위치 (운영, 자기장 운)
 *  - LogGameStatePeriodic                    → 자기장(다음 안전구역 흰 원, 지금 경계 파란 원)
 *  - LogPlayerKillV2                         → 내가 죽은 시각
 *  - LogParachuteLanding                     → 착지 지점 (지역 ID, 좌표)
 *  - LogItemPickup* / LogItemDrop / LogItemUse / LogItemPutToVehicleTrunk
 *                                            → 3페이즈 시작 순간의 회복·부스트템, 투척무기 개수와 주운 총알 수
 *
 * 내가 죽은 뒤의 기록은 보지 않는다. 이벤트를 하나씩 흘려 읽어서(stream) 큰 파일도 메모리를 적게 쓴다.
 */
@Component
public class TelemetryAnalyzer {

    /** 이 단계(isGame)부터 자기장 위치를 잰다. 3.0 = 3페이즈 */
    static final double ZONE_FROM_PHASE = 3.0;
    /** 자기장 순간(새 원·줄어들기 시작) 뒤 이 시간 안에 온 내 위치로 잰다. 위치 기록이 10초쯤 간격이라 넉넉히 */
    static final Duration ZONE_SAMPLE_WINDOW = Duration.ofSeconds(20);

    /** 회복·부스트템 */
    static final Set<String> HEAL_ITEMS = Set.of(
            "Item_Heal_Bandage_C", "Item_Heal_FirstAid_C", "Item_Heal_MedKit_C",
            "Item_Boost_EnergyDrink_C", "Item_Boost_PainKiller_C", "Item_Boost_AdrenalineSyringe_C");

    /** 투척무기 */
    static final Set<String> THROWABLE_ITEMS = Set.of(
            "Item_Weapon_Grenade_C", "Item_Weapon_Grenade_Warmode_C", "Item_Weapon_SmokeBomb_C",
            "Item_Weapon_Molotov_C", "Item_Weapon_FlashBang_C", "Item_Weapon_StickyGrenade_C",
            "Item_Weapon_BluezoneGrenade_C", "Item_Weapon_DecoyGrenade_C");

    /** 총알 (조명탄·박격포탄·석궁 화살·40mm 연막탄은 제외) */
    static final Set<String> AMMO_ITEMS = Set.of(
            "Item_Ammo_556mm_C", "Item_Ammo_762mm_C", "Item_Ammo_9mm_C", "Item_Ammo_45ACP_C",
            "Item_Ammo_300Magnum_C", "Item_Ammo_12Guage_C", "Item_Ammo_12GuageSlug_C", "Item_Ammo_57mm_C");

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
        private Integer myTeam;
        private Instant deathTime;
        private Instant lastTime;

        // 주무기: itemId → 슬롯에 넣은 시각들 (같은 총 두 자루일 수도 있어서 여러 개)
        private final Map<String, Deque<Instant>> equipped = new HashMap<>();
        private final Map<String, Long> weaponSeconds = new HashMap<>();
        private int throwables;

        // 자기장 (흰 원 = 다음 안전구역, 파란 원 = 지금 줄어드는 경계)
        private double zoneX, zoneY, zoneRadius;
        private double safeRadius;
        private boolean shrinking;
        /** 흰 원이 새로 떴을 때 / 파란 원이 줄기 시작할 때, 다음 내 위치로 잴 원 */
        private Zone pendingLuck, pendingEdge;
        private double zoneRatioSum;
        private int zoneSamples;
        private double zoneLuckSum;
        private int zoneLuckSamples;

        // 착지
        private Landing landing;

        // 가방 (3페이즈 시작 순간에 기록)
        private int heldHeals;
        private int heldThrowables;
        private int ammoPicked;
        private Bag phase3;

        record Zone(double x, double y, double radius, Instant time) {
        }

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
            learnMyTeam(e);

            // 3페이즈가 처음 시작된 순간의 가방 상태 기록 (이 이벤트를 처리하기 전 상태)
            if (phase3 == null && e.path("common").path("isGame").asDouble(0) >= ZONE_FROM_PHASE) {
                phase3 = new Bag(Math.max(0, heldHeals), Math.max(0, heldThrowables), ammoPicked);
            }

            switch (e.path("_T").asText("")) {
                case "LogItemEquip" -> onEquip(e, time);
                case "LogItemUnequip" -> onUnequip(e, time);
                case "LogPlayerUseThrowable" -> {
                    if (isMe(e.path("attacker"))) {
                        throwables++;
                        heldThrowables--;
                    }
                }
                case "LogPlayerPosition" -> onPosition(e, time);
                case "LogGameStatePeriodic" -> onGameState(e, time);
                case "LogPlayerKillV2" -> {
                    if (isMe(e.path("victim"))) {
                        deathTime = time;
                    }
                }
                case "LogItemPickup", "LogItemPickupFromLootbox", "LogItemPickupFromCarepackage",
                     "LogItemPickupFromCustomPackage", "LogItemPickupFromVehicleTrunk" -> {
                    if (isMe(e.path("character"))) {
                        onBagChange(e.path("item"), +1);
                    }
                }
                case "LogItemDrop", "LogItemPutToVehicleTrunk" -> {
                    if (isMe(e.path("character"))) {
                        onBagChange(e.path("item"), -1);
                    }
                }
                case "LogItemUse" -> {
                    if (isMe(e.path("character"))
                            && HEAL_ITEMS.contains(e.path("item").path("itemId").asText(""))) {
                        heldHeals--;   // 한 번 쓰면 하나 줄어든다
                    }
                }
                case "LogParachuteLanding" -> {
                    if (landing == null && isMe(e.path("character"))) {
                        JsonNode c = e.path("character");
                        List<String> zones = new ArrayList<>();
                        c.path("zone").forEach(z -> zones.add(z.asText()));
                        landing = new Landing(List.copyOf(zones),
                                c.path("location").path("x").asDouble(),
                                c.path("location").path("y").asDouble());
                    }
                }
                default -> {
                    // 다른 이벤트는 지금은 쓰지 않는다
                }
            }
        }

        // ---------- 주무기 ----------

        private void onEquip(JsonNode e, Instant time) {
            if (!isMe(e.path("character"))) {
                return;
            }
            String itemId = e.path("item").path("itemId").asText("");
            if (WeaponCatalog.isPrimary(itemId)) {
                equipped.computeIfAbsent(itemId, k -> new ArrayDeque<>()).push(time);
            }
        }

        private void onUnequip(JsonNode e, Instant time) {
            if (!isMe(e.path("character"))) {
                return;
            }
            String itemId = e.path("item").path("itemId").asText("");
            Deque<Instant> starts = equipped.get(itemId);
            if (starts != null && !starts.isEmpty()) {
                addWeaponTime(itemId, starts.pop(), time);
            }
        }

        // ---------- 가방 ----------

        /** 줍기(+1)·버리기(-1) 때 회복템·투척·총알 개수를 바꾼다. 개수는 item.stackCount */
        private void onBagChange(JsonNode item, int sign) {
            String itemId = item.path("itemId").asText("");
            int count = Math.max(1, item.path("stackCount").asInt(1));
            if (HEAL_ITEMS.contains(itemId)) {
                heldHeals += sign * count;
            } else if (THROWABLE_ITEMS.contains(itemId)) {
                heldThrowables += sign * count;
            } else if (AMMO_ITEMS.contains(itemId) && sign > 0) {
                ammoPicked += count;   // 총알은 주운 양만 센다
            }
        }

        // ---------- 위치, 자기장 ----------

        private void onPosition(JsonNode e, Instant time) {
            JsonNode character = e.path("character");
            String playerId = id(character);
            if (playerId.isEmpty()) {
                return;
            }
            if (!playerId.equals(me)) {
                return;
            }
            double x = character.path("location").path("x").asDouble();
            double y = character.path("location").path("y").asDouble();
            // 자기장 운: 흰 원이 새로 뜬 직후 내 위치. 중심 1, 경계·밖 0
            if (pendingLuck != null) {
                if (fresh(pendingLuck, time)) {
                    zoneLuckSum += Math.max(0, 1 - distance(x, y, pendingLuck.x(), pendingLuck.y()) / pendingLuck.radius());
                    zoneLuckSamples++;
                }
                pendingLuck = null;
            }
            // 운영: 파란 원이 줄어들기 시작한 직후 내 위치. 중심 0, 경계 1 (밖은 1.5까지)
            if (pendingEdge != null) {
                if (fresh(pendingEdge, time)) {
                    zoneRatioSum += Math.min(1.5, distance(x, y, pendingEdge.x(), pendingEdge.y()) / pendingEdge.radius());
                    zoneSamples++;
                }
                pendingEdge = null;
            }
        }

        /** 자기장 순간 뒤 이 시간 안에 온 내 위치만 쓴다 (그 사이 기록이 없으면 그 페이즈는 건너뜀) */
        private static boolean fresh(Zone zone, Instant time) {
            return Duration.between(zone.time(), time).compareTo(ZONE_SAMPLE_WINDOW) <= 0;
        }

        private void onGameState(JsonNode e, Instant time) {
            JsonNode state = e.path("gameState");
            boolean counted = e.path("common").path("isGame").asDouble(0) >= ZONE_FROM_PHASE;

            double radius = state.path("poisonGasWarningRadius").asDouble(0);
            if (radius > 0) {
                double x = state.path("poisonGasWarningPosition").path("x").asDouble();
                double y = state.path("poisonGasWarningPosition").path("y").asDouble();
                boolean newCircle = Math.abs(radius - zoneRadius) > 1
                        || distance(x, y, zoneX, zoneY) > 1;
                zoneX = x;
                zoneY = y;
                zoneRadius = radius;
                if (newCircle) {
                    // 새 원이 뜬 순간: 파란 원은 방금 다 줄어든 상태라 여기서 기준만 새로 잡는다
                    shrinking = false;
                    safeRadius = state.path("safetyZoneRadius").asDouble(safeRadius);
                    if (counted) {
                        pendingLuck = new Zone(x, y, radius, time);
                    }
                    return;
                }
            }

            double safe = state.path("safetyZoneRadius").asDouble(0);
            if (safe > 0) {
                boolean smaller = safeRadius > 0 && safe < safeRadius - 1;
                if (smaller && !shrinking && zoneRadius > 0 && counted) {
                    pendingEdge = new Zone(zoneX, zoneY, zoneRadius, time);
                }
                shrinking = smaller;
                safeRadius = safe;
            }
        }

        // ---------- 결과 ----------

        TelemetryStats result() {
            // 죽을 때(또는 경기 끝날 때)까지 슬롯에 남아 있던 총의 시간을 마감
            Instant end = deathTime != null ? deathTime : lastTime;
            if (end != null) {
                equipped.forEach((itemId, starts) -> starts.forEach(start -> addWeaponTime(itemId, start, end)));
            }
            equipped.clear();

            return new TelemetryStats(
                    Map.copyOf(weaponSeconds),
                    throwables,
                    zoneRatioSum,
                    zoneSamples,
                    zoneLuckSum,
                    zoneLuckSamples,
                    landing,
                    phase3);
        }

        // ---------- 도우미 ----------

        /** 이벤트에 나온 사람 중 나를 찾으면 내 팀 번호를 기억 */
        private void learnMyTeam(JsonNode e) {
            if (myTeam != null) {
                return;
            }
            for (String field : new String[]{"character", "attacker", "victim"}) {
                JsonNode c = e.path(field);
                if (isMe(c) && c.has("teamId")) {
                    myTeam = c.path("teamId").asInt();
                    return;
                }
            }
        }

        private void addWeaponTime(String itemId, Instant from, Instant to) {
            long s = Math.max(0, Duration.between(from, to).toSeconds());
            weaponSeconds.merge(itemId, s, Long::sum);
        }

        private boolean isMe(JsonNode character) {
            return me.equals(id(character));
        }

        private static String id(JsonNode character) {
            return character.path("accountId").asText("");
        }

        private static double distance(double x1, double y1, double x2, double y2) {
            return Math.hypot(x1 - x2, y1 - y2);
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
