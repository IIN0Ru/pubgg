package gg.pub.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 경기 하나에서 펍지지가 쓰는 정보만 담은 작은 기록.
 *
 * PUBG 경기 응답은 참가자 100명 기록이 다 들어 있어서 크다(수십 KB).
 * 그래서 처음 받을 때 나와 팀원 기록만 남기고 줄여서 저장해 둔다.
 * 최근 경기 목록, 플레이 카드, 함께한 팀원, 활동 시간대가 모두 이 기록을 쓴다.
 *
 * @param matchId      경기 ID
 * @param playedAt     경기 시작 시각
 * @param gameMode     squad-fpp 같은 원래 모드 이름
 * @param mapName      Baltic_Main 같은 원래 맵 이름
 * @param matchType    official(일반), competitive(경쟁) 등
 * @param teams        참가 팀 수
 * @param telemetryUrl 텔레메트리 파일 주소 (없으면 null)
 * @param me           내 기록
 * @param teammates    같은 팀 팀원 기록 (나 제외)
 */
public record MatchRecord(String matchId,
                          Instant playedAt,
                          String gameMode,
                          String mapName,
                          String matchType,
                          int teams,
                          String telemetryUrl,
                          MatchPlayer me,
                          List<MatchPlayer> teammates) {

    public static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    // ---------- 분류 ----------

    public boolean isCompetitive() {
        return "competitive".equals(matchType);
    }

    public boolean isFpp() {
        return gameMode != null && gameMode.endsWith("-fpp");
    }

    /** 목록 필터용: competitive / official / other */
    public String typeKey() {
        if (isCompetitive()) {
            return "competitive";
        }
        return "official".equals(matchType) ? "official" : "other";
    }

    /** 목록 필터용: fpp / tpp */
    public String perspectiveKey() {
        return isFpp() ? "fpp" : "tpp";
    }

    // ---------- 화면 표시용 ----------

    public int rank() {
        return me.rank();
    }

    public int kills() {
        return me.kills();
    }

    public int knocks() {
        return me.knocks();
    }

    public int damage() {
        return me.damage();
    }

    public boolean isWin() {
        return me.rank() == 1;
    }

    /** TOP 10 안에 든 판 (치킨 포함) */
    public boolean isTop10() {
        return me.rank() >= 1 && me.rank() <= 10;
    }

    /** 결과 표시 글자: 치킨 / TOP 10 / 없음 (색만으로 구분하지 않게 글자도 같이 보여준다) */
    public String resultBadge() {
        if (isWin()) {
            return "치킨";
        }
        return isTop10() ? "TOP 10" : "";
    }

    /** 화면에서 "3시간 전"을 계산하려고 넘기는 시각 (ISO-8601, 없으면 빈 문자열) */
    public String playedAtIso() {
        return playedAt == null ? "" : playedAt.toString();
    }

    public String survivedText() {
        return me.survivedText();
    }

    /** "스쿼드 FPP" */
    public String mode() {
        return GameLabels.gameMode(gameMode);
    }

    /** "에란겔" */
    public String map() {
        return GameLabels.map(mapName);
    }

    /** "일반", "경쟁" */
    public String matchTypeLabel() {
        return GameLabels.matchType(matchType);
    }

    /** "10월 19일 21:34" (한국 시간) */
    public String playedAtText() {
        return playedAt == null ? "" : DATE.format(playedAt.atZone(KOREA));
    }

    public boolean hasTeammates() {
        return !teammates.isEmpty();
    }
}
