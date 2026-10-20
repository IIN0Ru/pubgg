package gg.pub.domain;

import java.util.Locale;

/**
 * 플레이 카드의 파밍 정보.
 *
 * @param dropStyle     "대도시파" 또는 "짤파밍파". 착지 기록이 없으면 null
 * @param mainCity      대도시파일 때 가장 많이 내린 도시 (없으면 null)
 * @param bigCityDrops  대도시에 내린 판 수
 * @param drops         착지 기록이 있는 판 수
 * @param phase3Matches 3페이즈까지 살아남은 판 수 (아래 평균의 기준)
 * @param avgHeals      3페이즈 시작 때 평균 회복·부스트템 개수
 * @param avgThrowables 3페이즈 시작 때 평균 투척무기 개수
 * @param avgAmmo       3페이즈 시작까지 평균 주운 총알 수
 */
public record Farming(String dropStyle,
                      String mainCity,
                      int bigCityDrops,
                      int drops,
                      int phase3Matches,
                      double avgHeals,
                      double avgThrowables,
                      int avgAmmo) {

    public static final String BIG_CITY = "대도시파";
    public static final String SMALL_TOWN = "짤파밍파";

    public boolean hasDropStyle() {
        return dropStyle != null;
    }

    public boolean hasMainCity() {
        return mainCity != null;
    }

    public boolean hasBag() {
        return phase3Matches > 0;
    }

    public String avgHealsText() {
        return String.format(Locale.ROOT, "%.1f", avgHeals);
    }

    public String avgThrowablesText() {
        return String.format(Locale.ROOT, "%.1f", avgThrowables);
    }
}
