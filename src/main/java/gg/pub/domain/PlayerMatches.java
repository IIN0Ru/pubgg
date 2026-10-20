package gg.pub.domain;

import java.util.List;

/**
 * 검색 결과 화면에 넘겨줄 묶음.
 *
 * @param nickname PUBG 에 등록된 정확한 닉네임
 * @param platform 스팀 / 카카오
 * @param card     플레이 카드 (경쟁전 기록이 없으면 null)
 * @param matches  최근 14일 경기 (모든 모드, 최신순)
 */
public record PlayerMatches(String nickname, Platform platform, PlayCard card, List<MatchRecord> matches) {

    public boolean hasMatches() {
        return !matches.isEmpty();
    }

    public boolean hasCard() {
        return card != null;
    }
}
