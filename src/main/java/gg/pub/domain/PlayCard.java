package gg.pub.domain;

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
 * @param fightStartPercent 교전 중 우리 팀이 먼저 건 비율 (%). 교전이 없으면 null
 * @param zoneEdgePercent   3페이즈부터 자기장 안 평균 위치 (0 = 중심, 100 = 경계). 기록이 없으면 null
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
                       Integer fightStartPercent,
                       Integer zoneEdgePercent,
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

    public boolean hasFightGauge() {
        return fightStartPercent != null;
    }

    public boolean hasZoneGauge() {
        return zoneEdgePercent != null;
    }

    /** 싸움걸리기 비율 = 100 - 싸움걸기 비율 */
    public int fightReceivePercent() {
        return fightStartPercent == null ? 0 : 100 - fightStartPercent;
    }

    // ---------- 자기장 지도 그리기용 (120×120 그림, 안전구역 반지름 56) ----------

    private static final double MAP_RADIUS = 56;

    /** 내 평균 위치까지의 거리를 그림 크기로 바꾼 값 */
    public double zoneRingRadius() {
        return zoneEdgePercent == null ? 0 : MAP_RADIUS * zoneEdgePercent / 100.0;
    }

    /** 점을 오른쪽 위 45도 방향에 찍을 때 x·y 로 얼마나 움직일지 */
    public double zoneMarkerOffset() {
        return zoneRingRadius() / Math.sqrt(2);
    }
}
