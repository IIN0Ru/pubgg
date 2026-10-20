package gg.pub.domain;

import java.util.Map;
import java.util.Optional;

import static gg.pub.domain.WeaponClass.*;

/**
 * 텔레메트리의 무기 ID(itemId) → 이름과 분류.
 *
 * 주무기 슬롯(1·2번)에 들어가는 총만 등록한다.
 * 권총, 근접무기, 투척무기, 석궁·패스트 같은 특수무기는 일부러 넣지 않았다 → 주무기 계산에서 빠진다.
 *
 * 이름 출처: github.com/pubg/api-assets (dictionaries/telemetry/item/itemId.json)
 * 분류(탄종)는 직접 정리. 새 총이 나오면 여기에 한 줄 추가하면 된다.
 */
public final class WeaponCatalog {

    private WeaponCatalog() {
    }

    public record Weapon(String name, WeaponClass type) {
    }

    private static final Map<String, Weapon> WEAPONS = Map.ofEntries(
            // 5.56 AR
            entry("Item_Weapon_HK416_C", "M416", AR_556),
            entry("Item_Weapon_Duncans_M416_C", "M416", AR_556),
            entry("Item_Weapon_SCAR-L_C", "SCAR-L", AR_556),
            entry("Item_Weapon_M16A4_C", "M16A4", AR_556),
            entry("Item_Weapon_QBZ95_C", "QBZ95", AR_556),
            entry("Item_Weapon_G36C_C", "G36C", AR_556),
            entry("Item_Weapon_K2_C", "K2", AR_556),
            entry("Item_Weapon_AUG_C", "AUG A3", AR_556),

            // 7.62 AR
            entry("Item_Weapon_AK47_C", "AKM", AR_762),
            entry("Item_Weapon_BerylM762_C", "Beryl M762", AR_762),
            entry("Item_Weapon_Groza_C", "Groza", AR_762),
            entry("Item_Weapon_Mk47Mutant_C", "Mk47 Mutant", AR_762),
            entry("Item_Weapon_ACE32_C", "ACE32", AR_762),

            // DMR
            entry("Item_Weapon_Mini14_C", "Mini 14", DMR),
            entry("Item_Weapon_Mk12_C", "Mk12", DMR),
            entry("Item_Weapon_QBU88_C", "QBU", DMR),
            entry("Item_Weapon_Mads_QBU88_C", "QBU", DMR),
            entry("Item_Weapon_SKS_C", "SKS", DMR),
            entry("Item_Weapon_FNFal_C", "SLR", DMR),
            entry("Item_Weapon_Mk14_C", "Mk14 EBR", DMR),
            entry("Item_Weapon_VSS_C", "VSS", DMR),
            entry("Item_Weapon_Dragunov_C", "Dragunov", DMR),

            // SR
            entry("Item_Weapon_Kar98k_C", "Kar98k", SR),
            entry("Item_Weapon_M24_C", "M24", SR),
            entry("Item_Weapon_AWM_C", "AWM", SR),
            entry("Item_Weapon_Mosin_C", "Mosin-Nagant", SR),
            entry("Item_Weapon_Win1894_C", "Win94", SR),
            entry("Item_Weapon_L6_C", "Lynx AMR", SR),

            // SMG
            entry("Item_Weapon_UMP_C", "UMP45", SMG),
            entry("Item_Weapon_Vector_C", "Vector", SMG),
            entry("Item_Weapon_UZI_C", "Micro UZI", SMG),
            entry("Item_Weapon_Thompson_C", "Tommy Gun", SMG),
            entry("Item_Weapon_BizonPP19_C", "PP-19 Bizon", SMG),
            entry("Item_Weapon_MP5K_C", "MP5K", SMG),
            entry("Item_Weapon_P90_C", "P90", SMG),
            entry("Item_Weapon_MP9_C", "MP9", SMG),
            entry("Item_Weapon_JS9_C", "JS9", SMG),

            // 샷건
            entry("Item_Weapon_Berreta686_C", "S686", SHOTGUN),
            entry("Item_Weapon_Saiga12_C", "S12K", SHOTGUN),
            entry("Item_Weapon_Winchester_C", "S1897", SHOTGUN),
            entry("Item_Weapon_DP12_C", "DBS", SHOTGUN),
            entry("Item_Weapon_OriginS12_C", "O12", SHOTGUN),

            // LMG
            entry("Item_Weapon_DP28_C", "DP-28", LMG),
            entry("Item_Weapon_M249_C", "M249", LMG),
            entry("Item_Weapon_MG3_C", "MG3", LMG)
    );

    private static Map.Entry<String, Weapon> entry(String itemId, String name, WeaponClass type) {
        return Map.entry(itemId, new Weapon(name, type));
    }

    /** 주무기로 등록된 총이면 이름·분류를, 아니면 빈 값 */
    public static Optional<Weapon> find(String itemId) {
        return Optional.ofNullable(WEAPONS.get(itemId));
    }

    public static boolean isPrimary(String itemId) {
        return WEAPONS.containsKey(itemId);
    }
}
