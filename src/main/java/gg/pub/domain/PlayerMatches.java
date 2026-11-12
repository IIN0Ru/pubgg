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
 * @param matches    최근 경기 (모든 모드, 최신순, 최대 30경기)
 * @param teammates  자주 같이 한 팀원 (불러온 경기 기준)
 * @param clan       클랜 (없거나 못 받았으면 null)
 */
public record PlayerMatches(String nickname,
                            Platform platform,
                            PlayCard card,
                            SeasonRank season,
                            List<MatchRecord> matches,
                            List<Teammate> teammates,
                            Clan clan) {

    /** 자주 같이 한 팀원 최대 인원 */
    public static final int TEAMMATE_LIMIT = 5;

    /** 경기 목록만 주면 팀원은 여기서 계산한다 */
    public PlayerMatches(String nickname, Platform platform, PlayCard card, SeasonRank season,
                         List<MatchRecord> matches, Clan clan) {
        this(nickname, platform, card, season, matches,
                Teammate.fromMatches(matches, TEAMMATE_LIMIT), clan);
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

    /** 카드만 채운 새 결과 (카드는 따로 늦게 만들어서) */
    public PlayerMatches withCard(PlayCard newCard) {
        return new PlayerMatches(nickname, platform, newCard, season, matches, teammates, clan);
    }

    public boolean hasSeason() {
        return season != null;
    }

    /**
     * 최근 경쟁전 vs 이번 시즌 (둘 다 있을 때만).
     * 일반 경기로 만든 카드는 비교하지 않는다 (시즌 기록은 경쟁전이라 종류가 달라서 비교가 안 된다)
     */
    public List<SeasonRank.Comparison> seasonComparison() {
        return season == null || (card != null && card.isNormal()) ? List.of() : season.compareWith(card);
    }

    // ---------- 링크 미리보기 (디스코드·카톡·커뮤니티에 링크를 붙였을 때) ----------

    /** "Sample-01 · 플래티넘 3" (시즌 기록이 없으면 닉네임만) */
    public String shareTitle() {
        return season != null ? nickname + " · " + season.tierLabel() : nickname;
    }

    /**
     * "KDA 3.42 · 평균 딜 412 · 센터 운영 · 대도시파밍"
     * 듀오를 고를 때 보는 것만: 실력(KDA·딜) + 습관(스타일)
     */
    public String shareDescription() {
        List<String> parts = new ArrayList<>();
        if (card != null) {
            parts.add("KDA " + card.kdaText());
            parts.add("평균 딜 " + card.avgDamage());
            parts.addAll(card.styleTags());
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
