package gg.pub.domain;

import java.util.List;

/**
 * 검색 결과 화면에 넘겨줄 묶음: 누구의, 어느 플랫폼의, 최근 경기들.
 *
 * @param nickname PUBG 에 등록된 정확한 닉네임
 * @param platform 스팀 / 카카오
 * @param matches  최근 경기 (최신순)
 */
public record PlayerMatches(String nickname, Platform platform, List<MatchSummary> matches) {

    public boolean hasMatches() {
        return !matches.isEmpty();
    }
}
