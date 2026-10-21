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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 텔레메트리(경기 상세 기록)를 읽어서 2D 리플레이 데이터(Replay)로 줄인다.
 *
 * TelemetryAnalyzer 와 같은 방식: 수 MB 짜리 JSON 배열을 한 번에 올리지 않고 이벤트를 하나씩 읽는다.
 *
 * 쓰는 이벤트:
 *  - LogMatchStart              경기 시작 시각 (모든 시각을 여기서부터 초로 센다)
 *  - LogPlayerPosition          전원 위치 (약 10초마다)
 *  - LogGameStatePeriodic       자기장: safetyZone(지금 경계, 파란 원) / poisonGasWarning(다음 안전구역, 흰 원)
 *  - LogPlayerMakeGroggy        기절
 *  - LogPlayerKillV2            킬 (죽은 시각도 여기서)
 *  - LogPlayerRevive            부활
 *  - LogParachuteLanding        착지 (우리 팀만)
 *  - LogVehicleRide / Leave     차량 타고 내림 (우리 팀만). 비행기에서 뛰어내린 기록으로 비행기 경로도 구함
 *  - LogCarePackageLand         보급상자 떨어진 곳
 *  - LogPlayerTakeDamage        총 명중 (우리 팀이 맞힘 / 우리 팀이 맞음). 총알 방향은 기록에 없어서 맞은 것만
 *
 * 좌표는 cm → m, 시각은 초 단위 정수로 줄인다.
 */
@Component
public class ReplayBuilder {

    /** 우리 팀 기절·킬이 이 시간 안에 이어지면 같은 교전 (TelemetryAnalyzer 의 교전 기준과 같게) */
    static final int FIGHT_GAP_SECONDS = 30;

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
                case "LogPlayerPosition" -> onPosition(e.path("character"), t);
                case "LogGameStatePeriodic" -> onGameState(e.path("gameState"), t);
                case "LogPlayerMakeGroggy" -> {
                    JsonNode attacker = e.path("attacker");
                    JsonNode victim = e.path("victim");
                    addCombat(t, "knock", attacker, victim,
                            name(attacker) == null ? name(victim) + " 기절" : name(attacker) + " → " + name(victim) + " 기절");
                }
                case "LogPlayerKillV2" -> onKill(e, t);
                case "LogPlayerRevive" -> {
                    JsonNode reviver = e.path("reviver");
                    JsonNode victim = e.path("victim");
                    boolean ours = isMyTeam(reviver) || isMyTeam(victim);
                    if (ours) {
                        events.add(new Replay.Event(t, "revive", m(x(victim)), m(y(victim)), name(reviver), name(victim),
                                true, name(reviver) + "가 " + name(victim) + " 살림"));
                    }
                }
                case "LogParachuteLanding" -> {
                    JsonNode c = e.path("character");
                    learnTeam(c);
                    if (isMyTeam(c)) {
                        events.add(new Replay.Event(t, "landing", m(x(c)), m(y(c)), name(c), null, true, name(c) + " 착지"));
                    }
                }
                case "LogVehicleRide", "LogVehicleLeave" -> onVehicle(e, t, type.equals("LogVehicleRide"));
                case "LogPlayerTakeDamage" -> onDamage(e, t);
                case "LogCarePackageLand" -> {
                    JsonNode loc = e.path("itemPackage").path("location");
                    events.add(new Replay.Event(t, "package", m(loc.path("x").asDouble()), m(loc.path("y").asDouble()),
                            null, null, false, "보급상자 떨어짐"));
                }
                default -> {
                    // 나머지는 리플레이에 쓰지 않는다
                }
            }
        }

        // ---------- 위치 ----------

        private void onPosition(JsonNode c, int t) {
            String id = c.path("accountId").asText("");
            if (id.isEmpty()) {
                return;
            }
            learnTeam(c);
            tracks.computeIfAbsent(id, k -> new TrackBuilder(id, name(c), c.path("teamId").asInt(-1)))
                    .add(t, m(x(c)), m(y(c)));
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
            String text = name(killer) == null ? name(victim) + " 사망" : name(killer) + " → " + name(victim) + " 킬";
            addCombat(t, "kill", killer, victim, text);
        }

        private void addCombat(int t, String type, JsonNode attacker, JsonNode victim, String text) {
            learnTeam(attacker);
            learnTeam(victim);
            boolean ours = isMyTeam(attacker) || isMyTeam(victim);
            events.add(new Replay.Event(t, type, m(x(victim)), m(y(victim)), name(attacker), name(victim), ours, text));
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
                        name(c) + (ride ? " 차량 탑승" : " 차량에서 내림") + " (" + vehicleLabel(vehicle) + ")"));
            }
        }

        // ---------- 결과 ----------

        Replay result(String matchId, MapInfo map) {
            List<Replay.Track> players = tracks.values().stream().map(TrackBuilder::build).toList();
            events.sort(Comparator.comparingInt(Replay.Event::t));
            return new Replay(matchId, map, lastT, me, myTeam == null ? -1 : myTeam,
                    players, List.copyOf(zones), List.copyOf(phases), List.copyOf(events), List.copyOf(hits), plane(), fights());
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

        void add(int t, int x, int y) {
            // 같은 초에 두 번 오면 마지막 값만
            if (!points.isEmpty() && points.get(points.size() - 1)[0] == t) {
                points.set(points.size() - 1, new int[]{t, x, y});
            } else {
                points.add(new int[]{t, x, y});
            }
        }

        Replay.Track build() {
            int n = points.size();
            int[] t = new int[n], x = new int[n], y = new int[n];
            for (int i = 0; i < n; i++) {
                t[i] = points.get(i)[0];
                x[i] = points.get(i)[1];
                y[i] = points.get(i)[2];
            }
            return new Replay.Track(id, name, team, t, x, y, diedAt);
        }
    }
}
