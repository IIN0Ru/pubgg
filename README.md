# 펍지지 (PUB.GG)

배그 닉네임으로 플레이 스타일을 분석하고, 디스코드·커뮤니티에 공유할 수 있는 카드를 만들어 주는 사이트.
게임회사 웹서비스 개발자 지원용 개인 포트폴리오 프로젝트이며 KRAFTON·PUBG와 관련이 없다.

- 기술: Java 21, Spring Boot 3.5, Thymeleaf, Maven
- 데이터: PUBG 공식 API

## 현재 상태

- [x] 첫 화면(히어로): 로고, 스팀/카카오 선택, 닉네임 검색창
- [x] 검색 요청 처리: 빈 닉네임·잘못된 플랫폼이면 첫 화면으로 돌려보냄
- [x] PUBG API 연결: 닉네임 검색 → 최근 14일 전체 경기 목록
- [x] 플레이 카드 기본 정보: 경쟁전 최근 10경기 KDA, 평균 딜량, 평균 투척, 시점, 주무기 2개
- [x] 칭호(해적, 하이에나, 보급러), 게이지(싸움걸기 ↔ 걸리기, 센터 ↔ 외곽)
- [x] 파밍: 대도시파/짤파밍파, 3페이즈 시작 때 힐템·투척 보유량, 주운 총알
- [x] 결과 화면: 왼쪽 공유용 요약 카드(링크 복사) + 오른쪽 상세 패널 4개 + 최근 경기
- [x] 즐겨찾기·최근 검색 (브라우저 저장)
- [x] 검색 결과 저장 + 전적 갱신 버튼 (2분 대기)
- [x] 경기 필터(경쟁/일반, FPP/TPP)·더 보기·펼쳐보기(팀원 기록)
- [x] 자주 같이 한 팀원, 활동 시간대(요일 × 시간)
- [ ] 시즌 티어 + 최근 vs 시즌 비교
- [ ] 카드 이미지 공유(디스코드), 2D 리플레이

## 실행 방법 (IntelliJ)

1. IntelliJ에서 **File → Open** → 이 `pubgg` 폴더 선택
2. JDK 21 설정 (컴퓨터에 깔린 Java 8은 그대로 둬도 됨)
   - **File → Project Structure → Project → SDK** → **Add SDK → Download JDK**
   - Version **21**, Vendor는 아무거나 (예: Eclipse Temurin) → Download
3. 오른쪽 **Maven** 탭에서 새로고침 버튼을 눌러 라이브러리를 받는다 (처음 한 번만, 몇 분 걸릴 수 있음)
4. PUBG API 키를 환경 변수 `PUBG_API_KEY`로 등록 (developer.pubg.com에서 발급)
   - Windows: 시작 메뉴 → "환경 변수" 검색 → 시스템 환경 변수 편집 → 환경 변수 → 사용자 변수 새로 만들기
   - 등록 후 IntelliJ를 완전히 껐다 켜야 반영됨
   - 그래도 "API 키가 설정되지 않았어요"가 뜨면: 실행 버튼 옆 **PubggApplication ▼ → 구성 편집 → 환경 변수**에 `PUBG_API_KEY=키` 입력
     (JetBrains Toolbox로 켠 IntelliJ는 예전 환경 변수를 들고 있을 수 있음. 이 설정은 내 컴퓨터에만 저장되고 GitHub에 올라가지 않음)
5. `src/main/java/gg/pub/PubggApplication.java` 열고 `main` 옆 ▶ 버튼 → **Run**
6. 브라우저에서 http://localhost:8080 접속

테스트는 `src/test/java` 폴더 오른쪽 클릭 → **모든 테스트 실행**.

코드를 새로 받았을 때(Git → 풀)는 실행 중인 서버를 멈췄다가 다시 실행해야 반영된다.

## 폴더 구조

```
src/main/java/gg/pub/
├── PubggApplication.java      앱 시작점
├── web/
│   └── HomeController.java    "/" 첫 화면, "/search" 검색 처리 (Controller)
├── service/
│   ├── PlayerLookupService.java 검색 결과 저장, 전적 갱신 (2분 대기)
│   ├── PlayerService.java     검색 결과 만들기: 14일 경기 동시에 받기 + 경쟁전 10경기로 카드
│   ├── MatchRecordService.java 경기 하나 → 작은 기록(MatchRecord)으로 줄이고 캐시
│   ├── PlayCardService.java   플레이 카드 계산 (기본 정보 + 칭호·게이지)
│   ├── TelemetryService.java  텔레메트리 받기 + 분석 결과 캐시
│   └── TelemetryAnalyzer.java 텔레메트리 분석: 주무기, 투척, 교전, 해적, 하이에나, 보급, 자기장 위치
├── pubgapi/
│   ├── PubgApiClient.java     PUBG API 호출 (Client)
│   ├── PubgApiException.java  API 오류 (화면에 보여줄 문장 포함)
│   ├── PlayerNotFoundException.java
│   └── dto/                   API 응답 JSON 그릇
├── domain/
│   ├── Platform.java          스팀/카카오 (PUBG API shard 값)
│   ├── MatchRecord.java       경기 하나 기록 (나 + 팀원). 목록·카드·팀원·시간대가 같이 씀
│   ├── MatchPlayer.java       경기 하나에서 한 사람 기록
│   ├── Teammate.java          자주 같이 한 팀원 계산
│   ├── ActivityMap.java       활동 시간대(요일 × 시간) 계산
│   ├── PlayerMatches.java     검색 결과 묶음 (카드 + 경기 목록)
│   ├── PlayCard.java          플레이 카드
│   ├── Title.java             칭호 (이름은 여기서 바꿈)
│   ├── WeaponCatalog.java     무기 ID → 이름·분류 (5.56 AR, DMR, SR ...)
│   ├── DropZones.java         랭크 맵 대도시 목록 (지역 ID / 론도는 좌표)
│   ├── Farming.java           파밍 정보
│   ├── WeaponClass.java, WeaponUsage.java, TelemetryStats.java
│   └── GameLabels.java        맵·모드 한글 이름
└── config/
    ├── PubgApiProperties.java API 주소·키·경기 수 설정
    ├── CacheConfig.java       캐시 켜기
    └── ClockConfig.java       현재 시각 (테스트에서 바꿀 수 있게)

src/main/resources/
├── application.properties     설정
├── templates/
│   ├── fragments/topbar.html  모든 페이지 상단 바 (로고 + 닉네임 검색)
│   ├── index.html             첫 화면(히어로)
│   └── search.html            검색 결과: 플레이 카드 + 최근 경기 목록
├── static/css/main.css        스타일 (색은 맨 위 변수에서 관리)
└── static/js/players.js       즐겨찾기·최근 검색 (브라우저 저장)

DESIGN.md                      디자인 기준 파일
CLAUDE.md                      AI 작업용 안내 (DESIGN.md를 불러옴)
NOTES.md                       개발 기록 (무엇을 정했고 AI와 어떻게 만들었는지)
```

## 디자인 메모

- 기준: ClickHouse DESIGN.md (`DESIGN.md`), 강조색만 노랑 #FFD02F로 변경
- 검정 배경 + 노랑 강조색 하나, 큰 PUB.GG 로고, 가운데 정렬
- 그라데이션·광채 같은 장식은 쓰지 않는다
