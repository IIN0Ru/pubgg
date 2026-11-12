# 실행 방법

[← README](../README.md)

## 준비물

- JDK 21
- PUBG API 키 ([developer.pubg.com](https://developer.pubg.com)에서 발급)
- 프론트엔드를 고칠 때만: Node 22

## 서버 실행 (IntelliJ)

1. IntelliJ에서 **File → Open** → 이 `pubgg` 폴더 선택
2. JDK 21 설정 (컴퓨터에 깔린 Java 8은 그대로 둬도 됨)
   - **File → Project Structure → Project → SDK** → **Add SDK → Download JDK**
   - Version **21**, Vendor는 아무거나 (예: Eclipse Temurin) → Download
3. 오른쪽 **Maven** 탭에서 새로고침 버튼을 눌러 라이브러리를 받는다 (처음 한 번만, 몇 분 걸릴 수 있음)
4. PUBG API 키를 환경 변수 `PUBG_API_KEY`로 등록
   - Windows: 시작 메뉴 → "환경 변수" 검색 → 시스템 환경 변수 편집 → 환경 변수 → 사용자 변수 새로 만들기
   - 등록 후 IntelliJ를 완전히 껐다 켜야 반영됨
   - 그래도 "API 키가 설정되지 않았어요"가 뜨면: 실행 버튼 옆 **PubggApplication ▼ → 구성 편집 → 환경 변수**에 `PUBG_API_KEY=키` 입력
     (JetBrains Toolbox로 켠 IntelliJ는 예전 환경 변수를 들고 있을 수 있음. 이 설정은 내 컴퓨터에만 저장되고 GitHub에 올라가지 않음)
5. `src/main/java/gg/pub/PubggApplication.java` 열고 `main` 옆 ▶ 버튼 → **Run**
6. 브라우저에서 http://localhost:8080 접속

터미널로 실행하려면 (키는 같은 환경 변수):

```
mvn spring-boot:run
```

코드를 새로 받았을 때(Git → 풀)는 실행 중인 서버를 멈췄다가 다시 실행해야 반영된다.

로컬 실행에는 DB가 필요 없다. 공유 카드는 `./data/cards` 폴더에 저장된다 (`.gitignore` 대상). DB에 저장하는 운영 설정은 [배포](deployment.md) 참고.

## 프론트엔드(React) 개발

검색 결과 화면(`/search`, `/s/{id}`)만 React이고, 첫 화면·카드 꾸미기(`/card`)·2D 리플레이(`/replay`)는 Thymeleaf다.

```
cd frontend
npm install
npm run dev        # http://localhost:5173/?platform=steam&nickname=닉네임
npm test           # vitest
npm run typecheck  # 타입 검사
npm run build      # 타입 검사 + 빌드 → dist/
```

- `npm run dev`는 Spring을 8080에서 같이 띄워 둔 상태에서 쓴다. 5173은 `/api` 등을 8080으로 넘겨 준다
- 운영에서는 Docker 빌드가 `npm run build` 결과를 Spring의 `static/app/assets/`에 넣고, `/search`·`/s/{id}`의 껍데기(`templates/app.html`)가 이를 불러온다. 같은 주소라서 CORS 설정이 필요 없다

## 테스트

| 대상 | 방법 |
|---|---|
| Java | IntelliJ: `src/test/java` 폴더 오른쪽 클릭 → **모든 테스트 실행**, 또는 `mvn test` |
| 프론트 | `cd frontend && npm test` |

Java 테스트는 PUBG API 키가 필요 없다. `src/test/resources/pubg/`의 예시 응답과 가짜 서비스(Mockito)만 쓴다. GitHub Actions(`.github/workflows/test.yml`)가 커밋·PR마다 Java 테스트와 프론트(타입 검사·테스트·빌드)를 둘 다 돌린다.
