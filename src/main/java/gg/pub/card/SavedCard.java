package gg.pub.card;

import java.time.Instant;
import java.util.Map;

/**
 * 사이트에 저장한 플레이어 카드. 카드 그림 자체가 아니라 "어떻게 꾸몄는지"만 담는다.
 * KDA·평균 딜·태그 같은 기록 값은 저장하지 않고, 볼 때마다 최신 검색 결과(ShareCard)로 채운다.
 *
 * @param platform   steam / kakao
 * @param nickname   PUBG 에 등록된 정확한 닉네임
 * @param choices    꾸민 값 (CardChoices 가 검사한 것만): mic, aim, find, role, tee, suit, outer, oc, bottom, bc
 * @param background 배경 맵과 구역 (저장한 그대로 다시 그리려고)
 * @param savedAt    마지막으로 저장한 시각
 */
public record SavedCard(String platform,
                        String nickname,
                        Map<String, String> choices,
                        Background background,
                        Instant savedAt) {

    /**
     * 배경: 글자 없는 맵(2048px) 위 구역의 왼쪽 위 좌표.
     *
     * @param map erangel / miramar / taego / rondo
     */
    public record Background(String map, int sx, int sy) {
    }
}
