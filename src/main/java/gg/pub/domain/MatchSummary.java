package gg.pub.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 경기 목록 한 줄에 보여줄 정보.
 * PUBG API 응답(MatchResponse)에서 필요한 값만 골라 화면에 맞게 정리한 것이다.
 *
 * @param playedAt        경기 시작 시각
 * @param mode            "스쿼드 FPP" 같은 모드 이름
 * @param map             "에란겔" 같은 맵 이름
 * @param matchType       "일반", "경쟁" 등
 * @param rank            등수
 * @param teams           참가 팀 수
 * @param kills           킬
 * @param knocks          기절시킨 수
 * @param damage          딜량 (소수점 버림)
 * @param survivedSeconds 생존 시간 (초)
 */
public record MatchSummary(Instant playedAt,
                           String mode,
                           String map,
                           String matchType,
                           int rank,
                           int teams,
                           int kills,
                           int knocks,
                           int damage,
                           int survivedSeconds) {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    /** 치킨 먹은 판인지 */
    public boolean isWin() {
        return rank == 1;
    }

    /** "10월 19일 21:34" */
    public String playedAtText() {
        return playedAt == null ? "" : DATE.format(playedAt.atZone(KOREA));
    }

    /** "23분 10초" */
    public String survivedText() {
        return (survivedSeconds / 60) + "분 " + (survivedSeconds % 60) + "초";
    }
}
