package gg.pub.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
    public String search() {
        return "app";
    }
}
