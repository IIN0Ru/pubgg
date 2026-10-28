package gg.pub.card;

import gg.pub.domain.Platform;

import java.util.Optional;

/**
 * 저장한 플레이어 카드를 넣고 꺼내는 곳.
 * 지금은 서버 폴더에 파일로 저장한다 (FileCardStore).
 * 나중에 DB 를 붙이면 이 인터페이스를 DB 로 구현한 클래스 하나만 새로 만들면 된다 (CardService·컨트롤러·화면은 그대로).
 */
public interface CardStore {

    /** 저장된 카드 + 수정 열쇠(해시). 없으면 빈 값 */
    Optional<Stored> find(Platform platform, String nickname);

    /** 미리보기용 카드 그림 (PNG). 없으면 빈 값 */
    Optional<byte[]> image(Platform platform, String nickname);

    /** 저장 (있으면 덮어씀) */
    void write(Platform platform, String nickname, Stored card, byte[] png);

    /**
     * @param card      꾸민 값
     * @param tokenHash 처음 저장한 브라우저의 수정 열쇠 SHA-256 (열쇠 자체는 저장하지 않음)
     */
    record Stored(SavedCard card, String tokenHash) {
    }
}
