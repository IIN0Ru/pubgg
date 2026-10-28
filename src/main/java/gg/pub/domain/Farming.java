package gg.pub.domain;

import java.util.Locale;

/**
 * 플레이 카드의 파밍 정보.
 *
 * @param farmStyle     "대도시파밍" 또는 "짤파밍". 헬멧·가방·조끼를 다 모은 판이 없으면 null
 * @param mainCity      대도시파밍일 때 가장 많이 파밍을 끝낸 도시 (없으면 null)
 * @param bigCityFarms  헬멧·가방·조끼를 대도시에서 다 모은 판 수
 * @param farmedMatches 헬멧·가방·조끼를 다 모은 판 수 (모으기 전에 죽은 판은 빠짐)
 * @param phase3Matches 3페이즈까지 살아남은 판 수 (아래 평균의 기준)
 * @param avgHeals      3페이즈 시작 때 평균 회복·부스트템 개수
 * @param avgThrowables 3페이즈 시작 때 평균 투척무기 개수
 * @param avgAmmo       3페이즈 시작까지 평균 주운 총알 수
 */
public record Farming(String farmStyle,
                      String mainCity,
                      int bigCityFarms,
                      int farmedMatches,
                      int phase3Matches,
                      double avgHeals,
                      double avgThrowables,
                      int avgAmmo) {

    public static final String BIG_CITY = "대도시파밍";
    public static final String SMALL_TOWN = "짤파밍";

    public boolean hasFarmStyle() {
        return farmStyle != null;
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
