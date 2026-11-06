package gg.pub.card;

import java.time.Duration;
import java.util.Optional;

/**
 * 공유한 B카드를 넣고 꺼내는 곳.
 * 구현이 둘이다. 어느 쪽을 쓸지는 실행 프로필로 정한다 (CardService·컨트롤러·화면은 그대로).
 *  - FileCardStore: 서버 폴더에 파일로 저장. 로컬 개발용 (기본)
 *  - JdbcCardStore: DB(PostgreSQL)에 저장. 운영용 (prod 프로필)
 *
 * 보관 규칙 ("일회용" 느낌):
 *  - 같은 플레이어가 새로 공유하면 이전 공유 카드의 그림은 비워진다. 이전 링크는 계속 열리지만 미리보기 그림은 사라진다
 *  - 만든 지 {@link #RETENTION} 이 지난 공유 카드는 새로 공유할 때 지운다
 */
public interface CardStore {

    Duration RETENTION = Duration.ofDays(30);

    /** 새 공유 카드를 저장한다. 같은 플레이어의 이전 공유 카드 그림을 비우고, 오래된 카드를 지운다 */
    void create(SharedCard card, byte[] png);

    /** 공유 카드. 없거나 지워졌으면 빈 값 (그림이 비워져도 카드는 남아 있다) */
    Optional<SharedCard> find(String id);

    /** 미리보기용 카드 그림 (PNG). 없거나 비워졌으면 빈 값 */
    Optional<byte[]> image(String id);

    /** 그림이 남아 있는지 (그림 내용을 읽지 않고 확인) */
    boolean hasImage(String id);
}
