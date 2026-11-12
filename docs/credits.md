# 자료 출처·디자인 메모

[← README](../README.md)

펍지지는 KRAFTON·PUBG와 관련이 없는 비공식 사이트다. 모든 화면 아래에 같은 안내와 상표 문구를 넣었다 (`templates/fragments/footer.html`).

## 자료 출처

- **데이터**: [PUBG 공식 API](https://developer.pubg.com) (매치·텔레메트리·시즌)
- **리플레이 맵 이미지**: PUBG 공식 API 자료 [pubg/api-assets](https://github.com/pubg/api-assets)의 `Assets/Maps` (High_Res를 512px WebP 조각으로 잘라 `static/images/maps/tiles`에). PUBG API 이용약관에 따라 사용 → [만든 방법](map-tiles.md)
- **플레이어 카드**: 같은 자료의 글자 없는 맵(`Maps/*_No_Text_High_Res` → 2048px WebP), 티어 아이콘(`Icons/Insignias`, 서바이버는 `Ranks [Deprecated]/Survivor`)을 도트로 줄여서 사용. 크리스탈은 공식 자료에 없어서 게임 화면 캡처(크리스탈 IV)에서 바탕을 지우고 숫자만 I~V로 바꿔 만듦
- **플레이어 카드 무기 그림**: 같은 자료의 `Assets/Item/Weapon/Main` 그림(240px, 대각선)을 수평(총구 왼쪽)으로 돌려 여백을 잘라 `static/images/card/weapons/<itemId>.png`로. 이름이 같은 변형(Duncans M416 등)은 기본 총 그림을 같이 씀. JS9는 공식 그림이 없어 카드에 이름만 나옴
- **디자인 기준**: ClickHouse 마케팅 사이트를 분석한 [`DESIGN.md`](../DESIGN.md), 강조색만 노랑 `#FFD02F`로 변경

게임 그림 파일의 권리는 KRAFTON에 있다. 이 저장소에 라이선스 파일을 두지 않은 이유 중 하나다.

## 디자인 메모

- 검정 배경 + 노랑 강조색 하나, 큰 PUB.GG 로고, 가운데 정렬
- 그라데이션·광채 같은 장식은 쓰지 않는다
- 새 색을 추가하지 않고 `main.css` 맨 위 변수만 쓴다 (예외는 [`CLAUDE.md`](../CLAUDE.md)에 적혀 있다: 지도 위 색, 카드 캔버스 안 색, 전적 목록 2·3등 강조색)
