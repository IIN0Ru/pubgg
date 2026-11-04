package gg.pub.card;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.Platform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;

/**
 * 플레이어 카드를 DB(PostgreSQL)에 저장한다. 표는 schema.sql 의 player_card.
 *
 *  card_json   꾸민 값 (SavedCard 를 JSON 글자로)
 *  token_hash  수정 열쇠 SHA-256 (열쇠 자체는 저장하지 않음)
 *  png         디스코드·카톡 미리보기용 카드 그림
 *
 * 카드 하나 = 한 줄 (플랫폼 + 닉네임 소문자). FileCardStore 와 같은 규칙이다.
 */
@Component
@ConditionalOnProperty(name = "pubgg.card-store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcCardStore implements CardStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcCardStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Optional<Stored> find(Platform platform, String nickname) {
        return jdbc.query(
                        "SELECT card_json, token_hash FROM player_card WHERE platform = ? AND nickname_key = ?",
                        (rs, i) -> new Stored(read(rs.getString("card_json")), rs.getString("token_hash")),
                        platform.getShard(), key(nickname))
                .stream().findFirst();
    }

    @Override
    public Optional<byte[]> image(Platform platform, String nickname) {
        return jdbc.query(
                        "SELECT png FROM player_card WHERE platform = ? AND nickname_key = ?",
                        (rs, i) -> rs.getBytes("png"),
                        platform.getShard(), key(nickname))
                .stream().findFirst();
    }

    /** 같은 카드를 동시에 처음 저장하는 경우는 거의 없어서, 고쳐 쓰고 안 되면 새로 넣는 두 단계로 한다 */
    @Override
    public synchronized void write(Platform platform, String nickname, Stored card, byte[] png) {
        String json = write(card.card());
        int updated = jdbc.update(
                "UPDATE player_card SET card_json = ?, token_hash = ?, png = ?, updated_at = now() "
                        + "WHERE platform = ? AND nickname_key = ?",
                json, card.tokenHash(), png, platform.getShard(), key(nickname));
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO player_card (platform, nickname_key, card_json, token_hash, png) VALUES (?, ?, ?, ?, ?)",
                    platform.getShard(), key(nickname), json, card.tokenHash(), png);
        }
    }

    private static String key(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }

    private SavedCard read(String json) {
        try {
            return mapper.readValue(json, SavedCard.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String write(SavedCard card) {
        try {
            return mapper.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
