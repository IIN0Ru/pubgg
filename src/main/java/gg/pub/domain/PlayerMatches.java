package gg.pub.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 검색 결과 화면에 넘겨줄 묶음.
 *
 * @param nickname   PUBG 에 등록된 정확한 닉네임
 * @param platform   스팀 / 카카오
 * @param card       플레이 카드 (경쟁전 기록이 없으면 null)
 * @param season     이번 시즌 경쟁전 티어·기록 (없으면 null)
 * @param matches    최근 14일 경기 (모든 모드, 최신순)
 * @param teammates  자주 같이 한 팀원 (14일 전체 경기 기준)
 * @param activity   활동 시간대 (14일 전체 경기 기준)
 * @param clan       클랜 (없거나 못 받았으면 null)
 */
public record PlayerMatches(String nickname,
                            Platform platform,
                            PlayCard card,
                            SeasonRank season,
                            List<MatchRecord> matches,
                            List<Teammate> teammates,
                            ActivityMap activity,
                            Clan clan) {

    /** 자주 같이 한 팀원 최대 인원 */
    public static final int TEAMMATE_LIMIT = 5;

    /** 경기 목록만 주면 팀원·활동 시간대는 여기서 계산한다 */
    public PlayerMatches(String nickname, Platform platform, PlayCard card, SeasonRank season,
                         List<MatchRecord> matches, Clan clan) {
        this(nickname, platform, card, season, matches,
                Teammate.fromMatches(matches, TEAMMATE_LIMIT),
                ActivityMap.fromMatches(matches), clan);
    }

    /** 클랜 없이 */
    public PlayerMatches(String nickname, Platform platform, PlayCard card, SeasonRank season,
                         List<MatchRecord> matches) {
        this(nickname, platform, card, season, matches, null);
    }

    /** 시즌 기록 없이 (테스트용) */
    public PlayerMatches(String nickname, Platform platform, PlayCard card, List<MatchRecord> matches) {
        this(nickname, platform, card, null, matches);
    }

    public boolean hasSeason() {
        return season != null;
    }

    /** 최근 경쟁전 vs 이번 시즌 (둘 다 있을 때만) */
    public List<SeasonRank.Comparison> seasonComparison() {
        return season == null ? List.of() : season.compareWith(card);
    }

    // ---------- 링크 미리보기 (디스코드·카톡·커뮤니티에 링크를 붙였을 때) ----------

    /** "N0-RU · 플래티넘 3" (시즌 기록이 없으면 닉네임만) */
    public String shareTitle() {
        return season != null ? nickname + " · " + season.tierLabel() : nickname;
    }

    /**
     * "KDA 3.42 · 평균 딜 412 · 센터 운영 · 대도시파 · 주로 21~24시"
     * 듀오를 고를 때 보는 것만: 실력(KDA·딜) + 습관(스타일) + 언제 하는지
     */
    public String shareDescription() {
        List<String> parts = new ArrayList<>();
        if (card != null) {
            parts.add("KDA " + card.kdaText());
            parts.add("평균 딜 " + card.avgDamage());
            parts.addAll(card.styleTags());
        }
        if (activity != null && activity.hasData()) {
            parts.add("주로 " + activity.peakText());
        }
        if (parts.isEmpty()) {
            return platform.getLabel() + " " + nickname + "의 배그 플레이 스타일";
        }
        return String.join(" · ", parts);
    }

    public boolean hasMatches() {
        return !matches.isEmpty();
    }

    public boolean hasCard() {
        return card != null;
    }

    public boolean hasTeammates() {
        return !teammates.isEmpty();
    }
}
