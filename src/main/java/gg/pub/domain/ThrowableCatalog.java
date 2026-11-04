package gg.pub.domain;

import java.util.Map;

/**
 * 투척무기 itemId → 화면에 보여줄 이름.
 * 모르는 것(새로 나온 투척무기, 기록에 이름이 없는 경우)은 "기타 투척"으로 묶는다.
 */
public final class ThrowableCatalog {

    private static final String OTHER = "기타 투척";

    private static final Map<String, String> NAMES = Map.of(
            "Item_Weapon_Grenade_C", "수류탄",
            "Item_Weapon_Grenade_Warmode_C", "수류탄",
            "Item_Weapon_SmokeBomb_C", "연막탄",
            "Item_Weapon_Molotov_C", "화염병",
            "Item_Weapon_FlashBang_C", "섬광탄",
            "Item_Weapon_StickyGrenade_C", "점착 폭탄",
            "Item_Weapon_BluezoneGrenade_C", "블루존 수류탄",
            "Item_Weapon_DecoyGrenade_C", "디코이 수류탄");

    private ThrowableCatalog() {
    }

    public static String label(String itemId) {
        return NAMES.getOrDefault(itemId, OTHER);
    }
}
