package gg.pub.service;

import gg.pub.domain.Platform;
import gg.pub.domain.PlayCard;
import gg.pub.domain.PlayerMatches;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 검색 결과를 저장해 두고, "전적 갱신"을 눌렀을 때만 새로 불러온다. (OP.GG 방식)
 *
 *  - 처음 검색: PUBG API 로 경기 목록을 불러와 저장하고 바로 보여줌. 카드(텔레메트리 분석)는 뒤에서 따로 만들어 채움
 *  - 다시 검색: 저장된 결과를 바로 보여줌 (API 를 부르지 않음)
 *  - 갱신: 마지막 갱신 후 2분이 지났으면 새로 불러옴. 2분 안이면 저장된 결과 그대로
 *
 * 이유: PUBG API 는 플레이어 조회가 1분에 10번으로 제한되고, 한 번 검색에 경기 수십 개를 불러와서 느리다.
 *
 * 주의: 지금은 서버 메모리에 저장해서 서버를 다시 켜면 비워진다. 배포할 때 다시 검토.
 */
@Service
public class PlayerLookupService {

    /** 갱신 후 다시 갱신할 수 있을 때까지 기다리는 시간 */
    static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(2);

    /** 카드 꾸미기 화면이 카드 계산을 기다리는 최대 시간 */
    static final long CARD_WAIT_SECONDS = 180;

    private static final Logger log = LoggerFactory.getLogger(PlayerLookupService.class);
    private static final Executor CARD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final PlayerService playerService;
    private final Clock clock;
    private final Map<String, Lookup> store = new ConcurrentHashMap<>();
    /** 카드를 계산 중인 작업. 끝나면 지운다 */
    private final Map<String, CompletableFuture<Lookup>> cardTasks = new ConcurrentHashMap<>();

    public PlayerLookupService(PlayerService playerService, Clock clock) {
        this.playerService = playerService;
        this.clock = clock;
    }

    /**
     * 검색 결과 + 마지막 갱신 시각.
     *
     * @param result    검색 결과
     * @param updatedAt 마지막으로 PUBG API 에서 불러온 시각
     * @param cardPending 플레이 카드를 아직 계산 중이면 true (경기 목록은 이미 있음)
     */
    public record Lookup(PlayerMatches result, Instant updatedAt, boolean cardPending) {
        /** 카드 계산이 끝난(또는 필요 없는) 결과 */
        public Lookup(PlayerMatches result, Instant updatedAt) {
            this(result, updatedAt, false);
        }
    }

    /** 저장된 결과가 있으면 그걸, 없으면 새로 불러온다 */
    public Lookup find(Platform platform, String nickname) {
        Lookup saved = store.get(key(platform, nickname));
        return saved != null ? saved : fetch(platform, nickname);
    }

    /** 저장된 결과만 본다. 없어도 PUBG API 를 부르지 않는다 (화면 껍데기를 바로 띄울 때 쓴다) */
    public Optional<Lookup> findSaved(Platform platform, String nickname) {
        return Optional.ofNullable(store.get(key(platform, nickname)));
    }

    /** find 와 같지만 카드 계산이 끝날 때까지 기다린다 (카드 꾸미기 화면용) */
    public Lookup findWithCard(Platform platform, String nickname) {
        Lookup lookup = find(platform, nickname);
        if (!lookup.cardPending()) {
            return lookup;
        }
        CompletableFuture<Lookup> task = cardTasks.get(key(platform, lookup.result().nickname()));
        if (task == null) {
            return find(platform, nickname);   // 방금 끝났다
        }
        try {
            return task.get(CARD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            return find(platform, nickname);
        }
    }

    /** 카드를 아직 계산 중인지 (저장된 결과가 없으면 false) */
    public boolean isCardPending(Platform platform, String nickname) {
        Lookup saved = store.get(key(platform, nickname));
        return saved != null && saved.cardPending();
    }

    /**
     * 새로 불러온다. 단, 마지막 갱신 후 2분이 안 지났으면 저장된 결과를 그대로 돌려준다.
     *
     * @return 실제로 새로 불러왔으면 true
     */
    public boolean refresh(Platform platform, String nickname) {
        Lookup saved = store.get(key(platform, nickname));
        if (saved != null && secondsUntilRefresh(saved) > 0) {
            return false;
        }
        fetch(platform, nickname);
        return true;
    }

    /** 다시 갱신할 수 있을 때까지 남은 초. 0이면 지금 갱신 가능 */
    public long secondsUntilRefresh(Lookup lookup) {
        Instant availableAt = lookup.updatedAt().plus(REFRESH_COOLDOWN);
        long seconds = Duration.between(clock.instant(), availableAt).toSeconds();
        return Math.max(0, seconds);
    }

    /** "방금 전", "3분 전", "2시간 전", "1일 전" */
    public String updatedAgoText(Lookup lookup) {
        Duration ago = Duration.between(lookup.updatedAt(), clock.instant());
        if (ago.toMinutes() < 1) {
            return "방금 전";
        }
        if (ago.toHours() < 1) {
            return ago.toMinutes() + "분 전";
        }
        if (ago.toDays() < 1) {
            return ago.toHours() + "시간 전";
        }
        return ago.toDays() + "일 전";
    }

    /**
     * 1단계(경기 목록·시즌·클랜)만 끝내고 바로 돌려준다. 카드는 뒤에서 따로 만들어 같은 자리에 채운다.
     * 그래서 처음 검색해도 전적 화면이 먼저 뜬다.
     */
    private Lookup fetch(Platform platform, String nickname) {
        PlayerService.Basics basics = playerService.getBasics(platform, nickname);
        PlayerMatches result = basics.result();
        Instant updatedAt = clock.instant();
        Lookup lookup = new Lookup(result, updatedAt, basics.needsCard());
        String typedKey = key(platform, nickname);
        String exactKey = key(platform, result.nickname());
        store.put(typedKey, lookup);
        // PUBG 에 등록된 정확한 닉네임으로도 찾을 수 있게 같이 저장 (대소문자가 다르게 검색될 수 있어서)
        store.put(exactKey, lookup);

        if (basics.needsCard()) {
            CompletableFuture<Lookup> task = CompletableFuture.supplyAsync(() -> {
                PlayCard card = null;
                try {
                    card = playerService.buildCard(basics);
                } catch (RuntimeException e) {
                    log.warn("카드를 만들지 못함: {}", e.getMessage());
                }
                Lookup done = new Lookup(result.withCard(card), updatedAt, false);
                // 그 사이 새로 갱신됐으면(저장된 게 다른 객체면) 덮어쓰지 않는다
                store.replace(typedKey, lookup, done);
                store.replace(exactKey, lookup, done);
                return done;
            }, CARD_EXECUTOR);
            cardTasks.put(exactKey, task);
            task.whenComplete((r, e) -> cardTasks.remove(exactKey, task));
        }
        return lookup;
    }

    private static String key(Platform platform, String nickname) {
        return platform.getShard() + ":" + nickname.toLowerCase(Locale.ROOT);
    }
}
