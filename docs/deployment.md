# 배포 (Docker + PostgreSQL)

[← README](../README.md)

로컬 실행은 공유 카드를 `./data/cards` 폴더에 저장한다. 운영은 `prod` 프로필로 켜고, 공유 카드를 DB(PostgreSQL)에 저장한다.
서버를 다시 켜면 폴더가 사라지는 호스팅이 많아서 폴더 저장은 운영에 쓰지 않는다.

## 환경 변수

호스팅의 환경 변수 설정에 넣는다. 코드·GitHub에는 쓰지 않는다.

| 이름 | 값 |
|---|---|
| `PUBG_API_KEY` | PUBG API 키 |
| `DB_URL` | `jdbc:postgresql://호스트:5432/DB이름` |
| `DB_USER` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |

호스팅이 `postgres://사용자:비밀번호@호스트:5432/DB이름` 형태로 주소를 주면, 그대로 `DB_URL`에 넣으면 안 된다.
`jdbc:postgresql://호스트:5432/DB이름` 으로 바꿔 넣고 사용자·비밀번호는 `DB_USER`, `DB_PASSWORD`에 따로 넣는다.

환경 변수가 하나라도 없으면 서버가 켜질 때 "Could not resolve placeholder" 오류로 바로 멈춘다 (빠르게 실패하게 일부러 그렇게 둠).

## 내 컴퓨터에서 이미지 확인 (Docker 필요)

```
docker build -t pubgg .
docker run -p 8080:8080 -e PUBG_API_KEY=키 -e DB_URL=jdbc:postgresql://호스트:5432/DB이름 -e DB_USER=사용자 -e DB_PASSWORD=비밀번호 pubgg
```

## 이미지 구성 (`Dockerfile`)

| 단계 | 하는 일 |
|---|---|
| 0. frontend | Node로 React 빌드. 결과물(`dist/assets`)만 다음 단계로 넘김 |
| 1. build | Maven으로 jar 생성. React 결과물을 `static/app/assets/`에 넣은 뒤 패키징. 테스트는 CI에서 돌리므로 건너뜀 |
| 2. run | JRE + jar만 담은 가벼운 이미지. root가 아닌 `appuser`로 실행, 메모리는 컨테이너의 75%까지만 사용 |

## 운영 설정 (`application-prod.properties`)

- 포트는 호스팅이 주는 `PORT`를 따른다 (없으면 8080)
- 프록시 뒤에서 돌기 때문에 `forward-headers-strategy=framework`로 원래 주소·https 여부를 따른다. 안 하면 링크 미리보기(`og:image`) 주소가 http로 만들어진다
- DB 연결 풀은 5개로 작게 둔다 (무료 DB는 동시 연결 수가 적음)
- 서버가 켜질 때 `share_card` 테이블을 자동으로 만든다 (`src/main/resources/db/schema.sql`). 예전 `player_card` 테이블은 더 이상 쓰지 않는다. 자동으로 지우지 않으니 필요하면 직접 `DROP TABLE player_card`
