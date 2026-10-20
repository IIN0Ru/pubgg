package gg.pub.domain;

/**
 * 한 경기에서 한 사람의 기록 (나 또는 팀원).
 *
 * @param name            닉네임
 * @param accountId       계정 ID (account.xxx)
 * @param kills           킬
 * @param knocks          기절시킨 수
 * @param assists         어시스트
 * @param damage          딜량 (소수점 버림)
 * @param rank            등수 (팀 등수)
 * @param survivedSeconds 생존 시간 (초)
 * @param headshotKills   헤드샷 킬
 * @param longestKill     최장 킬 거리 (m, 소수점 버림)
 * @param revives         팀원 부활 횟수
 * @param deathType       어떻게 끝났는지: alive, byplayer, byzone, suicide, logout
 */
public record MatchPlayer(String name,
                          String accountId,
                          int kills,
                          int knocks,
                          int assists,
                          int damage,
                          int rank,
                          int survivedSeconds,
                          int headshotKills,
                          int longestKill,
                          int revives,
                          String deathType) {

    /** "23분 10초" */
    public String survivedText() {
        return (survivedSeconds / 60) + "분 " + (survivedSeconds % 60) + "초";
    }

    /** 어떻게 끝났는지 한글로 */
    public String deathTypeLabel() {
        if (deathType == null) {
            return "";
        }
        return switch (deathType) {
            case "alive" -> "끝까지 생존";
            case "byplayer" -> "적에게 사망";
            case "byzone" -> "자기장 사망";
            case "suicide" -> "자멸";
            case "logout" -> "나감";
            default -> deathType;
        };
    }
}
