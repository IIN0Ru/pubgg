-- 플레이어 카드 저장 테이블 (운영 prod 프로필에서 서버가 켜질 때 실행된다)
-- IF NOT EXISTS 라서 여러 번 실행해도 이미 있는 테이블과 데이터는 그대로 둔다.
-- PostgreSQL 과 테스트용 H2(PostgreSQL 모드) 양쪽에서 같은 문장으로 돌아가게 썼다.
CREATE TABLE IF NOT EXISTS player_card (
    shard        VARCHAR(20)  NOT NULL,  -- steam / kakao
    nickname_key VARCHAR(100) NOT NULL,  -- 닉네임을 소문자로 바꾼 값 (대소문자 달라도 같은 카드)
    card_json    TEXT         NOT NULL,  -- 꾸민 값 (SavedCard 를 JSON 으로)
    token_hash   VARCHAR(100) NOT NULL,  -- 처음 저장한 브라우저의 수정 열쇠 SHA-256
    png          BYTEA        NOT NULL,  -- 링크 미리보기용 카드 그림
    PRIMARY KEY (shard, nickname_key)    -- 플랫폼 + 닉네임당 카드는 하나
);
