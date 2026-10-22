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
 * @param hits        총 명중: 우리 팀이 맞힌 것 + 적이 우리 팀을 맞힌 것 (빗나간 총알은 기록에 없음)
 * @param plane       비행기 경로 (알 수 없으면 null)
 * @param fights      우리 팀 교전 묶음 (동선 전체 보기에서 번호로 표시)
 * @param blasts      우리 팀이 피해를 준 투척·박격포·판처 한 발씩 (터진 곳은 맞은 사람 위치로 추정)
 * @param heals       우리 팀 회복·부스트템 사용
 * @param hurts       우리 팀이 받은 피해 (죽기 전 30초 요약용). 같은 사람에게 같은 무기로 2초 안에 이어 맞으면 한 줄
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
                     List<Hit> hits,
                     Plane plane,
                     List<Fight> fights,
                     List<Blast> blasts,
                     List<Heal> heals,
                     List<Hurt> hurts) {

    /**
     * 한 사람의 이동 기록. 같은 순서의 t[i], x[i], y[i] 가 한 점이다 (배열로 보내야 JSON 이 작다).
     *
     * @param id     계정 ID
     * @param name   닉네임
     * @param team   팀 번호
     * @param t      시각 (초)
     * @param x      x (m)
     * @param y      y (m)
     * @param v      그 순간 탄 것: 0 = 걸어서, 1 = 차량(바이크 포함), 2 = 보트
     * @param hp     그 순간 체력 (0~100). 위치 기록의 체력에서 그 뒤 받은 피해·회복을 더하고 뺀 값
     * @param diedAt 죽은 시각 (초). 끝까지 살았으면 null
     * @param at     총을 맞힌 시각들 (초). 추정 시야에서 "이때 이쪽을 보고 쐈다"로 씀
     * @param aa     그때 쏜 방향 (도, 0 = 지도 오른쪽, 시계 방향으로 증가 = 화면 아래쪽)
     */
    public record Track(String id, String name, int team, int[] t, int[] x, int[] y, int[] v, int[] hp, Integer diedAt,
                        int[] at, int[] aa) {

        public static final int ON_FOOT = 0;
        public static final int CAR = 1;
        public static final int BOAT = 2;
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
     * @param cause  기절·킬을 무엇으로 했는지: grenade / molotov / c4 / sticky (투척), mortar (박격포),
     *               panzer (판처파우스트). 총·근접 등은 null
     */
    public record Event(int t, String type, int x, int y, String actor, String target, boolean ours, String text,
                        String cause) {
    }

    /**
     * 총 명중 한 번. 맞힌 순간 쏜 사람 → 맞은 사람 위치에 선을 긋는 데 쓴다.
     *
     * @param t        시각 (초)
     * @param ax       쏜 사람 위치 x (m)
     * @param ay       쏜 사람 위치 y (m)
     * @param vx       맞은 사람 위치 x (m)
     * @param vy       맞은 사람 위치 y (m)
     * @param ours     우리 팀이 쏜 것이면 true, 적이 우리 팀을 맞힌 것이면 false
     * @param attacker 쏜 사람 닉네임
     * @param victim   맞은 사람 닉네임
     */
    public record Hit(int t, int ax, int ay, int vx, int vy, boolean ours, String attacker, String victim) {
    }

    /**
     * 우리 팀이 던지거나 쏜 폭발물 한 발 (적에게 피해를 준 것만).
     * 기록에는 터진 좌표가 없어서, 같은 한 발에 맞은 적들의 위치 가운데를 터진 곳으로 본다 (피해 범위가 몇 m 라 오차가 작음).
     *
     * @param t        터진 시각 (첫 피해, 초)
     * @param kind     grenade / molotov / c4 / sticky / mortar / panzer
     * @param fromX    던진·쏜 사람 위치 x (m)
     * @param fromY    던진·쏜 사람 위치 y (m)
     * @param x        터진 곳 x (추정, m)
     * @param y        터진 곳 y (추정, m)
     * @param attacker 던진·쏜 사람 닉네임
     * @param result   kill / knock / hit (이 한 발로 잡았는지)
     * @param victims  맞은 적 수
     */
    public record Blast(int t, String kind, int fromX, int fromY, int x, int y, String attacker, String result,
                        int victims) {
    }

    /**
     * 우리 팀 회복·부스트템 사용.
     *
     * @param t    시각 (초)
     * @param name 쓴 사람 닉네임
     * @param x    위치 x (m)
     * @param y    위치 y (m)
     * @param item 아이템 이름 (구급상자, 에너지 드링크 ...)
     */
    public record Heal(int t, String name, int x, int y, String item) {
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
     * @param enemyTeam 가장 많이 주고받은 상대 팀 번호 (없으면 -1)
     * @param converted 우리가 기절시킨 적 중 우리 팀이 끝내 죽인 수 (knocks 중)
     * @param losses    우리 팀이 적에게 당한 횟수 (기절, 또는 기절 없이 바로 사망)
     * @param traded    그중 10초 안에 우리 팀이 그 적을 기절·킬해서 복수한 수
     * @param revived   우리 팀 기절 중 살려낸 수 (knocked 중)
     */
    public record Fight(int n, int start, int end, int x, int y, int kills, int knocks, int knocked, int deaths,
                        int enemyTeam, int converted, int losses, int traded, int revived) {
    }

    /**
     * 우리 팀이 받은 피해 한 줄 (같은 사람에게 같은 무기로 2초 안에 이어 맞은 건 합침).
     *
     * @param t        처음 맞은 시각 (초)
     * @param victim   맞은 우리 팀원 닉네임
     * @param attacker 때린 사람 닉네임 (자기장·낙하 등은 null)
     * @param weapon   무엇으로: 총 이름 (M416 ...), 수류탄, 자기장, 낙하, 차량 ...
     * @param damage   합친 피해량 (반올림)
     * @param hits     합친 횟수
     * @param hp       마지막으로 맞은 뒤 남은 체력 (기절 중이면 기절 체력이 아니라 0)
     * @param distance 때린 사람과의 거리 (m, 때린 사람이 없으면 -1)
     * @param height   때린 사람이 몇 m 위에 있었는지 (아래면 음수, 없으면 0)
     * @param headshot 한 번이라도 머리에 맞았는지
     */
    public record Hurt(int t, String victim, String attacker, String weapon, int damage, int hits, int hp,
                       int distance, int height, boolean headshot) {
    }
}
