package gg.pub.card;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 공유한 B카드를 DB(share_card 테이블)에 저장한다. 운영(prod 프로필)에서 쓴다.
 *
 * 한 공유 = 한 행. 행을 찾는 열쇠는 무작위 ID 이고, "같은 플레이어의 이전 공유"를 찾을 때는
 * (플랫폼, 닉네임 소문자) 로 찾는다. 이전 공유는 행을 지우지 않고 그림(png)만 NULL 로 비운다 → 이전 링크는 계속 열린다.
 * 서버를 다시 켜도 DB 는 남아 있어서, 로컬 폴더에 저장하던 방식의 "재배포하면 사라지는" 문제가 없다.
 */
@Component
@Profile("prod")
public class JdbcCardStore implements CardStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcCardStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** 세 문장이 함께 성공하거나 함께 취소되게 한다 (그림만 비우고 새 행이 안 들어가는 일이 없도록) */
    @Override
    @Transactional
    public void create(SharedCard card, byte[] png) {
        jdbc.update("DELETE FROM share_card WHERE created_at < ?",
                Timestamp.from(card.createdAt().minus(RETENTION)));
        jdbc.update("UPDATE share_card SET png = NULL WHERE shard = ? AND nickname_key = ? AND png IS NOT NULL",
                card.platform(), key(card.nickname()));
        jdbc.update("INSERT INTO share_card (id, shard, nickname, nickname_key, card_json, png, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                card.id(), card.platform(), card.nickname(), key(card.nickname()),
                toJson(card), png, Timestamp.from(card.createdAt()));
    }

    @Override
    public Optional<SharedCard> find(String id) {
        if (!SharedCard.isValidId(id)) {
            return Optional.empty();
        }
        List<SharedCard> rows = jdbc.query(
                "SELECT card_json FROM share_card WHERE id = ?",
                (rs, rowNum) -> fromJson(rs.getString("card_json")), id);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<byte[]> image(String id) {
        if (!SharedCard.isValidId(id)) {
            return Optional.empty();
        }
        List<byte[]> rows = jdbc.query(
                "SELECT png FROM share_card WHERE id = ?",
                (rs, rowNum) -> rs.getBytes("png"), id);
        return rows.stream().filter(Objects::nonNull).findFirst();   // 그림이 비워진 행은 NULL
    }

    @Override
    public boolean hasImage(String id) {
        if (!SharedCard.isValidId(id)) {
            return false;
        }
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM share_card WHERE id = ? AND png IS NOT NULL", Integer.class, id);
        return n != null && n > 0;
    }

    private static String key(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }

    private String toJson(SharedCard card) {
        try {
            return mapper.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SharedCard fromJson(String json) {
        try {
            return mapper.readValue(json, SharedCard.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
