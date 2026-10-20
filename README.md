# 펍지지 (PUB.GG)

배그 닉네임으로 플레이 스타일을 분석하고, 디스코드·커뮤니티에 공유할 수 있는 카드를 만들어 주는 사이트.
게임회사 웹서비스 개발자 지원용 개인 포트폴리오 프로젝트이며 KRAFTON·PUBG와 관련이 없다.

- 기술: Java 21, Spring Boot 3.5, Thymeleaf, Maven
- 데이터: PUBG 공식 API (예정)

## 현재 상태

- [x] 첫 화면(히어로): 로고, 스팀/카카오 선택, 닉네임 검색창
- [x] 검색 요청 처리: 빈 닉네임·잘못된 플랫폼이면 첫 화면으로 돌려보냄
- [ ] PUBG API 연결
- [ ] 플레이 카드 (칭호, 게이지, 기본 정보)
- [ ] 2D 리플레이

## 실행 방법 (IntelliJ)

1. IntelliJ에서 **File → Open** → 이 `pubgg` 폴더 선택
2. JDK 21 설정 (컴퓨터에 깔린 Java 8은 그대로 둬도 됨)
   - **File → Project Structure → Project → SDK** → **Add SDK → Download JDK**
   - Version **21**, Vendor는 아무거나 (예: Eclipse Temurin) → Download
3. 오른쪽 **Maven** 탭에서 새로고침 버튼을 눌러 라이브러리를 받는다 (처음 한 번만, 몇 분 걸릴 수 있음)
4. `src/main/java/gg/pub/PubggApplication.java` 열고 `main` 옆 ▶ 버튼 → **Run**
5. 브라우저에서 http://localhost:8080 접속

테스트는 `src/test/java/gg/pub/web/HomeControllerTest.java`에서 ▶ 버튼으로 실행한다.

## 폴더 구조

```
src/main/java/gg/pub/
├── PubggApplication.java      앱 시작점
└── web/
    ├── HomeController.java    "/" 첫 화면, "/search" 검색 처리
    └── Platform.java          스팀/카카오 (PUBG API shard 값)

src/main/resources/
├── application.properties     설정
├── templates/
│   ├── index.html             첫 화면(히어로)
│   └── search.html            검색 후 화면 (지금은 준비 중 안내)
└── static/css/main.css        스타일 (색은 맨 위 변수에서 관리)

DESIGN.md                      디자인 기준 파일
CLAUDE.md                      AI 작업용 안내 (DESIGN.md를 불러옴)
NOTES.md                       개발 기록 (무엇을 정했고 AI와 어떻게 만들었는지)
```

## 디자인 메모

- 기준: ClickHouse DESIGN.md (`DESIGN.md`), 강조색만 노랑 #FFD02F로 변경
- 검정 배경 + 노랑 강조색 하나, 큰 PUB.GG 로고, 가운데 정렬
- 그라데이션·광채 같은 장식은 쓰지 않는다
