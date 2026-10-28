package gg.pub.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 공유용 플레이어 카드 (/card 화면). 검색 결과(PlayerMatches)에서 카드에 그릴 것만 뽑는다.
 * 그리는 일은 화면(static/js/player-card.js)이 캔버스로 한다. 이 값은 JSON 으로 넘어간다.
 *
 * 기록에서 나오는 것만 여기서 정한다. 마이크·지향·구함·역할(성향 태그)과 반팔 색은 사용자가 고르고 주소에 담긴다.
 *
 * @param nickname    닉네임
 * @param clan        "[HLMT] 헬멧연구소" (없으면 빈 문자열)
 * @param tierFamily  테두리 색 계열: Bronze ~ Survivor, 배치 전이면 Unranked
 * @param tierIcon    티어 아이콘 파일 이름 (static/images/card/tiers/*.png): "Diamond-2", "Master", "Unranked"
 * @param tierText    "DIAMOND II", "MASTER", "UNRANKED"
 * @param kda         "3.42"
 * @param avgDamage   평균 딜량
 * @param weaponName  주무기 이름 ("Beryl"). 기록이 없으면 빈 문자열
 * @param weaponClass 주무기 분류 (WeaponClass 이름). 그릴 총 그림을 못 찾으면 분류로 대신 고른다
 * @param combatTags  전투 태그 (# 없이): 외곽 운영, 대도시파, 헤드샷 32%, TOP10 60%, 투척 2.1개
 * @param peakHours   주 활동 시간 "21~24시" (없으면 빈 문자열). 성향 줄 맨 앞에 붙는다
 * @param matches     계산에 쓴 경쟁전 경기 수 (최대 10)
 */
public record ShareCard(String nickname,
                        String clan,
                        String tierFamily,
                        String tierIcon,
                        String tierText,
                        String kda,
                        int avgDamage,
                        String weaponName,
                        String weaponClass,
                        List<String> combatTags,
                        String peakHours,
                        int matches) {

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /** 경쟁전 카드가 없으면 빈 값 */
    public static Optional<ShareCard> from(PlayerMatches result) {
        PlayCard c = result.card();
        if (c == null) {
            return Optional.empty();
        }
        // 플레이 카드와 같은 경기들: 최신 경쟁전 c.matches() 판
        List<MatchRecord> games = result.matches().stream()
                .filter(MatchRecord::isCompetitive)
                .limit(c.matches())
                .toList();

        List<String> combat = new ArrayList<>(c.styleTags());
        combat.add("헤드샷 " + headshotPercent(games) + "%");
        combat.add("TOP10 " + top10Percent(games) + "%");
        combat.add("투척 " + c.avgThrowablesText() + "개");

        WeaponUsage weapon = c.hasWeapons() ? c.mainWeapons().get(0) : null;
        Clan clan = result.clan();
        ActivityMap activity = result.activity();
        String[] tier = tier(result.season());

        return Optional.of(new ShareCard(
                result.nickname(),
                clan == null ? "" : clan.label(),
                tier[0], tier[1], tier[2],
                c.kdaText(),
                c.avgDamage(),
                weapon == null ? "" : weapon.name(),
                weapon == null ? WeaponClass.OTHER.name() : weapon.type().name(),
                combat,
                activity != null && activity.hasData() ? activity.peakText() : "",
                c.matches()));
    }

    /** 헤드샷 킬 ÷ 킬 (%). 킬이 없으면 0 */
    static int headshotPercent(List<MatchRecord> games) {
        int kills = games.stream().mapToInt(g -> g.me().kills()).sum();
        int heads = games.stream().mapToInt(g -> g.me().headshotKills()).sum();
        return kills == 0 ? 0 : (int) Math.round(heads * 100.0 / kills);
    }

    /** TOP 10 에 든 판 비율 (%) */
    static int top10Percent(List<MatchRecord> games) {
        if (games.isEmpty()) {
            return 0;
        }
        long top = games.stream().filter(MatchRecord::isTop10).count();
        return (int) Math.round(top * 100.0 / games.size());
    }

    /** [테두리 계열, 아이콘 파일, 글자] */
    static String[] tier(SeasonRank season) {
        if (season == null || !season.isPlaced()) {
            return new String[]{"Unranked", "Unranked", "UNRANKED"};
        }
        String t = season.tier();
        String upper = t.toUpperCase(Locale.ROOT);
        if ("Master".equals(t) || "Survivor".equals(t)) {
            return new String[]{t, t, upper};
        }
        int sub = parseSub(season.subTier());
        return new String[]{t, t + "-" + sub, upper + " " + ROMAN[sub - 1]};
    }

    /** "1" ~ "5". 이상하면 1 */
    private static int parseSub(String subTier) {
        try {
            int n = Integer.parseInt(subTier == null ? "" : subTier.trim());
            return n >= 1 && n <= 5 ? n : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
