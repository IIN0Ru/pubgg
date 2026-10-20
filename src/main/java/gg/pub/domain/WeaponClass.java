package gg.pub.domain;

/**
 * 주무기 분류. "이 사람이 5.56을 쓰는지, 7.62를 쓰는지, SR 유저인지 DMR 유저인지"를 보기 위한 것.
 */
public enum WeaponClass {

    AR_556("5.56 AR"),
    AR_762("7.62 AR"),
    DMR("DMR"),
    SR("SR"),
    SMG("SMG"),
    SHOTGUN("샷건"),
    LMG("LMG"),
    OTHER("기타");

    private final String label;

    WeaponClass(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
