package gg.pub.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 플레이 카드. 경쟁전 최근 경기들을 모아서 계산한다.
 *
 * @param matches           계산에 쓴 경쟁전 경기 수 (최대 10)
 * @param kills             총 킬
 * @param assists           총 어시스트
 * @param deaths            죽은 판 수 (1등이 아닌 판)
 * @param avgDamage         평균 딜량
 * @param perspective       "FPP" 또는 "TPP" (더 많이 한 쪽)
 * @param mainWeapons       슬롯에 가장 오래 넣고 있던 주무기 (최대 2개)
 * @param avgThrowables     한 판 평균 투척 개수
 * @param analyzedMatches   텔레메트리 분석에 성공한 경기 수 (아래 항목들은 이 경기들 기준)
 * @param titles            받은 칭호 (해적, 하이에나, 보급러)
 * @param zoneEdgePercent   3페이즈부터 자기장 안 평균 위치 (0 = 중심, 100 = 경계). 기록이 없으면 null
 * @param zoneMatchPercents 위 값을 경기마다 (3페이즈까지 간 경기만)
 * @param farming           착지 성향, 3페이즈 시작 가방 상태
 */
public record PlayCard(int matches,
                       int kills,
                       int assists,
                       int deaths,
                       int avgDamage,
                       String perspective,
                       List<WeaponUsage> mainWeapons,
                       double avgThrowables,
                       int analyzedMatches,
                       List<Title> titles,
                       Integer zoneEdgePercent,
                       List<Integer> zoneMatchPercents,
                       Farming farming) {

    /** KDA = (킬 + 어시스트) ÷ 죽은 판 수. 한 번도 안 죽었으면 (킬 + 어시스트) 그대로 */
    public double kda() {
        int ka = kills + assists;
        return deaths == 0 ? ka : (double) ka / deaths;
    }

    /** "3.42" */
    public String kdaText() {
        return String.format(Locale.ROOT, "%.2f", kda());
    }

    /** "2.3" */
    public String avgThrowablesText() {
        return String.format(Locale.ROOT, "%.1f", avgThrowables);
    }

    public boolean hasWeapons() {
        return !mainWeapons.isEmpty();
    }

    public boolean hasTitles() {
        return !titles.isEmpty();
    }

    public boolean hasZoneGauge() {
        return zoneEdgePercent != null;
    }

    // ---------- 자기장 미니맵 그리기용 (220×220 그림, 안전구역 중심 110,110 · 반지름 92) ----------

    static final double MAP_CENTER = 110;
    static final double MAP_RADIUS = 92;
    /** 판별 점을 고르게 흩어 찍으려고 쓰는 각도 (황금각). 방향 자체는 의미 없음 */
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    /** 그림 위의 한 점 */
    public record Dot(double x, double y) {
    }

    /** 중심에서 percent% 거리, angle 방향의 점 (소수 첫째 자리까지) */
    private static Dot dotAt(int percent, double angle) {
        double r = MAP_RADIUS * Math.min(percent, 100) / 100.0;
        return new Dot(Math.round((MAP_CENTER + r * Math.cos(angle)) * 10) / 10.0,
                Math.round((MAP_CENTER + r * Math.sin(angle)) * 10) / 10.0);
    }

    /** 한 판마다 점 하나. 거리만 진짜이고, 방향은 겹치지 않게 돌려가며 찍는다 */
    public List<Dot> zoneMatchDots() {
        List<Dot> dots = new ArrayList<>();
        for (int i = 0; i < zoneMatchPercents.size(); i++) {
            dots.add(dotAt(zoneMatchPercents.get(i), i * GOLDEN_ANGLE - Math.PI / 2 + 0.6));
        }
        return dots;
    }

    /** 평균 위치 표시(노란 화살표). 오른쪽 위 45도 방향 */
    public Dot zoneMarker() {
        return dotAt(zoneEdgePercent == null ? 0 : zoneEdgePercent, -Math.PI / 4);
    }

    // ---------- 스타일 요약 (공유 카드의 한 단어 태그) ----------

    /** 이 이상 / 이하일 때만 한쪽 성향으로 본다. 그 사이는 태그를 붙이지 않음 */
    static final int LEAN_HIGH = 55;
    static final int LEAN_LOW = 45;

    /** 예: ["센터 운영", "대도시파"] — 성향이 반반이면 그 항목은 빠진다 */
    public List<String> styleTags() {
        List<String> tags = new ArrayList<>();
        if (zoneEdgePercent != null) {
            if (zoneEdgePercent <= LEAN_LOW) {
                tags.add("센터 운영");
            } else if (zoneEdgePercent >= LEAN_HIGH) {
                tags.add("외곽 운영");
            }
        }
        if (farming != null && farming.hasDropStyle()) {
            tags.add(farming.dropStyle());
        }
        return tags;
    }

    /** 주무기 이름만 이어 붙인 것: "Beryl M762 · Mini 14" */
    public String weaponNames() {
        return String.join(" · ", mainWeapons.stream().map(WeaponUsage::name).toList());
    }
}
