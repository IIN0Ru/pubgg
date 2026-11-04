package gg.pub.card;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.Platform;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 플레이어 카드를 DB(player_card 테이블)에 저장한다. 운영(prod 프로필)에서 쓴다.
 *
 * 한 카드 = 한 행. 행을 찾는 열쇠는 (플랫폼, 닉네임 소문자) 이고, FileCardStore 가 파일 이름에 쓰던 것과 같은 규칙이다.
 * 서버를 다시 켜도 DB 는 남아 있어서, 로컬 폴더에 저장하던 방식의 "재배포하면 카드가 사라지는" 문제가 없다.
 */
@Component
@Profile("prod")
public class JdbcCardStore implements CardStore {

    private static final String UPDATE =
            "UPDATE player_card SET card_json = ?, token_hash = ?, png = ? WHERE shard = ? AND nickname_key = ?";

    private static final String INSERT =
            "INSERT INTO player_card (card_json, token_hash, png, shard, nickname_key) VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcCardStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Optional<Stored> find(Platform platform, String nickname) {
        List<Stored> rows = jdbc.query(
                "SELECT card_json, token_hash FROM player_card WHERE shard = ? AND nickname_key = ?",
                (rs, rowNum) -> new Stored(fromJson(rs.getString("card_json")), rs.getString("token_hash")),
                platform.getShard(), key(nickname));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<byte[]> image(Platform platform, String nickname) {
        List<byte[]> rows = jdbc.query(
                "SELECT png FROM player_card WHERE shard = ? AND nickname_key = ?",
                (rs, rowNum) -> rs.getBytes("png"),
                platform.getShard(), key(nickname));
        return rows.stream().findFirst();
    }

    /**
     * 저장 (있으면 덮어씀).
     * 먼저 UPDATE 를 해보고, 고친 행이 0개(= 처음 저장)일 때만 INSERT 한다.
     * 같은 닉네임을 두 요청이 동시에 처음 저장하면 뒤에 온 INSERT 가 기본 키 중복으로 실패하는데,
     * 그때는 먼저 들어간 행을 덮어쓰는 UPDATE 로 마무리한다.
     */
    @Override
    public void write(Platform platform, String nickname, Stored stored, byte[] png) {
        String json = toJson(stored.card());
        String shard = platform.getShard();
        String key = key(nickname);

        int updated = jdbc.update(UPDATE, json, stored.tokenHash(), png, shard, key);
        if (updated == 0) {
            try {
                jdbc.update(INSERT, json, stored.tokenHash(), png, shard, key);
            } catch (DuplicateKeyException e) {
                jdbc.update(UPDATE, json, stored.tokenHash(), png, shard, key);
            }
        }
    }

    private static String key(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }

    private String toJson(SavedCard card) {
        try {
            return mapper.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SavedCard fromJson(String json) {
        try {
            return mapper.readValue(json, SavedCard.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
