-- 앱이 켜질 때마다 실행된다 (spring.sql.init). 이미 있으면 건드리지 않는다.
-- 저장한 플레이어 카드: 한 줄 = 카드 하나 (플랫폼 + 닉네임 소문자)
CREATE TABLE IF NOT EXISTS player_card (
    platform      VARCHAR(16)  NOT NULL,
    nickname_key  VARCHAR(64)  NOT NULL,
    card_json     TEXT         NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    png           BYTEA        NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (platform, nickname_key)
);
