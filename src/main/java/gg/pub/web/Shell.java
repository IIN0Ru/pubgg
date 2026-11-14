package gg.pub.web;

import gg.pub.domain.Platform;
import org.springframework.ui.Model;

/**
 * 모든 화면이 쓰는 React 껍데기(templates/app.html)에 넣을 값.
 * 화면은 React(frontend/)가 그리고, 서버는 탭 제목과 링크 미리보기용 <head> 만 채운다.
 * (디스코드·카톡 미리보기는 JS 를 실행하지 않고 <head> 만 읽는다)
 */
final class Shell {

    static final String VIEW = "app";

    static final String SITE_TITLE = "펍지지 PUB.GG";
    static final String HOME_DESCRIPTION = "스팀·카카오 닉네임 하나로 내 배그 플레이 스타일 확인";
    static final String HOME_OG_DESCRIPTION = "자기장 운영 · 파밍 · 주무기로 보는 배그 플레이 스타일 카드";
    static final String DEFAULT_DESCRIPTION = "배그 플레이 스타일 분석";

    private Shell() {
    }

    /**
     * @param title         탭 제목
     * @param ogTitle       미리보기 제목
     * @param description   검색 결과 설명 + 미리보기 설명
     */
    static String fill(Model model, String title, String ogTitle, String description) {
        return fill(model, title, ogTitle, description, description);
    }

    static String fill(Model model, String title, String ogTitle, String description, String ogDescription) {
        model.addAttribute("pageTitle", title);
        model.addAttribute("ogTitle", ogTitle);
        model.addAttribute("pageDescription", description);
        model.addAttribute("ogDescription", ogDescription);
        return VIEW;
    }

    /**
     * React 가 읽는 검색 대상(<div id="root" data-platform data-nickname>).
     * 공유 주소 /s/{id} 는 주소에 닉네임이 없어서 이 값으로 알아낸다.
     */
    static void target(Model model, Platform platform, String nickname) {
        model.addAttribute("platform", platform);
        model.addAttribute("nickname", nickname);
    }
}
