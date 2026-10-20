package gg.pub.domain.replay;

import gg.pub.domain.GameLabels;

import java.util.Map;
import java.util.Optional;

/**
 * 리플레이 지도 정보.
 * 이미지는 PUBG 공식 자료(github.com/pubg/api-assets, Assets/Maps)의 저해상도 지도를
 * static/images/maps 에 넣어 두고 쓴다. 지금은 경쟁전(랭크) 맵 4개만.
 *
 * @param name  맵 내부 이름 (Baltic_Main ...)
 * @param label 한글 이름
 * @param image 이미지 주소 (/images/maps/...)
 * @param size  지도 한 변 길이 (m). 이미지 한 변이 이 길이에 해당
 */
public record MapInfo(String name, String label, String image, int size) {

    /** 경쟁전 맵은 모두 8km × 8km. (론도 8km 는 아직 실제 데이터로 확인 전) */
    private static final int EIGHT_KM = 8_160;

    private static final Map<String, String> IMAGES = Map.of(
            "Baltic_Main", "/images/maps/erangel.png",
            "Erangel_Main", "/images/maps/erangel.png",
            "Desert_Main", "/images/maps/miramar.png",
            "Tiger_Main", "/images/maps/taego.png",
            "Neon_Main", "/images/maps/rondo.jpg");

    /** 리플레이를 그릴 수 있는 맵이면 정보를, 아니면 빈 값 */
    public static Optional<MapInfo> of(String mapName) {
        String image = mapName == null ? null : IMAGES.get(mapName);
        if (image == null) {
            return Optional.empty();
        }
        return Optional.of(new MapInfo(mapName, GameLabels.map(mapName), image, EIGHT_KM));
    }
}
