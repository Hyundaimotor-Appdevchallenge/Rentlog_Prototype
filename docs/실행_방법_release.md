# 렌트로그 실행 방법 (release APK)

제출용 서명 APK `apk/팀명_렌트로그_release.apk`를 Pleos Connect 에뮬레이터에 설치해 실행하는 방법입니다.
개발용(debug) 빌드·실행은 [`README.md`](../README.md)의 '실행 방법'을 보세요.

## debug와 다른 점

| 항목 | debug (`app-debug.apk`) | release (`apk/팀명_렌트로그_release.apk`) |
|---|---|---|
| 설치 방법 | Android Studio Run ▶ 또는 `./gradlew assembleDebug` | 빌드 없이 APK 파일만 설치 |
| 서명 | Android 디버그 키 | 팀 키스토어(`CN=RentLog`) |
| 최적화 | 없음 | 없음 (R8 최적화를 켜면 시작 시 크래시하므로 꺼 둠, 아래 참고) |
| debuggable | 예 | 아니요 (`run-as`, 디버거 연결 불가) |
| 앱 기능 | 동일 (가짜 서버, 인증번호 `1234`, 차량 번호 `12가3456`) | 동일 |

- 패키지 이름은 둘 다 `com.example.pleosrantlog`입니다. **서명이 달라서 한 기기에 함께 설치할 수 없습니다.** 바꿔 설치할 때는 먼저 지워야 합니다(앱 데이터도 지워집니다).

## 1. 준비물

- Android SDK **Platform-Tools**(adb)와 **Emulator** (Android Studio SDK Manager에서 설치)
- `Pleos_Connect_v2.1.0` AVD (Pleos 제공 시스템 이미지, RAM 4GB 이상)
- release APK: `apk/팀명_렌트로그_release.apk`
- 자동 테스트·자동 시연을 돌릴 때만 Python 3 (표준 라이브러리만 사용)

APK만 설치해 실행할 때는 Android Studio, Gradle, 키스토어가 **필요 없습니다.**

adb·emulator 기본 위치

| OS | adb | emulator |
|---|---|---|
| Windows | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` | `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe` |
| macOS | `~/Library/Android/sdk/platform-tools/adb` | `~/Library/Android/sdk/emulator/emulator` |

## 2. 에뮬레이터 켜기 (Cold Boot)

1. Android Studio Device Manager에서 `Pleos_Connect_v2.1.0` 오른쪽 ▼ → **Cold Boot Now**
2. 또는 터미널에서:
   ```bash
   emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load -memory 4096
   ```
3. 부팅 완료 확인 (`1`이 나오면 완료):
   ```bash
   adb shell getprop sys.boot_completed
   ```

- 일반 Run으로 켜면 이전 주행거리 등이 남습니다. 시연·테스트 전에는 항상 Cold Boot 하세요.
- 에뮬레이터 창을 닫거나 Android Studio가 에뮬레이터를 종료하면 테스트가 중간에 끊깁니다. 실행 중에는 창을 닫지 마세요.

## 3. release APK 설치·실행

```bash
# debug 앱이 설치되어 있으면 서명이 달라 먼저 지웁니다 (없으면 "Unknown package" 오류가 나도 괜찮습니다)
adb uninstall com.example.pleosrantlog

adb install -r apk/팀명_렌트로그_release.apk
adb shell am start -n com.example.pleosrantlog/.MainActivity
```

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`이 나오면 서명이 다른 앱(대개 debug)이 남아 있는 것입니다. `adb uninstall` 후 다시 설치하세요.
- Windows에서 한글 파일명 때문에 설치가 안 되면 영문 이름으로 복사해서 설치합니다:
  ```bash
  cp apk/팀명_렌트로그_release.apk rentlog_release.apk
  adb install -r rentlog_release.apk
  ```
- 설치 확인:
  ```bash
  adb shell pm list packages | grep pleosrantlog
  ```
- 앱 안의 가짜 서버는 앱을 다시 시작하면 초기화됩니다. **본인 확인 번호는 `1234`**, 대상 차량 번호는 `12가3456`입니다.
- 앱 다시 시작:
  ```bash
  adb shell am force-stop com.example.pleosrantlog
  adb shell am start -n com.example.pleosrantlog/.MainActivity
  ```

## 4. Virtual Console로 차량 신호 조작

```bash
adb forward tcp:8088 tcp:8088
```

- 브라우저로 `http://127.0.0.1:8088/`을 열고 **닫지 마세요.** 페이지가 열려 있어야 콘솔이 동작합니다(자동화 스크립트는 대신 하트비트를 보내므로 브라우저가 필요 없습니다).
- 시연 전 초기값: Odometer 12340 km, Battery SOC 80 %, Gear P, Telltales Off, 문·창문·트렁크·프렁크 닫힘
- 명령줄(curl) 조작은 [`시연_시나리오_release.md`](시연_시나리오_release.md)의 2절을 보세요.
- 주의
  - 주행거리는 **줄일 수 없습니다.** 처음부터 다시 하려면 Cold Boot 하세요.
  - **비행기 모드를 켜면 콘솔이 먹통**이 됩니다. 연결 끊김 시연은 마지막에 하고, 이후 Cold Boot로 복구합니다.
  - 전기차라 화면의 "연료"는 배터리 SOC입니다. 브레이크 경고등이 없어 **ABS를 긴급 경고**로 씁니다.

## 5. release APK로 자동 테스트·자동 시연

`scripts/`의 Python 스크립트는 설치된 앱의 서명과 관계없이 동작합니다(adb + uiautomator로 화면을 읽고 누릅니다). 위 3절대로 release APK를 설치한 뒤 실행하세요.

```bash
adb forward tcp:8088 tcp:8088
python3 scripts/e2e_test.py                 # 통합 테스트 (약 6~7분, 실패 시 종료코드 1)
python3 scripts/e2e_test.py --only lock     # 일부만: lock, blockers, flow, tire
python3 scripts/demo_autoplay.py --shots    # 시연 자동 재생 (약 3~4분)
```

- Windows에서 `python3`가 Microsoft Store로 연결되면 `python`이나 `py -3`을 쓰세요. 한글 출력이 깨지면 `PYTHONIOENCODING=utf-8`을 지정합니다.
- adb를 못 찾으면 `ADB` 환경 변수에 adb 경로를 지정합니다. 예: `ADB="C:/Users/<사용자>/AppData/Local/Android/Sdk/platform-tools/adb.exe"`
- 결과 스크린샷은 `scripts/out/<시각>/`에 남습니다(커밋 금지).
- `./gradlew connectedDebugAndroidTest`(계측 테스트)는 **debug 앱을 설치하고 끝나면 지웁니다.** release로 시연할 거라면 계측 테스트 뒤에 3절을 다시 하세요.

### 검증 결과 (2026-11-08, release APK · Pleos_Connect_v2.1.0 Cold Boot)

| 항목 | 결과 |
|---|---|
| 설치·실행 | 한글 파일명 그대로 `adb install` 성공, 크래시 없이 실행 |
| `e2e_test.py` 전체 | 51단계 통과, 실패 0, SKIP 2 (타이어 kPa: 에뮬레이터 `TIRE_PRESSURE` 미지원, TPMS 경고등으로 대신 검증), 약 6분 |
| `demo_autoplay.py --pause 2 --shots` | 19/19 장면 완주 (F-01 ~ F-11, 명세서, 오답 5회 잠김), 약 4분 |

- 테스트 중 에뮬레이터 창을 조작하거나 닫으면 실패합니다. 실행 중에는 손대지 마세요.

## 6. release APK 새로 만들기

소스를 고쳤다면 APK를 다시 만들어야 합니다. 키스토어와 `keystore.properties`가 필요합니다(만드는 방법은 [`README.md`](../README.md)의 '릴리즈 APK 만들기').

```bash
./gradlew releaseApk      # 서명된 APK가 apk/팀명_렌트로그_release.apk 로 복사됩니다
```

- `keystore.properties`와 키스토어 파일은 **커밋하지 않습니다.**
- **R8 최적화를 다시 켜지 마세요.** `app/build.gradle.kts`의 release `optimization`을 `enable = true` + `packageScope = kotlin.**`로 켜면, Kotlin 표준 라이브러리의 패키지 전용 클래스가 다른 패키지로 옮겨져 앱이 시작하자마자 `IllegalAccessError: Class kotlin.collections.i extended by class st is inaccessible`로 크래시합니다. 켜야 한다면 반드시 이 문서의 5절 E2E 테스트를 release APK로 다시 통과시킨 뒤 제출하세요.
- 새로 만든 뒤에는 앱이 실제로 뜨는지 확인합니다:
  ```bash
  adb install -r apk/팀명_렌트로그_release.apk
  adb shell am start -W -n com.example.pleosrantlog/.MainActivity
  adb shell pidof com.example.pleosrantlog     # 숫자(PID)가 나오면 실행 중
  ```
- 서명 확인: `apksigner verify --print-certs apk/팀명_렌트로그_release.apk` (build-tools 폴더에 있음)
