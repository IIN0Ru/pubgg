package gg.pub.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 공유한 B카드를 서버 폴더에 파일로 저장한다.
 *
 *  {pubgg.card-dir}/<ID>.json   꾸민 값
 *  {pubgg.card-dir}/<ID>.png    디스코드·카톡 미리보기용 카드 그림 (새로 공유하면 이전 것은 지워진다)
 *
 * 쓸 때는 임시 파일에 쓰고 옮겨서, 쓰다 만 파일이 보이지 않게 한다.
 * 로컬 개발용이다 (prod 프로필이 아닐 때만 쓰인다). 운영에서는 서버를 다시 켜면 폴더가 사라지는 호스팅이 많아서
 * DB 로 저장하는 JdbcCardStore 를 쓴다.
 */
@Component
@Profile("!prod")
public class FileCardStore implements CardStore {

    private final Path root;
    private final ObjectMapper mapper;

    public FileCardStore(@Value("${pubgg.card-dir:./data/cards}") String dir, ObjectMapper mapper) {
        this.root = Path.of(dir);
        this.mapper = mapper;
    }

    @Override
    public synchronized void create(SharedCard card, byte[] png) {
        try {
            Files.createDirectories(root);
            clean(card);
            replace(file(card.id(), ".png"), png);
            replace(file(card.id(), ".json"), mapper.writeValueAsBytes(card));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 같은 플레이어의 이전 공유는 그림을 지우고, 보관 기간이 지난 공유는 통째로 지운다 */
    private void clean(SharedCard fresh) throws IOException {
        List<Path> jsons;
        try (Stream<Path> files = Files.list(root)) {
            jsons = files.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
        }
        for (Path json : jsons) {
            SharedCard old;
            try {
                old = mapper.readValue(json.toFile(), SharedCard.class);
            } catch (IOException e) {
                continue;   // 읽을 수 없는 파일은 건드리지 않는다
            }
            Path png = file(old.id(), ".png");
            if (old.createdAt().isBefore(fresh.createdAt().minus(RETENTION))) {
                Files.deleteIfExists(png);
                Files.deleteIfExists(json);
            } else if (sameOwner(old, fresh)) {
                Files.deleteIfExists(png);
            }
        }
    }

    private static boolean sameOwner(SharedCard a, SharedCard b) {
        return a.platform().equals(b.platform())
                && a.nickname().toLowerCase(Locale.ROOT).equals(b.nickname().toLowerCase(Locale.ROOT));
    }

    @Override
    public Optional<SharedCard> find(String id) {
        if (!SharedCard.isValidId(id)) {
            return Optional.empty();
        }
        Path json = file(id, ".json");
        if (!Files.exists(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json.toFile(), SharedCard.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<byte[]> image(String id) {
        if (!SharedCard.isValidId(id)) {
            return Optional.empty();
        }
        Path png = file(id, ".png");
        try {
            return Files.exists(png) ? Optional.of(Files.readAllBytes(png)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasImage(String id) {
        return SharedCard.isValidId(id) && Files.exists(file(id, ".png"));
    }

    private static void replace(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** id 는 isValidId 를 통과한 값만 온다 (파일 이름으로 써도 경로가 깨지지 않는다) */
    private Path file(String id, String ext) {
        return root.resolve(id + ext);
    }
}
