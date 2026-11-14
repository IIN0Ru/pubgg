# 리플레이 지도 조각

[← README](../README.md)

`src/main/resources/static/images/maps/tiles/`에 WebP 이미지가 **1,364장** 들어 있다. 코드가 아니라 지도 그림이라, 커밋에서 "1,379 files changed"처럼 크게 보이는 것이다.

- **왜**: PUBG 공식 고해상도 지도는 8192px, 장당 약 100MB라서 통째로 쓸 수 없다. 저해상도(819px)는 리플레이에서 확대하면 흐려서 못 쓴다
- **어떻게**: 지도 앱(구글 지도 등)처럼 512px 조각으로 잘라 5단계로 만들고, 화면 크기·확대에 맞는 단계의 **보이는 조각만** 불러온다. 아직 안 온 자리는 이미 받은 덜 자세한 단계로 채운다 (`frontend/src/lib/replay/engine.ts`의 `drawMap`)

| 단계 | 지도 한 변 | 조각 수 |
|---|---|---|
| 0 | 512px | 1 |
| 1 | 1024px | 4 |
| 2 | 2048px | 16 |
| 3 | 4096px | 64 |
| 4 | 8192px | 256 |

- 맵 4개(에란겔·미라마·태이고·론도) × 341장 = 1,364장, 합계 약 42MB. 한 화면에서 받는 양은 수백 KB
- 주소: `/images/maps/tiles/{지도}/{단계}/{x}_{y}.webp`
- 만든 방법: [pubg/api-assets](https://github.com/pubg/api-assets)의 `Assets/Maps/*_Main_High_Res.png`를 Python(Pillow)으로 단계별로 줄여 512px씩 잘라 WebP(품질 72)로 저장
