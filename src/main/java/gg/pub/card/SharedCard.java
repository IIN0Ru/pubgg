package gg.pub.card;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 공유할 때 만든 B카드 한 장. 카드 그림 자체가 아니라 "어떻게 꾸몄는지"만 담는다.
 * KDA·평균 딜 같은 기록 값은 저장하지 않는다. 공유 링크를 열면 전적 화면(A카드)이 늘 최신 값으로 나온다.
 *
 * @param id         공유 주소 /s/{id} 에 쓰는 무작위 ID
 * @param platform   steam / kakao
 * @param nickname   PUBG 에 등록된 정확한 닉네임
 * @param choices    꾸민 값 (CardChoices 가 검사한 것만): mic, time, aim, find, role
 * @param background 배경 맵과 구역 (공유한 그림과 같은 모습)
 * @param createdAt  공유한 시각
 */
public record SharedCard(String id,
                         String platform,
                         String nickname,
                         Map<String, String> choices,
                         Background background,
                         Instant createdAt) {

    /** 저장소가 파일 이름·DB 조회에 쓰기 전에 걸러 내는 ID 모양 (경로를 거슬러 올라가는 값 등을 막는다) */
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{6,32}");

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /**
     * 배경: 글자 없는 맵(2048px) 위 구역의 왼쪽 위 좌표.
     *
     * @param map erangel / miramar / taego / rondo
     */
    public record Background(String map, int sx, int sy) {
    }
}
