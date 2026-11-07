# CLAUDE.md

Pleos Connect용 렌터카 기록 앱(렌트로그) 프로토타입입니다. 작업 전에 읽을 것:

- `README.md`: 실행 방법, SDK 버전, 에뮬레이터·Virtual Console 사용법
- `docs/인수인계.md`: 구조와 설계 결정, 남은 일
- `docs/개발_가이드.md`: 커밋 컨벤션, PR 체크리스트
- `docs/AI_코딩_가이드.md`: AI 도구 사용 규칙 (필독)

핵심 규칙
- 검증: `./gradlew assembleDebug testDebugUnitTest`, UI·차량 변경 시 `python3 scripts/e2e_test.py --only <시나리오>` (에뮬레이터 `Pleos_Connect_v2.1.0` Cold Boot, `adb forward tcp:8088 tcp:8088`)
- Pleos SDK API는 추측하지 말고 `data/vehicle/PleosVehicleDataSource.kt`와 SDK 문서를 확인. SDK 호출은 예외로 보호하고 시뮬레이터 폴백 유지
- Conventional Commits, 논리 단위 커밋. 푸시·PR은 사용자 확인 후
- 공개 저장소: 비밀값·개인 경로·`local.properties`·`scripts/out` 커밋 금지
