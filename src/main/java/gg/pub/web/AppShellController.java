package gg.pub.web;

import gg.pub.domain.Platform;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * React 화면을 담는 빈 껍데기 페이지. (M1: 검색 결과 화면을 React + TypeScript 로 옮기는 중)
 *
 * 지금은 기존 /search 를 그대로 두고 /next/search 에서 새 화면을 만든다.
 * 새 화면이 기존 화면을 따라잡으면 /search 가 이 껍데기를 쓰도록 바꾼다.
 * 링크 미리보기(og 태그)는 JS 를 실행하지 않는 크롤러가 읽으므로, 나중에 이 템플릿의 <head> 를 서버가 채운다.
 */
@Controller
public class AppShellController {

    @GetMapping("/next/search")
    public String search(@RequestParam(required = false) String platform,
                         @RequestParam(required = false) String nickname,
                         Model model) {
        // 상단 검색창(topbar)이 쓰는 값. React 가 그리는 본문과 별개로 서버가 그린다
        model.addAttribute("platforms", Platform.values());
        model.addAttribute("platform", Platform.fromShard(platform).orElse(null));
        model.addAttribute("nickname", nickname == null ? "" : nickname.trim());
        return "app";
    }
}
