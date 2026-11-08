# 렌트로그 시연 영상 시나리오 (release APK)

제출용 release APK(`apk/팀명_렌트로그_release.apk`)로 시연 영상을 찍는 순서입니다. Pleos Connect 에뮬레이터와 Virtual Console을 씁니다.
설치 방법의 자세한 내용은 [`실행_방법_release.md`](실행_방법_release.md)를 보세요.

데모 패널(제목 길게 누르기)은 **사용하지 않습니다.** 차량 신호는 Virtual Console로, 나머지는 화면 터치로 진행합니다.

## 0. 촬영 전 준비 (매 테이크마다)

1. `Pleos_Connect_v2.1.0` AVD를 **Cold Boot**로 켭니다 (Device Manager → ▼ → Cold Boot Now, RAM 4GB 이상).
   - 주행거리는 **줄일 수 없습니다**(`traveled_distance_must_not_decrease`). 테이크를 다시 찍으려면 반드시 Cold Boot로 0 km로 되돌립니다.
   - 비행기 모드를 켠 뒤에는 콘솔이 먹통이 됩니다. Cold Boot로 복구합니다.
   - 촬영 중에는 에뮬레이터 창을 닫지 마세요. Android Studio가 에뮬레이터를 붙잡고 있다면 Android Studio를 먼저 끄는 편이 안전합니다.
2. release APK를 설치합니다. debug 앱이 있으면 서명이 달라 먼저 지웁니다.
   ```bash
   adb uninstall com.example.pleosrantlog
   adb install -r apk/팀명_렌트로그_release.apk
   ```
   - 한 번 설치해 두면 Cold Boot를 해도 앱은 남아 있습니다. 다시 설치할 필요는 없습니다.
3. 콘솔 포트를 연결합니다: `adb forward tcp:8088 tcp:8088`
4. 브라우저로 `http://127.0.0.1:8088/`을 열어 두고 **닫지 않습니다** (열려 있어야 콘솔이 동작합니다).
5. 콘솔 초기값을 맞춥니다 (브라우저 카드 또는 2절 curl).
   - Odometer = **12340** km, Battery SOC = **80** %, Gear = **P**, Telltales(TPMS, ABS) = **Off**
   - Body 패널에서 FRUNK/TRUNK, 문, 창문이 모두 Closed인지 확인합니다(열려 있으면 출발 전 검수에서 출발이 막힙니다).
   - 주행거리는 한 번 올리면 내릴 수 없으니 **12340을 정확히 한 번만** 입력합니다.
6. 앱을 새로 시작합니다. 가짜 서버(FakeBackend)가 초기화되고 F-01 화면이 나옵니다. 인증번호는 `1234`입니다.
   ```bash
   adb shell am force-stop com.example.pleosrantlog
   adb shell am start -n com.example.pleosrantlog/.MainActivity
   ```

## 1. 장면별 진행

| # | 장면 | 조작 | 화면 결과 |
|---|---|---|---|
| 1 | F-01 대여 시작·본인 확인 | 키패드로 틀린 번호 4자리 → 정답 `1234` | "번호가 달라요 (남은 횟수 4회)" → 정답이면 F-02 |
| 2 | F-02 출발 전 검수 | (콘솔 값이 자동 반영) | 주행거리 12,340 km, 연료(=배터리) 80 %, 경고등 0개, 문 모두 닫힘 → **확인, 출발하기** |
| 2-1 | (선택) 트렁크 열림 시연 | F-02 화면에서 콘솔 **TRUNK = Open** | '문 · 창문 / 열림 있음 · 트렁크 열림 / 주의', 하단 "트렁크가 열려 있어요…", **확인, 출발하기 비활성** → 콘솔 **TRUNK = Close** → 1~2초 뒤 '모두 닫힘 / 정상', 출발 가능 |
| 3 | F-03 주행 중 | 콘솔 **Gear = D** | "기록 중 · 이상 없음", 반납까지 약 2시간 |
| 4 | F-04 주의 경고 | 콘솔 Telltales **TPMS = On** | "주의 · 운행 가능" + 음성 안내 |
| 5 | F-05 정차 후 대응 | 콘솔 **Gear = P** | 기록된 내용 타임라인, 선택지 3개 → **계속 운행** |
| 6 | 복귀 | 콘솔 TPMS = Off, Gear = D | F-03으로 복귀 |
| 7 | F-10 긴급 경고 | 콘솔 Telltales **ABS = On** | 긴급 화면 + 음성. 연락 상태가 **6초 후 "확인 완료", 12초 후 "출동 접수됨"** 으로 바뀜 → **확인했어요 · 운행 화면으로** |
| 8 | F-06 반납 전 안내 | 콘솔 ABS = Off, Odometer **12612**, Battery SOC **62** | 출발 **약 1분 뒤** 운행 화면에서 자동 표시(반납까지 2시간). 272 km → 67,760원 |
| 9 | 내비 호출 | **반납 장소로 안내** | 내비 앱이 열리고 경로 표시 → 하단 앱 아이콘으로 렌트로그로 복귀 |
| 10 | F-07 반납 검수 | 콘솔 **Gear = P** (또는 "반납 장소에 도착했어요") | 인수/반납 비교표 + 정산 미리보기 → **반납 확정 · 명세서 받기** |
| 11 | F-11 반납 완료 | — | 272 km, 67,760원, 기록 검증 코드 → **명세서 미리 보기** → 닫기 → 닫기 → F-01 |
| 12 | F-08 확인 잠김 | 앱 다시 시작(0절 6번) 후 틀린 번호 5회 | "본인 확인이 잠겼어요" |
| 13 | F-08 연결 끊김 (마지막에!) | F-02까지 진행 → 에뮬레이터 **비행기 모드 ON** → **확인, 출발하기** | 5초 후 "연결이 끊겼어요" → 비행기 모드 OFF → **다시 시도** → 중단된 동작이 이어서 실행됨 |

주의
- F-06은 **운행 화면(F-03)에 있을 때만** 뜹니다. F-10/F-05 화면에서는 뜨지 않으니 운행 화면으로 돌아온 뒤 기다리세요.
- 12번은 앱을 다시 시작해야 남은 횟수가 5회로 초기화됩니다. 1번에서 틀린 횟수가 이어지지 않게 하세요.
- 13번(연결 끊김)은 영상의 **맨 마지막**에 찍습니다. 이후에는 Cold Boot가 필요합니다.

## 2. 콘솔을 명령줄로 조작하기

브라우저 카드와 같은 동작입니다. 브라우저 페이지를 열어 둔 상태에서 실행하세요.

```bash
C=http://127.0.0.1:8088/vehicle
curl -X PUT "$C/odometer?traveled_distance_km=12340"
curl -X PUT "$C/ev-battery?field=soc&value=80"
curl -X PUT "$C/gear?gear=Park"            # Drive
curl -X PUT "$C/telltales/simulator?color=Red&state=On&type=TPMS"   # state=Off, type=ABS
curl -X PUT "$C/body?door_fl=true"          # 문 열림 (false=닫힘). door_fr / door_rl / door_rr
curl -X PUT "$C/body?trunk=true"            # 트렁크 열림 (false=닫힘)
curl -X PUT "$C/body?frunk=true"            # 프렁크 열림 (false=닫힘)
curl -X PUT "$C/body?window_fl=50"          # 창문 열림 0~100 % (0=닫힘). window_fr / window_rl / window_rr
# 모두 닫기 (촬영 전 준비)
curl -X PUT "$C/body?trunk=false&frunk=false&door_fl=false&door_fr=false&door_rl=false&door_rr=false&window_fl=0&window_fr=0&window_rl=0&window_rr=0"
```

비행기 모드: `adb shell cmd connectivity airplane-mode enable` / `disable`

## 3. 자동 시연 (release APK)

`scripts/demo_autoplay.py`는 release APK에서도 그대로 동작합니다. 장면마다 한 줄씩 출력하고 `--pause` 초만큼 멈추므로 화면 녹화와 함께 쓸 수 있습니다.

```bash
adb forward tcp:8088 tcp:8088
python3 scripts/demo_autoplay.py --pause 4 --shots
```

- 브라우저 없이 동작합니다(스크립트가 콘솔 하트비트를 대신 보냅니다).
- 스크립트는 **현재 주행거리**를 인수 기준으로 삼아 +272 km만 올립니다. Cold Boot 직후에 돌리면 화면에 0 km → 272 km로 나옵니다. 12,340 km로 찍고 싶으면 스크립트 실행 **전에** 콘솔 Odometer를 12340으로 맞추세요(요금 67,760원은 같습니다).
- `--skip-lock`은 마지막 잠김 장면 생략, `--no-problem-scene`은 F-02 문제 연출 생략입니다.
- 13번(비행기 모드 연결 끊김)은 스크립트에 없습니다. 직접 찍으세요.
- 끝나면 콘솔을 중립 상태(기어 P, 경고등 Off, 문 닫힘, SOC 80)로 되돌립니다. 주행거리는 그대로 남습니다.

## 4. 알려진 한계

- 콘솔에는 연료/속도 조절이 없습니다(전기차). 화면의 "연료"는 배터리 SOC 값입니다.
- 브레이크 전용 telltale이 없어 **ABS**를 긴급(브레이크) 경고로 씁니다.
- 에뮬레이터가 `TIRE_PRESSURE` 속성을 앱에 주지 않아 타이어 kPa는 화면에 반영되지 않습니다. 타이어 시연은 TPMS 경고등으로 합니다.
- 앱에 `PRIVATE_CAR_SAFETY` 권한이 있습니다(경고등 읽기에 필요). 스토어 제출 시 Pleos App Market 사전 승인이 필요합니다.
- 업체 전화번호(02-1234-5678), 정비소/반납 장소 이름은 임시값입니다.
- release APK는 debuggable이 아니어서 `adb shell run-as`로 앱 데이터를 볼 수 없습니다.
