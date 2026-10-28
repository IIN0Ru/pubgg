package gg.pub.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.Platform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * 플레이어 카드를 서버 폴더에 파일로 저장한다.
 *
 *  {pubgg.card-dir}/steam/<닉네임>.json   꾸민 값 + 수정 열쇠 해시
 *  {pubgg.card-dir}/steam/<닉네임>.png    디스코드·카톡 미리보기용 카드 그림
 *
 * 파일 이름은 닉네임(소문자)을 URL 안전 Base64 로 바꾼 것 (어떤 글자가 와도 경로가 깨지지 않게).
 * 쓸 때는 임시 파일에 쓰고 옮겨서, 쓰다 만 파일이 보이지 않게 한다.
 * 배포할 때는 이 폴더가 서버를 다시 켜도 남아 있어야 한다.
 */
@Component
public class FileCardStore implements CardStore {

    private final Path root;
    private final ObjectMapper mapper;

    public FileCardStore(@Value("${pubgg.card-dir:./data/cards}") String dir, ObjectMapper mapper) {
        this.root = Path.of(dir);
        this.mapper = mapper;
    }

    @Override
    public Optional<Stored> find(Platform platform, String nickname) {
        Path json = file(platform, nickname, ".json");
        if (!Files.exists(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json.toFile(), Stored.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<byte[]> image(Platform platform, String nickname) {
        Path png = file(platform, nickname, ".png");
        try {
            return Files.exists(png) ? Optional.of(Files.readAllBytes(png)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void write(Platform platform, String nickname, Stored card, byte[] png) {
        try {
            Files.createDirectories(file(platform, nickname, ".json").getParent());
            replace(file(platform, nickname, ".png"), png);
            replace(file(platform, nickname, ".json"), mapper.writeValueAsBytes(card));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void replace(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path file(Platform platform, String nickname, String ext) {
        String name = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(nickname.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return root.resolve(platform.getShard()).resolve(name + ext);
    }
}
