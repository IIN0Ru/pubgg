package gg.pub.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.replay.MapInfo;
import gg.pub.domain.replay.Replay;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 텔레메트리(경기 상세 기록)를 읽어서 2D 리플레이 데이터(Replay)로 줄인다.
 *
 * TelemetryAnalyzer 와 같은 방식: 수 MB 짜리 JSON 배열을 한 번에 올리지 않고 이벤트를 하나씩 읽는다.
 *
 * 쓰는 이벤트:
 *  - LogMatchStart              경기 시작 시각 (모든 시각을 여기서부터 초로 센다)
 *  - LogPlayerPosition          전원 위치 (약 10초마다) + 그때 탄 차량·보트
 *  - LogGameStatePeriodic       자기장: safetyZone(지금 경계, 파란 원) / poisonGasWarning(다음 안전구역, 흰 원)
 *  - LogPlayerMakeGroggy        기절 (무엇으로 했는지도: 투척·박격포·판처·근접. 총은 따로 안 적음)
 *  - LogPlayerKillV2            킬 (죽은 시각도 여기서)
 *  - LogPlayerRevive            부활
 *  - LogParachuteLanding        착지 (우리 팀만)
 *  - LogVehicleRide / Leave     차량 타고 내림 (우리 팀만). 비행기에서 뛰어내린 기록으로 비행기 경로도 구함
 *  - LogCarePackageLand         보급상자 떨어진 곳
 *  - LogPlayerTakeDamage        총 명중 (우리 팀이 맞힘 / 우리 팀이 맞음). 총알 방향은 기록에 없어서 맞은 것만
 *                               + 우리 팀 폭발물(투척·박격포·판처)이 적을 맞힌 것 → 터진 곳 추정
 *  - LogPlayerUseThrowable      우리 팀이 던진 곳 (attackId 로 위 피해와 이음)
 *  - LogItemUse                 우리 팀 회복·부스트템 사용
 *
 * 좌표는 cm → m, 시각은 초 단위 정수로 줄인다.
 */
@Component
public class ReplayBuilder {

    /** 우리 팀 기절·킬이 이 시간 안에 이어지면 같은 교전 (TelemetryAnalyzer 의 교전 기준과 같게) */
    static final int FIGHT_GAP_SECONDS = 30;
    /** 같은 한 발의 피해로 묶는 시간. 화염병은 불이 오래 타서 길게 */
    static final int BLAST_GAP_SECONDS = 2;
    static final int FIRE_GAP_SECONDS = 15;
    /** 던진 기록과 attackId 가 안 맞을 때, 피해 전 이 시간 안에 같은 사람이 던진 것을 대신 쓴다 */
    static final int THROW_LOOKBACK_SECONDS = 10;

    /** 회복·부스트템 → 이름 */
    static final Map<String, String> HEAL_ITEMS = Map.of(
            "Item_Heal_Bandage_C", "붕대",
            "Item_Heal_FirstAid_C", "구급상자",
            "Item_Heal_MedKit_C", "의료키트",
            "Item_Boost_EnergyDrink_C", "에너지 드링크",
            "Item_Boost_PainKiller_C", "진통제",
            "Item_Boost_AdrenalineSyringe_C", "아드레날린");

    /** 근접 무기 (damageCauserName 앞부분) → 이름 */
    static final Map<String, String> MELEE_WEAPONS = Map.of(
            "WeapPan", "프라이팬",
            "WeapMachete", "마체테",
            "WeapSickle", "낫",
            "WeapCowbar", "쇠지렛대");

    private final ObjectMapper mapper;

    public ReplayBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Replay build(InputStream json, String matchId, MapInfo map, String accountId) {
        State s = new State(accountId);
        try (JsonParser parser = mapper.getFactory().createParser(json)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("텔레메트리 형식이 배열이 아니에요");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                s.handle(mapper.readTree(parser));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return s.result(matchId, map);
    }

    /** 이벤트를 하나씩 받아서 쌓아가는 내부 도우미 */
    private static final class State {

        private final String me;
        private Instant start;
        private int lastT;
        private Integer myTeam;

        // 이동 기록: 계정 ID → 쌓는 중인 기록 (넣은 순서 유지)
        private final Map<String, TrackBuilder> tracks = new LinkedHashMap<>();
        private final List<Replay.Zone> zones = new ArrayList<>();
        private final List<Replay.Phase> phases = new ArrayList<>();
        private int lastNextR = -1;
        private final List<Replay.Event> events = new ArrayList<>();
        private final List<Replay.Hit> hits = new ArrayList<>();
        private final List<BlastBuilder> blasts = new ArrayList<>();
        private final List<Replay.Heal> heals = new ArrayList<>();
        // 우리 팀이 던진 것: 던진 시각·위치 (attackId 로 찾기 + 사람별 최근 순)
        private final Map<Integer, Throw> throwsById = new HashMap<>();
        private final List<Throw> throwList = new ArrayList<>();
        // 비행기에서 뛰어내린 곳들 (시각 순)
        private final List<int[]> jumps = new ArrayList<>();

        State(String me) {
            this.me = me;
        }

        void handle(JsonNode e) {
            Instant time = parseTime(e.path("_D").asText(null));
            String type = e.path("_T").asText("");
            if (time == null) {
                return;
            }
            if (start == null) {
                if (!type.equals("LogMatchStart")) {
                    return;   // 경기 시작 전(대기실) 기록은 쓰지 않는다
                }
                start = time;
                return;
            }
            int t = (int) Math.max(0, Duration.between(start, time).toSeconds());
            lastT = Math.max(lastT, t);

            switch (type) {
                case "LogPlayerPosition" -> onPosition(e.path("character"), e.path("vehicle"), t);
                case "LogGameStatePeriodic" -> onGameState(e.path("gameState"), t);
                case "LogPlayerMakeGroggy" -> {
                    JsonNode attacker = e.path("attacker");
                    JsonNode victim = e.path("victim");
                    String cause = weaponCause(e.path("damageTypeCategory").asText(""));
                    addCombat(t, "knock", attacker, victim,
                            (name(attacker) == null ? name(victim) + " 기절" : name(attacker) + " → " + name(victim) + " 기절")
                                    + causeText(cause, e.path("damageCauserName").asText("")), cause);
                }
                case "LogPlayerKillV2" -> onKill(e, t);
                case "LogPlayerRevive" -> {
                    JsonNode reviver = e.path("reviver");
                    JsonNode victim = e.path("victim");
                    boolean ours = isMyTeam(reviver) || isMyTeam(victim);
                    if (ours) {
                        events.add(new Replay.Event(t, "revive", m(x(victim)), m(y(victim)), name(reviver), name(victim),
                                true, name(reviver) + "가 " + name(victim) + " 살림", null));
                    }
                }
                case "LogParachuteLanding" -> {
                    JsonNode c = e.path("character");
                    learnTeam(c);
                    if (isMyTeam(c)) {
                        events.add(new Replay.Event(t, "landing", m(x(c)), m(y(c)), name(c), null, true, name(c) + " 착지", null));
                    }
                }
                case "LogVehicleRide", "LogVehicleLeave" -> onVehicle(e, t, type.equals("LogVehicleRide"));
                case "LogPlayerTakeDamage" -> {
                    onDamage(e, t);
                    onBlastDamage(e, t);
                }
                case "LogPlayerUseThrowable" -> onThrow(e, t);
                case "LogItemUse" -> {
                    JsonNode c = e.path("character");
                    String item = HEAL_ITEMS.get(e.path("item").path("itemId").asText(""));
                    learnTeam(c);
                    if (item != null && isMyTeam(c)) {
                        heals.add(new Replay.Heal(t, name(c), m(x(c)), m(y(c)), item));
                    }
                }
                case "LogCarePackageLand" -> {
                    JsonNode loc = e.path("itemPackage").path("location");
                    events.add(new Replay.Event(t, "package", m(loc.path("x").asDouble()), m(loc.path("y").asDouble()),
                            null, null, false, "보급상자 떨어짐", null));
                }
                default -> {
                    // 나머지는 리플레이에 쓰지 않는다
                }
            }
        }

        // ---------- 위치 ----------

        private void onPosition(JsonNode c, JsonNode vehicle, int t) {
            String id = c.path("accountId").asText("");
            if (id.isEmpty()) {
                return;
            }
            learnTeam(c);
            tracks.computeIfAbsent(id, k -> new TrackBuilder(id, name(c), c.path("teamId").asInt(-1)))
                    .add(t, m(x(c)), m(y(c)), vehicleKind(vehicle));
        }

        /** 위치 기록의 vehicle → 걸어서 / 차량 / 보트. 비행기·낙하산은 걸어서로 본다 (차량 아이콘을 그리지 않게) */
        static int vehicleKind(JsonNode vehicle) {
            String type = vehicle == null ? "" : vehicle.path("vehicleType").asText("");
            return switch (type) {
                case "", "TransportAircraft", "Parachute" -> Replay.Track.ON_FOOT;
                case "FloatingVehicle" -> Replay.Track.BOAT;
                default -> Replay.Track.CAR;   // WheeledVehicle, Motorbike 등
            };
        }

        private void learnTeam(JsonNode c) {
            if (myTeam == null && me.equals(c.path("accountId").asText(null))) {
                myTeam = c.path("teamId").asInt(-1);
            }
        }

        // ---------- 자기장 ----------

        private void onGameState(JsonNode g, int t) {
            JsonNode safe = g.path("safetyZonePosition");
            JsonNode next = g.path("poisonGasWarningPosition");
            int safeR = m(g.path("safetyZoneRadius").asDouble());
            int nextR = m(g.path("poisonGasWarningRadius").asDouble());
            zones.add(new Replay.Zone(t,
                    m(safe.path("x").asDouble()), m(safe.path("y").asDouble()), safeR,
                    m(next.path("x").asDouble()), m(next.path("y").asDouble()), nextR));
            // 다음 안전구역(흰 원)이 새로 작아지면 새 페이즈
            if (nextR > 0 && (lastNextR < 0 || nextR < lastNextR - 1)) {
                phases.add(new Replay.Phase(phases.size() + 1, t));
            }
            if (nextR > 0) {
                lastNextR = nextR;
            }
        }

        // ---------- 교전 ----------

        private void onKill(JsonNode e, int t) {
            JsonNode victim = e.path("victim");
            // 킬은 마무리한 사람(finisher) 기준, 없으면 killer
            JsonNode killer = e.path("finisher").path("accountId").asText("").isEmpty() ? e.path("killer") : e.path("finisher");
            String victimId = victim.path("accountId").asText("");
            TrackBuilder tb = tracks.get(victimId);
            if (tb != null) {
                tb.diedAt = t;
            }
            // 마무리한 피해(없으면 킬 피해)가 무엇이었는지
            JsonNode info = e.path("finishDamageInfo").path("damageTypeCategory").asText("").isEmpty()
                    ? e.path("killerDamageInfo") : e.path("finishDamageInfo");
            String cause = weaponCause(info.path("damageTypeCategory").asText(""));
            String text = (name(killer) == null ? name(victim) + " 사망" : name(killer) + " → " + name(victim) + " 킬")
                    + causeText(cause, info.path("damageCauserName").asText(""));
            addCombat(t, "kill", killer, victim, text, cause);
        }

        private void addCombat(int t, String type, JsonNode attacker, JsonNode victim, String text, String cause) {
            learnTeam(attacker);
            learnTeam(victim);
            boolean ours = isMyTeam(attacker) || isMyTeam(victim);
            events.add(new Replay.Event(t, type, m(x(victim)), m(y(victim)), name(attacker), name(victim), ours, text,
                    cause));
        }

        /** 피해 종류 → 짧은 이름. 총이나 그 밖의 것(자기장, 차량 ...)은 null */
        static String weaponCause(String damageTypeCategory) {
            return switch (damageTypeCategory) {
                case "Damage_Explosion_Grenade" -> "grenade";
                case "Damage_Molotov" -> "molotov";
                case "Damage_Explosion_C4" -> "c4";
                case "Damage_Explosion_StickyBomb" -> "sticky";
                case "Damage_Explosion_Mortar" -> "mortar";
                case "Damage_Explosion_PanzerFaustWarhead", "Damage_Explosion_PanzerFaustWarheadVehicleArmorPenetration",
                     "Damage_Explosion_PanzerFaustBackBlast" -> "panzer";
                case "Damage_Melee" -> "melee";
                case "Damage_MeleeThrow" -> "meleeThrow";
                case "Damage_Punch" -> "punch";
                default -> null;
            };
        }

        /** 터지는 것 (터진 곳 이펙트를 그리는 것) */
        static boolean isBlast(String cause) {
            return cause != null && switch (cause) {
                case "grenade", "molotov", "c4", "sticky", "mortar", "panzer" -> true;
                default -> false;
            };
        }

        /** 경기 흐름 글 끝에 붙일 무기 이름. causer = damageCauserName (근접 무기 이름에 씀) */
        static String causeText(String cause, String causer) {
            if (cause == null) {
                return "";
            }
            return switch (cause) {
                case "grenade" -> " (수류탄)";
                case "molotov" -> " (화염병)";
                case "c4" -> " (C4)";
                case "sticky" -> " (접착 폭탄)";
                case "mortar" -> " (박격포)";
                case "panzer" -> " (판처파우스트)";
                case "melee" -> meleeName(causer).map(n -> " (근접: " + n + ")").orElse(" (근접)");
                case "meleeThrow" -> meleeName(causer).map(n -> " (던진 " + n + ")").orElse(" (근접 무기 던짐)");
                case "punch" -> " (주먹)";
                default -> "";
            };
        }

        private static Optional<String> meleeName(String causer) {
            return MELEE_WEAPONS.entrySet().stream()
                    .filter(en -> causer.startsWith(en.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }

        // ---------- 폭발물: 던진 곳 → 터진 곳 ----------

        /** 우리 팀이 던진 순간 (터진 피해와 attackId 로 잇는다) */
        private void onThrow(JsonNode e, int t) {
            JsonNode a = e.path("attacker");
            learnTeam(a);
            if (!isMyTeam(a)) {
                return;
            }
            Throw th = new Throw(e.path("attackId").asInt(-1), name(a), t, m(x(a)), m(y(a)));
            if (th.attackId() > 0) {
                throwsById.put(th.attackId(), th);
            }
            throwList.add(th);
        }

        /**
         * 우리 팀 폭발물이 적을 맞힌 피해를 한 발씩 묶는다.
         * 같은 사람·같은 종류·같은 attackId(없으면 무시)이고 첫 피해 뒤 2초(화염병 15초) 안이면 같은 한 발.
         */
        private void onBlastDamage(JsonNode e, int t) {
            String kind = weaponCause(e.path("damageTypeCategory").asText(""));
            if (!isBlast(kind)) {
                return;
            }
            JsonNode attacker = e.path("attacker");
            JsonNode victim = e.path("victim");
            learnTeam(attacker);
            learnTeam(victim);
            if (!isMyTeam(attacker) || isMyTeam(victim) || victim.isMissingNode()) {
                return;   // 우리 팀이 적을 맞힌 것만 (자기 자신·팀원 피해는 터진 곳 추정에 안 씀)
            }
            String who = name(attacker);
            int attackId = e.path("attackId").asInt(-1);
            int gap = kind.equals("molotov") ? FIRE_GAP_SECONDS : BLAST_GAP_SECONDS;
            BlastBuilder b = null;
            for (int i = blasts.size() - 1; i >= 0; i--) {
                BlastBuilder c = blasts.get(i);
                if (t - c.t > gap) {
                    break;
                }
                boolean sameShot = attackId <= 0 || c.attackId <= 0 || c.attackId == attackId;
                if (c.attacker.equals(who) && c.kind.equals(kind) && sameShot) {
                    b = c;
                    break;
                }
            }
            if (b == null) {
                int[] from = throwFrom(attackId, who, t, attacker);
                b = new BlastBuilder(t, kind, who, attackId, from[0], from[1]);
                blasts.add(b);
            }
            b.add(t, name(victim), m(x(victim)), m(y(victim)));
        }

        /** 던진 위치: attackId 가 같은 던진 기록 → 없으면 10초 안에 같은 사람이 던진 것 → 없으면 피해 순간 그 사람 위치 */
        private int[] throwFrom(int attackId, String who, int t, JsonNode attacker) {
            Throw th = attackId > 0 ? throwsById.get(attackId) : null;
            if (th == null) {
                for (int i = throwList.size() - 1; i >= 0; i--) {
                    Throw c = throwList.get(i);
                    if (t - c.t() > THROW_LOOKBACK_SECONDS) {
                        break;
                    }
                    if (c.name() != null && c.name().equals(who)) {
                        th = c;
                        break;
                    }
                }
            }
            return th != null ? new int[]{th.x(), th.y()} : new int[]{m(x(attacker)), m(y(attacker))};
        }

        /** 한 발로 기절·킬까지 했는지: 같은 사람·같은 종류로 맞은 적을 그 뒤 몇 초 안에 기절·킬 */
        private Replay.Blast toBlast(BlastBuilder b) {
            String result = "hit";
            for (Replay.Event ev : events) {
                if (ev.t() < b.t || ev.t() > b.last + BLAST_GAP_SECONDS
                        || !b.kind.equals(ev.cause()) || !b.attacker.equals(ev.actor()) || !b.victims.contains(ev.target())) {
                    continue;
                }
                if (ev.type().equals("kill")) {
                    result = "kill";
                } else if (ev.type().equals("knock") && result.equals("hit")) {
                    result = "knock";
                }
            }
            return new Replay.Blast(b.t, b.kind, b.fromX, b.fromY,
                    (int) Math.round((double) b.sumX / b.firstHits), (int) Math.round((double) b.sumY / b.firstHits),
                    b.attacker, result, b.victims.size());
        }

        // ---------- 총 명중 ----------

        /** 총으로 맞힌 것만. 우리 팀끼리 맞힌 것(오사)은 뺀다 */
        private void onDamage(JsonNode e, int t) {
            if (!"Damage_Gun".equals(e.path("damageTypeCategory").asText(""))) {
                return;
            }
            JsonNode attacker = e.path("attacker");
            JsonNode victim = e.path("victim");
            if (attacker.path("accountId").asText("").isEmpty()) {
                return;
            }
            learnTeam(attacker);
            learnTeam(victim);
            boolean byUs = isMyTeam(attacker);
            boolean onUs = isMyTeam(victim);
            if (byUs == onUs) {
                return;   // 우리와 상관없거나, 우리끼리
            }
            hits.add(new Replay.Hit(t, m(x(attacker)), m(y(attacker)), m(x(victim)), m(y(victim)),
                    byUs, name(attacker), name(victim)));
        }

        // ---------- 차량 · 비행기 ----------

        private void onVehicle(JsonNode e, int t, boolean ride) {
            JsonNode c = e.path("character");
            String vehicleType = e.path("vehicle").path("vehicleType").asText("");
            if (!ride && vehicleType.equals("TransportAircraft")) {
                jumps.add(new int[]{t, m(x(c)), m(y(c))});
                return;
            }
            if (vehicleType.equals("TransportAircraft") || vehicleType.equals("Parachute")) {
                return;
            }
            learnTeam(c);
            if (isMyTeam(c)) {
                String vehicle = e.path("vehicle").path("vehicleId").asText("차량");
                events.add(new Replay.Event(t, ride ? "ride" : "leave", m(x(c)), m(y(c)), name(c), null, true,
                        name(c) + (ride ? " 차량 탑승" : " 차량에서 내림") + " (" + vehicleLabel(vehicle) + ")", null));
            }
        }

        // ---------- 결과 ----------

        Replay result(String matchId, MapInfo map) {
            List<Replay.Track> players = tracks.values().stream().map(TrackBuilder::build).toList();
            events.sort(Comparator.comparingInt(Replay.Event::t));
            return new Replay(matchId, map, lastT, me, myTeam == null ? -1 : myTeam,
                    players, List.copyOf(zones), List.copyOf(phases), List.copyOf(events), List.copyOf(hits), plane(), fights(),
                    blasts.stream().map(this::toBlast).toList(), List.copyOf(heals));
        }

        /** 첫 낙하 지점과 마지막 낙하 지점을 이은 선. 낙하 기록이 2개 미만이면 모름 */
        private Replay.Plane plane() {
            if (jumps.size() < 2) {
                return null;
            }
            int[] first = jumps.get(0);
            int[] last = jumps.get(jumps.size() - 1);
            return new Replay.Plane(first[1], first[2], last[1], last[2]);
        }

        /** 우리 팀이 관련된 기절·킬을 30초 간격으로 묶는다 */
        private List<Replay.Fight> fights() {
            List<Replay.Fight> result = new ArrayList<>();
            List<Replay.Event> group = new ArrayList<>();
            for (Replay.Event ev : events) {
                if (!ev.ours() || !(ev.type().equals("knock") || ev.type().equals("kill"))) {
                    continue;
                }
                if (!group.isEmpty() && ev.t() - group.get(group.size() - 1).t() > FIGHT_GAP_SECONDS) {
                    result.add(toFight(result.size() + 1, group));
                    group = new ArrayList<>();
                }
                group.add(ev);
            }
            if (!group.isEmpty()) {
                result.add(toFight(result.size() + 1, group));
            }
            return result;
        }

        private Replay.Fight toFight(int n, List<Replay.Event> group) {
            int kills = 0, knocks = 0, knocked = 0, deaths = 0;
            long sx = 0, sy = 0;
            for (Replay.Event ev : group) {
                boolean weDidIt = isMyTeamName(ev.actor());
                boolean weGotIt = isMyTeamName(ev.target());
                if (ev.type().equals("kill")) {
                    if (weGotIt) deaths++; else if (weDidIt) kills++;
                } else {
                    if (weGotIt) knocked++; else if (weDidIt) knocks++;
                }
                sx += ev.x();
                sy += ev.y();
            }
            return new Replay.Fight(n, group.get(0).t(), group.get(group.size() - 1).t(),
                    (int) (sx / group.size()), (int) (sy / group.size()), kills, knocks, knocked, deaths);
        }

        // ---------- 작은 도우미 ----------

        private boolean isMyTeam(JsonNode c) {
            return myTeam != null && !c.isMissingNode() && c.path("teamId").asInt(-2) == myTeam;
        }

        private boolean isMyTeamName(String name) {
            if (name == null || myTeam == null) {
                return false;
            }
            return tracks.values().stream().anyMatch(tb -> tb.team == myTeam && name.equals(tb.name));
        }

        private static String name(JsonNode c) {
            String n = c.path("name").asText("");
            return n.isEmpty() ? null : n;
        }

        private static double x(JsonNode c) {
            return c.path("location").path("x").asDouble();
        }

        private static double y(JsonNode c) {
            return c.path("location").path("y").asDouble();
        }

        /** cm → m */
        private static int m(double cm) {
            return (int) Math.round(cm / 100.0);
        }

        /** "BP_Mirado_A_03_C" → "Mirado" 정도로 짧게 */
        private static String vehicleLabel(String vehicleId) {
            String v = vehicleId.replace("BP_", "").replaceAll("_[A-Z]?_?\\d+_C$", "").replaceAll("_C$", "");
            int cut = v.indexOf('_');
            return cut > 0 ? v.substring(0, cut) : v;
        }

        private static Instant parseTime(String text) {
            try {
                return text == null ? null : Instant.parse(text);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** 던진 순간 */
    private record Throw(int attackId, String name, int t, int x, int y) {
    }

    /** 폭발물 한 발을 쌓는 중. 터진 곳 = 첫 1초 안에 맞은 적들 위치 평균 (화염병은 불에 늦게 들어온 사람을 빼려고) */
    private static final class BlastBuilder {
        final int t;
        final String kind;
        final String attacker;
        final int attackId;
        final int fromX, fromY;
        int last;
        long sumX, sumY;
        int firstHits;
        final Set<String> victims = new LinkedHashSet<>();

        BlastBuilder(int t, String kind, String attacker, int attackId, int fromX, int fromY) {
            this.t = t;
            this.kind = kind;
            this.attacker = attacker;
            this.attackId = attackId;
            this.fromX = fromX;
            this.fromY = fromY;
            this.last = t;
        }

        void add(int at, String victim, int x, int y) {
            last = Math.max(last, at);
            if (at - t <= 1 && victims.add(victim)) {
                sumX += x;
                sumY += y;
                firstHits++;
            } else {
                victims.add(victim);
            }
        }
    }

    /** 한 사람의 이동 기록을 쌓는 중 */
    private static final class TrackBuilder {
        final String id;
        final String name;
        final int team;
        Integer diedAt;
        private final List<int[]> points = new ArrayList<>();

        TrackBuilder(String id, String name, int team) {
            this.id = id;
            this.name = name;
            this.team = team;
        }

        void add(int t, int x, int y, int vehicle) {
            // 같은 초에 두 번 오면 마지막 값만
            if (!points.isEmpty() && points.get(points.size() - 1)[0] == t) {
                points.set(points.size() - 1, new int[]{t, x, y, vehicle});
            } else {
                points.add(new int[]{t, x, y, vehicle});
            }
        }

        Replay.Track build() {
            int n = points.size();
            int[] t = new int[n], x = new int[n], y = new int[n], v = new int[n];
            for (int i = 0; i < n; i++) {
                t[i] = points.get(i)[0];
                x[i] = points.get(i)[1];
                y[i] = points.get(i)[2];
                v[i] = points.get(i)[3];
            }
            return new Replay.Track(id, name, team, t, x, y, v, diedAt);
        }
    }
}
