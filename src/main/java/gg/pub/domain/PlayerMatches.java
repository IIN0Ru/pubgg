package gg.pub.domain;

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
 */
public record PlayerMatches(String nickname,
                            Platform platform,
                            PlayCard card,
                            SeasonRank season,
                            List<MatchRecord> matches,
                            List<Teammate> teammates,
                            ActivityMap activity) {

    /** 자주 같이 한 팀원 최대 인원 */
    public static final int TEAMMATE_LIMIT = 5;

    /** 경기 목록만 주면 팀원·활동 시간대는 여기서 계산한다 */
    public PlayerMatches(String nickname, Platform platform, PlayCard card, SeasonRank season,
                         List<MatchRecord> matches) {
        this(nickname, platform, card, season, matches,
                Teammate.fromMatches(matches, TEAMMATE_LIMIT),
                ActivityMap.fromMatches(matches));
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
