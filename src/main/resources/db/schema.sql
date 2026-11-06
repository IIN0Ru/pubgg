-- 공유한 B카드 저장 테이블 (운영 prod 프로필에서 서버가 켜질 때 실행된다)
-- IF NOT EXISTS 라서 여러 번 실행해도 이미 있는 테이블과 데이터는 그대로 둔다.
-- PostgreSQL 과 테스트용 H2(PostgreSQL 모드) 양쪽에서 같은 문장으로 돌아가게 썼다.
--
-- 예전 player_card 테이블(닉네임당 카드 하나 + 수정 열쇠 방식)은 더 이상 쓰지 않는다.
-- 데이터를 날리는 문장은 서버가 켜질 때 자동으로 돌리지 않는다. 필요하면 직접 DROP TABLE player_card 를 실행하면 된다.
CREATE TABLE IF NOT EXISTS share_card (
    id           VARCHAR(32)  PRIMARY KEY,   -- 공유 주소 /s/{id} 의 무작위 ID
    shard        VARCHAR(20)  NOT NULL,      -- steam / kakao
    nickname     VARCHAR(100) NOT NULL,      -- PUBG 에 등록된 정확한 닉네임
    nickname_key VARCHAR(100) NOT NULL,      -- 닉네임을 소문자로 바꾼 값 (같은 플레이어의 이전 공유를 찾을 때)
    card_json    TEXT         NOT NULL,      -- 꾸민 값 (SharedCard 를 JSON 으로)
    png          BYTEA,                      -- 링크 미리보기용 카드 그림. NULL = 새로 공유해서 비워짐
    created_at   TIMESTAMP    NOT NULL       -- 공유한 시각 (30일이 지나면 지운다)
);

CREATE INDEX IF NOT EXISTS share_card_owner ON share_card (shard, nickname_key);
