# 펍지지 (PUB.GG)

배그 닉네임으로 전적과 플레이 스타일을 분석하고, 디스코드·커뮤니티에 공유할 수 있는 카드를 만들어 주는 사이트.

- 지금: 친구들과 서로의 전적을 검색하고, 플레이 스타일 카드로 "이 사람이랑 듀오할까"를 판단. 우리 판(경쟁전)을 2D 리플레이로 다시 보기
- 다음: 리플레이 위에 그려서 전략을 공유하기
- 목표: 배그 프로 구단이 이 사이트로 전략·전술을 짜는 것

AI를 활용해 만든 개인 프로젝트이며, KRAFTON·PUBG와 관련이 없는 비공식 사이트다.

- 기술: Java 21, Spring Boot 3.5, Thymeleaf, Maven
- 데이터: PUBG 공식 API

## 현재 상태

- [x] 첫 화면(히어로): 로고, 스팀/카카오 선택, 닉네임 검색창
- [x] 검색 요청 처리: 빈 닉네임·잘못된 플랫폼이면 첫 화면으로 돌려보냄
- [x] PUBG API 연결: 닉네임 검색 → 최근 14일 전체 경기 목록
- [x] 플레이 카드 기본 정보: 경쟁전 최근 10경기 KDA, 평균 딜량, 평균 투척, 시점, 주무기 2개
- [x] 칭호(해적, 하이에나, 보급러), 자기장 운영(줄어들기 시작할 때 위치, 인게임 미니맵형) + 자기장 운(새 원이 뜬 직후 위치)
- [x] 파밍: 대도시파/짤파밍파, 3페이즈 시작 때 힐템·투척 보유량, 주운 총알
- [x] 결과 화면: 왼쪽 공유용 요약 카드(링크 복사) + 오른쪽 상세 패널 3개(자기장 / 주무기 · 파밍) + 최근 경기
- [x] 즐겨찾기·최근 검색 (브라우저 저장)
- [x] 검색 결과 저장 + 전적 갱신 버튼 (2분 대기)
- [x] 경기 필터(경쟁/일반, FPP/TPP)·더 보기·펼쳐보기(팀원 기록)
- [x] 자주 같이 한 팀원, 활동 시간대(요일 × 시간)
- [x] 이번 시즌 경쟁전 티어·RP + 최근 10경기 vs 시즌 비교
- [x] 다크/라이트 모드, 검색 중 표시, 링크 미리보기 문구(og), 폰 배치·디자인 수치 정리 (UI/UX 리뷰 반영)
- [x] 2D 리플레이 (경쟁전): 재생·배속·타임라인, 경기 흐름 누르면 이동, 근처 적만, 동선 전체 + 교전 번호, 확대·이동·따라가기
  - 고해상도 지도 조각(타일)을 보이는 만큼만 불러오기, 명중선, 차량·보급 아이콘, 폭발물(터진 곳 추정)·회복 이펙트
- [ ] 카드 이미지 공유(디스코드), 리플레이 그리기·공유

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
│   ├── HomeController.java    "/" 첫 화면, "/search" 검색 처리 (Controller)
│   └── ReplayController.java  "/replay" 리플레이 화면, "/api/replay" 리플레이 데이터(JSON)
├── service/
│   ├── PlayerLookupService.java 검색 결과 저장, 전적 갱신 (2분 대기)
│   ├── PlayerService.java     검색 결과 만들기: 14일 경기 동시에 받기 + 경쟁전 10경기로 카드
│   ├── ReplayService.java     2D 리플레이 데이터 (경쟁전만, 캐시)
│   ├── ReplayBuilder.java     텔레메트리 → 리플레이 데이터 (위치·자기장·사건·교전·폭발물·회복)
│   ├── SeasonService.java     이번 시즌 티어·기록 (시즌 ID 6시간 저장, 실패하면 티어 없이)
│   ├── MatchRecordService.java 경기 하나 → 작은 기록(MatchRecord)으로 줄이고 캐시
│   ├── PlayCardService.java   플레이 카드 계산 (기본 정보 + 칭호·게이지)
│   ├── TelemetryService.java  텔레메트리 받기 + 분석 결과 캐시
│   └── TelemetryAnalyzer.java 텔레메트리 분석: 주무기, 투척, 교전, 해적, 하이에나, 보급, 자기장 운영·운
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
│   ├── SeasonRank.java        시즌 티어 + 최근 vs 시즌 비교
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
├── static/images/maps/tiles/  리플레이 지도 조각 (아래 "리플레이 지도 조각" 참고)
└── static/js/players.js       즐겨찾기·최근 검색 (브라우저 저장)

DESIGN.md                      디자인 기준 파일
CLAUDE.md                      AI 작업용 안내 (DESIGN.md를 불러옴)
NOTES.md                       개발 기록 (무엇을 정했고 AI와 어떻게 만들었는지)
```

## 리플레이 지도 조각

`static/images/maps/tiles/` 에 WebP 이미지가 **1,364장** 들어 있다. 코드가 아니라 지도 그림이라 커밋에서 "1,379 files changed"처럼 크게 보이는 것.

- 왜: PUBG 공식 고해상도 지도는 8192px, 장당 약 100MB 라서 통째로 쓸 수 없다. 저해상도(819px)는 리플레이에서 확대하면 흐려서 못 쓴다
- 어떻게: 지도 앱(구글 지도 등)처럼 512px 조각으로 잘라 5단계로 만들고, 화면 크기·확대에 맞는 단계의 **보이는 조각만** 불러온다. 아직 안 온 자리는 이미 받은 덜 자세한 단계로 채운다 (`static/js/replay.js` 의 `drawMap`)

  | 단계 | 지도 한 변 | 조각 수 |
  |---|---|---|
  | 0 | 512px | 1 |
  | 1 | 1024px | 4 |
  | 2 | 2048px | 16 |
  | 3 | 4096px | 64 |
  | 4 | 8192px | 256 |

- 맵 4개(에란겔·미라마·태이고·론도) × 341장 = 1,364장, 합계 약 42MB. 한 화면에서 받는 양은 수백 KB
- 주소: `/images/maps/tiles/{지도}/{단계}/{x}_{y}.webp`
- 만든 방법: [pubg/api-assets](https://github.com/pubg/api-assets) 의 `Assets/Maps/*_Main_High_Res.png` 를 Python(Pillow)으로 단계별로 줄여 512px 씩 잘라 WebP(품질 72)로 저장

## 개발 방식 (AI 활용)

- 기획·기능 범위·칭호와 판정 기준·디자인 방향과 최종 결정: 본인
- 구현: Claude
- 검토: GPT가 검토 요청서(체크리스트)를 쓰고, Claude가 화면을 직접 띄워 본 리뷰와 합쳐 우선순위(P0/P1/P2)로 정리 → 본인 승인 후 반영
- 기능마다 무엇을 정했고, AI에게 무엇을 시켰고, 어디서 방향을 바꿨는지는 [`NOTES.md`](NOTES.md)에 남긴다
- API로 판단할 수 없는 항목(집·숲 운영, 소통 기록 등)은 기획 단계에서 걸러냈다

## 디자인 메모

- 기준: ClickHouse DESIGN.md (`DESIGN.md`), 강조색만 노랑 #FFD02F로 변경
- 검정 배경 + 노랑 강조색 하나, 큰 PUB.GG 로고, 가운데 정렬
- 그라데이션·광채 같은 장식은 쓰지 않는다


## 자료 출처
- 리플레이 맵 이미지: PUBG 공식 API 자료 [pubg/api-assets](https://github.com/pubg/api-assets) (Assets/Maps, High_Res 를 512px WebP 조각으로 잘라 static/images/maps/tiles 에). PUBG API 이용약관에 따라 사용
