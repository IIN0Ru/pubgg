package gg.pub.domain.replay;

import gg.pub.domain.GameLabels;

import java.util.Map;
import java.util.Optional;

/**
 * 리플레이 지도 정보.
 * 이미지는 PUBG 공식 자료(github.com/pubg/api-assets, Assets/Maps)의 고해상도 지도(8192px)를
 * 512px 조각(타일)으로 잘라 static/images/maps/tiles/{지도}/{단계}/{x}_{y}.webp 에 넣어 두고,
 * 화면에 보이는 조각만 불러온다. 단계 0 = 지도 전체 1조각(512px), 단계 4 = 16×16조각(8192px).
 * 지금은 경쟁전(랭크) 맵 4개만.
 *
 * @param name   맵 내부 이름 (Baltic_Main ...)
 * @param label  한글 이름
 * @param tiles  조각 폴더 주소 (/images/maps/tiles/erangel)
 * @param levels 조각 단계 수 (0 ~ levels-1)
 * @param size   지도 한 변 길이 (m). 이미지 한 변이 이 길이에 해당
 */
public record MapInfo(String name, String label, String tiles, int levels, int size) {

    /** 경쟁전 맵은 모두 8km × 8km. (론도 8km 는 아직 실제 데이터로 확인 전) */
    private static final int EIGHT_KM = 8_160;

    /** 512 → 1024 → 2048 → 4096 → 8192 */
    static final int LEVELS = 5;

    private static final Map<String, String> TILES = Map.of(
            "Baltic_Main", "/images/maps/tiles/erangel",
            "Erangel_Main", "/images/maps/tiles/erangel",
            "Desert_Main", "/images/maps/tiles/miramar",
            "Tiger_Main", "/images/maps/tiles/taego",
            "Neon_Main", "/images/maps/tiles/rondo");

    /** 리플레이를 그릴 수 있는 맵이면 정보를, 아니면 빈 값 */
    public static Optional<MapInfo> of(String mapName) {
        String tiles = mapName == null ? null : TILES.get(mapName);
        if (tiles == null) {
            return Optional.empty();
        }
        return Optional.of(new MapInfo(mapName, GameLabels.map(mapName), tiles, LEVELS, EIGHT_KM));
    }
}
