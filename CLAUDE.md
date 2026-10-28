# 펍지지 (PUB.GG)

Spring Boot 3.5 + Thymeleaf, Java 21. 실행 방법과 구조는 README.md 참고.

## 디자인
색·글꼴·간격은 아래 디자인 파일을 따른다. 맨 위 주석의 "펍지지에서 바꾼 점"이 원본보다 우선한다.
새 색을 추가하지 말고 main.css 맨 위 변수만 쓴다.
예외: 2D 리플레이 지도 위 적 팀 색(`static/js/replay.js` 의 TEAM_PALETTE 5색)과 지도 위 고정색(MAP_COLORS, 회복 이펙트 초록 #22C55E 포함). 범례 아이콘은 이 색을 그대로 따라 쓴다. 지도 이미지가 늘 어두워서 다크/라이트와 상관없이 고정이다. 예외 2: 플레이어 카드(/card) 캔버스 안 색. 맵 사진 위에 그리는 카드라 테마와 상관없이 고정이다 — 티어 테두리 색(`static/images/card/frames/palette.json`, 테두리 그림과 같은 값), 반팔티 색(`CardController.TEE_COLORS`), 카드 안 글자·상자 색(`static/js/player-card.js`). 강조 노랑은 사이트와 같은 #FFD02F.
이 밖에서는 새 색을 쓰지 않는다.

@DESIGN.md
