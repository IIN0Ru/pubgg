package gg.pub.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 이번 시즌 경쟁전 기록 (한 모드).
 *
 * @param gameMode     squad / squad-fpp
 * @param tier         Bronze, Silver, Gold, Platinum, Crystal, Diamond, Master, Survivor (배치 전이면 Unranked 또는 null)
 * @param subTier      "1" ~ "5"
 * @param rankPoint    RP
 * @param rounds       이번 시즌 판 수
 * @param kda          이번 시즌 KDA
 * @param avgDamage    이번 시즌 평균 딜량
 * @param avgRank      이번 시즌 평균 등수
 * @param top10Percent TOP 10 비율 %
 */
public record SeasonRank(String gameMode,
                         String tier,
                         String subTier,
                         int rankPoint,
                         int rounds,
                         double kda,
                         int avgDamage,
                         double avgRank,
                         int top10Percent) {

    private static final Map<String, String> TIER_NAMES = Map.of(
            "Bronze", "브론즈",
            "Silver", "실버",
            "Gold", "골드",
            "Platinum", "플래티넘",
            "Crystal", "크리스탈",
            "Diamond", "다이아몬드",
            "Master", "마스터",
            "Survivor", "서바이버");

    /** 시즌 판 수가 이보다 적으면 비교 표에 "표본 적음"을 붙인다 */
    public static final int SMALL_SAMPLE = 20;

    public boolean isSmallSample() {
        return rounds < SMALL_SAMPLE;
    }

    /** 배치를 끝내서 티어가 있는지 */
    public boolean isPlaced() {
        return tier != null && TIER_NAMES.containsKey(tier);
    }

    /** "플래티넘 3". 마스터·서바이버는 세부 단계가 없다. 배치 전이면 "배치 중" */
    public String tierLabel() {
        if (!isPlaced()) {
            return "배치 중";
        }
        String name = TIER_NAMES.get(tier);
        if ("Master".equals(tier) || "Survivor".equals(tier) || subTier == null || subTier.isBlank()) {
            return name;
        }
        return name + " " + subTier;
    }

    /** "2,450 RP" */
    public String rankPointText() {
        return String.format(Locale.ROOT, "%,d RP", rankPoint);
    }

    /** "스쿼드 FPP" */
    public String modeLabel() {
        return GameLabels.gameMode(gameMode);
    }

    public String kdaText() {
        return String.format(Locale.ROOT, "%.2f", kda);
    }

    public String avgRankText() {
        return "#" + String.format(Locale.ROOT, "%.1f", avgRank);
    }

    /**
     * 최근 경쟁전(플레이 카드)과 이번 시즌 비교.
     * 시즌 기록이 너무 적으면(판 수 0) 비교하지 않는다.
     */
    public List<Comparison> compareWith(PlayCard card) {
        if (card == null || rounds == 0) {
            return List.of();
        }
        return List.of(
                new Comparison("KDA", card.kdaText(), kdaText(), Double.compare(round2(card.kda()), round2(kda))),
                new Comparison("평균 딜량", String.valueOf(card.avgDamage()), String.valueOf(avgDamage),
                        Integer.compare(card.avgDamage(), avgDamage)));
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /**
     * 비교 한 줄.
     *
     * @param label  "KDA"
     * @param recent 최근 10경기 값
     * @param season 시즌 값
     * @param trend  최근이 시즌보다 높으면 1, 낮으면 -1, 같으면 0
     */
    public record Comparison(String label, String recent, String season, int trend) {

        /** "▲" / "▼" / "" */
        public String arrow() {
            return trend > 0 ? "▲" : trend < 0 ? "▼" : "";
        }

        /** 화면 읽기 프로그램용 */
        public String trendText() {
            return trend > 0 ? "시즌보다 높음" : trend < 0 ? "시즌보다 낮음" : "시즌과 같음";
        }
    }
}
