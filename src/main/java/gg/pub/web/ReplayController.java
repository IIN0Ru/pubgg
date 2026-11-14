package gg.pub.web;

import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.replay.Replay;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.service.ReplayService;
import gg.pub.service.ReplayUnavailableException;
import gg.pub.web.api.PlayerPageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Optional;

/**
 * 2D 리플레이.
 *  - GET /replay              화면 껍데기 (React 가 그린다)
 *  - GET /api/replay/summary  머리 줄에 쓸 경기 기록 (맵·등수·킬…). 경기 기록만 읽어서 빠르다
 *  - GET /api/replay          리플레이 데이터(JSON)
 *
 * 머리 줄과 데이터를 나눈 이유: 텔레메트리를 처음 읽을 때 수 초가 걸려서,
 * 화면과 머리 줄부터 띄우고 "불러오는 중"을 보여준 뒤 데이터가 오면 그린다.
 */
@Controller
public class ReplayController {

    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    @GetMapping("/replay")
    public String page(@RequestParam(required = false) String platform,
                       @RequestParam(required = false) String match,
                       @RequestParam(required = false) String player,
                       @RequestParam(required = false) String nickname,
                       Model model) {
        if (Platform.fromShard(platform).isEmpty() || !validMatch(match) || !validPlayer(player)) {
            return "redirect:/";
        }
        String name = nickname == null ? "" : nickname.trim();
        String title = name.isEmpty() ? "리플레이" : name + " 리플레이";
        return Shell.fill(model, title + " | 펍지지", title, "배그 2D 리플레이 · 우리 팀 동선과 교전");
    }

    /** 머리 줄: 전적 목록과 같은 경기 모양 (frontend 의 Match 타입) */
    @GetMapping("/api/replay/summary")
    @ResponseBody
    public ResponseEntity<?> summary(@RequestParam String platform,
                                     @RequestParam String match,
                                     @RequestParam String player) {
        Optional<Platform> selected = Platform.fromShard(platform);
        if (selected.isEmpty() || !validMatch(match) || !validPlayer(player)) {
            return ResponseEntity.badRequest().body(Map.of("message", "주소가 올바르지 않아요."));
        }
        try {
            MatchRecord record = replayService.record(selected.get(), match, player);
            return ResponseEntity.ok(PlayerPageResponse.MatchDto.from(record));
        } catch (ReplayUnavailableException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (PubgApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/api/replay")
    @ResponseBody
    public ResponseEntity<?> data(@RequestParam String platform,
                                  @RequestParam String match,
                                  @RequestParam String player) {
        Optional<Platform> selected = Platform.fromShard(platform);
        if (selected.isEmpty() || !validMatch(match) || !validPlayer(player)) {
            return ResponseEntity.badRequest().body(Map.of("message", "주소가 올바르지 않아요."));
        }
        try {
            Replay replay = replayService.replay(selected.get(), match, player);
            return ResponseEntity.ok(replay);
        } catch (ReplayUnavailableException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (PubgApiException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    /** 경기 ID: 영문·숫자·하이픈만 (PUBG 경기 ID 는 UUID 모양) */
    static boolean validMatch(String match) {
        return match != null && match.matches("[A-Za-z0-9-]{8,64}");
    }

    /** 계정 ID: account. 으로 시작 */
    static boolean validPlayer(String player) {
        return player != null && player.matches("account\\.[A-Za-z0-9]{1,64}");
    }
}
