package gg.pub.domain.replay;

import java.util.List;

/**
 * 2D 리플레이 한 경기에 필요한 것만 추린 데이터. 화면(replay.js)이 JSON 으로 받아서 그린다.
 *
 * 텔레메트리 원본은 한 경기에 수 MB 라서 그대로 보내지 않고,
 * 위치는 미터 단위 정수, 시각은 경기 시작부터 초 단위 정수로 줄여서 보낸다.
 * 좌표: 지도 왼쪽 위가 (0, 0), 오른쪽·아래로 갈수록 커진다 (PUBG 좌표를 100으로 나눈 값).
 *
 * @param matchId     경기 ID
 * @param map         지도 정보 (이미지, 크기)
 * @param duration    경기 길이 (초, 마지막 기록 기준)
 * @param me          검색한 플레이어 계정 ID
 * @param myTeam      그 플레이어의 팀 번호
 * @param players     전원 이동 기록
 * @param zones       자기장 기록 (약 10초마다)
 * @param phases      페이즈가 바뀐 시각
 * @param events      기절·킬·부활·착지·차량·보급 등
 * @param plane       비행기 경로 (알 수 없으면 null)
 * @param fights      우리 팀 교전 묶음 (동선 전체 보기에서 번호로 표시)
 */
public record Replay(String matchId,
                     MapInfo map,
                     int duration,
                     String me,
                     int myTeam,
                     List<Track> players,
                     List<Zone> zones,
                     List<Phase> phases,
                     List<Event> events,
                     Plane plane,
                     List<Fight> fights) {

    /**
     * 한 사람의 이동 기록. 같은 순서의 t[i], x[i], y[i] 가 한 점이다 (배열로 보내야 JSON 이 작다).
     *
     * @param id     계정 ID
     * @param name   닉네임
     * @param team   팀 번호
     * @param t      시각 (초)
     * @param x      x (m)
     * @param y      y (m)
     * @param diedAt 죽은 시각 (초). 끝까지 살았으면 null
     */
    public record Track(String id, String name, int team, int[] t, int[] x, int[] y, Integer diedAt) {
    }

    /**
     * 자기장 한 순간.
     * safe = 지금 자기장 경계(파란 원, 이 밖은 피해), next = 다음 안전구역(흰 원)
     */
    public record Zone(int t, int safeX, int safeY, int safeR, int nextX, int nextY, int nextR) {
    }

    /** n 페이즈가 시작된 시각 (다음 안전구역 흰 원이 새로 정해진 순간) */
    public record Phase(int n, int t) {
    }

    /**
     * 사건 하나.
     *
     * @param t      시각 (초)
     * @param type   landing / knock / kill / revive / ride / leave / package
     * @param x      위치 x (m)
     * @param y      위치 y (m)
     * @param actor  한 사람 닉네임 (때린 사람, 살린 사람, 내린 사람 ...). 없으면 null
     * @param target 당한 사람 닉네임. 없으면 null
     * @param ours   우리 팀이 관련된 사건인지 (경기 흐름 목록에는 이것만 나온다)
     * @param text   목록에 보여줄 한 줄
     */
    public record Event(int t, String type, int x, int y, String actor, String target, boolean ours, String text) {
    }

    /** 비행기 경로: 첫 낙하 지점에서 마지막 낙하 지점까지를 이은 직선 */
    public record Plane(int x1, int y1, int x2, int y2) {
    }

    /**
     * 우리 팀 교전 하나 (기절·킬이 30초 안에 이어지면 같은 교전).
     *
     * @param n       번호 (1부터)
     * @param start   시작 (초)
     * @param end     끝 (초)
     * @param x       교전 위치 평균 x (m)
     * @param y       교전 위치 평균 y (m)
     * @param kills   우리 팀이 낸 킬
     * @param knocks  우리 팀이 기절시킨 수
     * @param knocked 우리 팀이 기절당한 수
     * @param deaths  우리 팀이 죽은 수
     */
    public record Fight(int n, int start, int end, int x, int y, int kills, int knocks, int knocked, int deaths) {
    }
}
