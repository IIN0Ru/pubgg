package gg.pub.domain;

/**
 * 플레이 카드에 보여줄 주무기 하나.
 *
 * @param name         총 이름 (예: "Beryl M762")
 * @param type         분류 (예: 7.62 AR)
 * @param sharePercent 주무기를 들고 있던 전체 시간 중 이 총의 비중 (%)
 */
public record WeaponUsage(String name, WeaponClass type, int sharePercent) {

    public String typeLabel() {
        return type.getLabel();
    }
}
