# 구조

[← README](../README.md)

## 한눈에 보기

```
브라우저 ──GET /search?platform=steam&nickname=…──▶ Spring: 화면 껍데기(app.html)만 바로 응답 (링크 미리보기용 <head> 포함)
   │                                                   │
   └─ React(frontend/) 가 껍데기 안에서 실행 ──GET /api/players/steam/닉네임──▶ Spring: JSON (저장된 결과 / PUBG API)
                                                       └─ 오류는 { code, message, retryAfterSec? } + HTTP 상태
```

- 검색 결과 화면(카드·경기 목록·상세 패널·시즌 비교·팀원)은 React가 그리고, 첫 화면·카드 꾸미기(`/card`)·2D 리플레이(`/replay`)는 Thymeleaf가 그린다
- 서버 데이터(전적)는 React Query, 브라우저에만 있는 설정(필터·즐겨찾기·최근 검색)은 Zustand로 나눠 관리한다
- 오류는 서버가 알려준 `code`로 화면을 나눈다: 없는 플레이어(`PLAYER_NOT_FOUND`) / 요청 한도(`RATE_LIMITED`, 남은 초 표시 후 다시 시도) / 서버·연결 오류(`UPSTREAM_ERROR`, 바로 다시 시도)
- 링크 미리보기(디스코드·카톡)는 JS를 실행하지 않고 `<head>`만 읽는다. 그래서 `/s/{id}`의 제목·설명·`og:image`는 React가 아니라 서버(`app.html`)가 채운다
- 카드는 뒤에서 계산한다. 계산 중(`cardStatus: pending`)이면 프론트가 같은 GET을 3초마다 다시 부른다
- 서버는 원시값(숫자, ISO 시각)만 주고, "3분 전"·"2,450 RP" 같은 표시 글자는 프론트가 만든다. 도메인 record를 그대로 내보내지 않고 `PlayerPageResponse`를 따로 둬서, 내부 모양이 바뀌어도 화면 계약이 안 바뀌게 했다 (프론트 타입은 `frontend/src/api/types.ts`)

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/players/{platform}/{nickname}` | 검색 결과 (카드·경기 목록·시즌·팀원). 오류는 400 `BAD_REQUEST` / 404 `PLAYER_NOT_FOUND` / 429 `RATE_LIMITED` / 502 `UPSTREAM_ERROR` |
| POST | `/api/players/{platform}/{nickname}/refresh` | 전적 갱신 (2분 대기). 응답 `{ refreshed, player }` |
| GET | `/api/replay` | 2D 리플레이 데이터 (경쟁전만) |
| POST | `/api/share` | 공유 링크 만들기 (같은 접속 주소에서 10분에 10번까지) |
| GET | `/s/{id}`, `/s/{id}/image` | 공유 전적 화면(og 태그 포함), 미리보기 그림 |

## 폴더 구조

```
src/main/java/gg/pub/
├── PubggApplication.java      앱 시작점
├── web/
│   ├── HomeController.java    "/" 첫 화면, "/search"·"/s/{id}" React 화면 껍데기 + 링크 미리보기 <head>
│   ├── ReplayController.java  "/replay" 리플레이 화면, "/api/replay" 리플레이 데이터(JSON)
│   ├── CardController.java    "/card" 공유 카드(B카드) 꾸미기, "/api/share" 공유 링크 만들기, "/s/{id}/image" 미리보기 그림
│   └── api/                   React가 부르는 JSON API: PlayerApiController, PlayerPageResponse(응답 모양), ApiError
├── service/
│   ├── PlayerLookupService    검색 결과 저장(findSaved 는 PUBG 를 부르지 않고 저장본만 봄), 전적 갱신 (2분 대기)
│   ├── PlayerService          검색 결과 만들기: 최근 30경기 동시에 받기 + 경쟁전 10경기로 카드
│   ├── ReplayService          2D 리플레이 데이터 (경쟁전만, 캐시)
│   ├── ReplayBuilder          텔레메트리 → 리플레이 데이터 (위치·자기장·사건·교전·폭발물·회복)
│   ├── SeasonService          이번 시즌 티어·기록 (시즌 ID 6시간 저장, 실패하면 티어 없이)
│   ├── MatchRecordService     경기 하나 → 작은 기록(MatchRecord)으로 줄이고 캐시
│   ├── PlayCardService        플레이 카드 계산 (기본 정보 + 게이지)
│   ├── TelemetryService       텔레메트리 받기 + 분석 결과 캐시 (운영은 DB 에 저장)
│   └── TelemetryAnalyzer      텔레메트리 분석: 주무기, 투척, 자기장 운영·운, 착지, 파밍(가방)
├── pubgapi/
│   ├── PubgApiClient          PUBG API 호출
│   ├── PubgApiException, PlayerNotFoundException, PubgRateLimitException   API 오류 (화면에 보여줄 문장 포함)
│   └── dto/                   API 응답 JSON 그릇
├── domain/                    Platform, MatchRecord, MatchPlayer, Teammate, SeasonRank, PlayerMatches,
│                              PlayCard, ShareCard, WeaponCatalog, DropZones, Farming, GameLabels … (업무 값과 계산)
├── card/                      공유 카드(B카드) 저장. CardStore 인터페이스 → 로컬은 FileCardStore, 운영(prod)은 JdbcCardStore
└── config/                    PubgApiProperties(API 주소·키·경기 수), CacheConfig, ClockConfig(테스트에서 시각 바꾸기)

src/main/resources/
├── application.properties         설정 (로컬 기본)
├── application-prod.properties    운영 설정. prod 프로필에서만 적용
├── db/schema.sql                  운영 DB 테이블 정의 (share_card)
├── templates/                     index(첫 화면), app(React 껍데기 + 링크 미리보기 <head>), card(공유 카드 꾸미기), replay, fragments/(상단 바·푸터·테마)
├── static/css/main.css            스타일 (색은 맨 위 변수에서 관리)
├── static/js/                     replay.js(2D 리플레이), card-render.js(카드 그리기, B카드·A카드 공용), player-card.js(카드 꾸미기·공유·PNG 저장),
│                                  players.js(즐겨찾기·최근 검색), theme.js(다크/라이트), loading.js(검색 중 표시), card.js(링크 복사)
├── static/images/card/            카드 그림 (무기 42종, 티어 아이콘·테두리, 글자 없는 맵)
└── static/images/maps/tiles/      리플레이 지도 조각 ([지도 조각](map-tiles.md) 참고)

frontend/                          React + TypeScript 검색 결과 화면 (Vite)
├── src/api/                       서버 호출(client.ts), 응답 타입(types.ts), React Query 훅(queries.ts)
├── src/components/                PlayerScreen(화면 연결), ErrorScreen(오류 3종), IdCard, PlayerCanvas(A카드), DetailsPanel·ZonePanel(상세),
│                                  SideBoxes(시즌 비교·팀원), MatchList·MatchItem, FavoriteButton
├── src/store/                     화면 설정 상태 (Zustand): 경기 필터·더 보기(matchFilters), 즐겨찾기·최근 검색(players)
└── src/lib/                       format.ts(표시 글자), location.ts(주소·껍데기에서 검색 대상 읽기), matchSummary.ts(필터 요약), cardRenderer.ts(card-render.js 연결)
```

- 카드 캔버스는 React로 다시 만들지 않고 기존 `card-render.js`를 브라우저가 직접 불러와 쓴다. `/card`(B카드)와 같은 그리기 코드를 쓰기 때문에 두 화면의 그림이 어긋나지 않는다

## 문서·기준 파일

| 파일 | 내용 |
|---|---|
| [`DESIGN.md`](../DESIGN.md) | 디자인 기준 (색·글꼴·간격). `main.css` 맨 위 변수만 쓴다 |
| [`CLAUDE.md`](../CLAUDE.md) | AI 작업용 안내 (`DESIGN.md`를 불러옴) |
| [`NOTES.md`](../NOTES.md) | 개발 기록 (무엇을 정했고 AI와 어떻게 만들었는지) |
