#!/usr/bin/env python3
"""RentLog 통합(E2E) 테스트: 실제 앱 + Vehicle SDK + Pleos Virtual Console (adb/uiautomator).

사용: python3 scripts/e2e_test.py [--only lock,blockers,flow,tire] [--out DIR]
전제: 에뮬레이터 콜드 부팅, adb forward tcp:8088 tcp:8088, 앱 설치. (브라우저 불필요: 하트비트 내장)
주행거리는 줄일 수 없으므로 '현재 값'을 인수 기준으로 사용하고 +272 km 로 반납한다.
"""
import argparse
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rentlog_driver import Driver, DriverError  # noqa: E402

TIRE_OK = [True]   # go_to_f02 에서 갱신
DRIVE_KM = 272
FEE = "67,760원"


class Suite:
    def __init__(self, drv):
        self.d = drv
        self.results = []   # (scenario, step, ok, detail)
        self.scenario = ""

    def step(self, name, fn):
        """fn 실행. 예외/AssertionError 는 FAIL. 성공 시 True."""
        t0 = time.time()
        ok, detail = True, ""
        try:
            fn()
        except (DriverError, AssertionError) as e:
            ok, detail = False, str(e)
        except Exception as e:  # noqa: BLE001
            ok, detail = False, f"{type(e).__name__}: {e}"
        shot = self.d.screenshot(f"{self.scenario}_{name}")
        self.results.append((self.scenario, name, ok, detail))
        print(f"  [{'PASS' if ok else 'FAIL'}] {name} ({time.time() - t0:.1f}s)" + (f"\n         -> {detail}" if not ok else ""), flush=True)
        return ok

    def skip(self, name, reason):
        self.results.append((self.scenario, name, None, reason))
        print(f"  [SKIP] {name}\n         -> {reason}", flush=True)

    def begin(self, name):
        self.scenario = name
        print(f"\n=== 시나리오: {name} ===", flush=True)

    def summary(self):
        print("\n================ 요약 ================")
        scen = {}
        for s, n, ok, det in self.results:
            scen.setdefault(s, []).append((n, ok, det))
        failed = 0
        skipped = 0
        for s, items in scen.items():
            bad = [i for i in items if i[1] is False]
            sk = [i for i in items if i[1] is None]
            failed += len(bad)
            skipped += len(sk)
            print(f"{'PASS' if not bad else 'FAIL'}  {s}  ({len(items) - len(bad) - len(sk)}/{len(items) - len(sk)} 통과, SKIP {len(sk)})")
            for n, _, det in bad:
                print(f"      x {n}: {det}")
            for n, _, det in sk:
                print(f"      - SKIP {n}: {det}")
        print(f"총 {len(self.results)}단계, 실패 {failed}, SKIP {skipped}")
        print(f"스크린샷: {self.d.out_dir}")
        return failed


# ---------------------------------------------------------------- helpers
def wait_all(d, texts, timeout=12, exact=True):
    """모든 텍스트가 (정확히 일치로) 동시에 화면에 있을 때까지 대기."""
    end = time.time() + timeout
    while True:
        nodes = d.ui_dump()
        missing = [t for t in texts if not d.has_text(t, exact, nodes)]
        if not missing:
            return nodes
        if time.time() > end:
            raise DriverError(f"없는 텍스트 {missing} ({timeout}s). 현재 화면: {d._screen_blob(nodes)}")
        time.sleep(0.5)


def wait_sub(d, subs, timeout=12):
    """각 부분문자열이 어떤 텍스트에든 포함될 때까지 대기."""
    end = time.time() + timeout
    while True:
        nodes = d.ui_dump()
        texts = d.screen_text(nodes)
        missing = [s for s in subs if not any(s in t for t in texts)]
        if not missing:
            return nodes
        if time.time() > end:
            raise DriverError(f"포함되지 않은 텍스트 {missing} ({timeout}s). 현재 화면: {d._screen_blob(nodes)}")
        time.sleep(0.5)


def tire_sdk_supported(d):
    """에뮬레이터 CarService 가 TIRE_PRESSURE 속성을 노출하는지 (앱이 PleosVehicle 태그로 1회 로그)."""
    time.sleep(1.5)
    log = d.adb("logcat", "-d", "-s", "PleosVehicle", timeout=30)
    return "not supported: TIRE_PRESSURE" not in log


def depart_enabled(d, nodes=None):
    return d.is_enabled("확인, 출발하기", nodes=nodes)


def go_to_f02(d, code="1234"):
    d.adb("logcat", "-c")
    d.restart_app()
    d.type_code(code)
    d.wait_text("확인, 출발하기", timeout=15)
    TIRE_OK[0] = tire_sdk_supported(d)


def normal_f02(d, nodes_timeout=12):
    """콘솔의 현재 값과 F-02 정상 표시가 일치하는지."""
    odo, soc = d.odometer(), d.soc()
    kpa = d.state()["tire_pressure"]["pressure_kpa"]
    vals = sorted({int(v) for v in kpa.values()})
    note = f"4륜 {vals[0]} kPa" if len(vals) == 1 else f"{vals[0]}–{vals[-1]} kPa"
    if not TIRE_OK[0]:
        note = "공기압 경고등 꺼짐"   # SDK 로 kPa 를 읽지 못하는 환경
    nodes = wait_all(d, ["모두 닫힘", "경고 없음", "켜진 경고등 없음", "이상 없음", f"{odo:,} km", f"{soc} %", note],
                     timeout=nodes_timeout)
    texts = d.screen_text(nodes)
    assert any(t.startswith("전조등 ") for t in texts) or "전조등 · 미등 · 방향지시등" in texts, \
        f"등화 안내 문구 없음. 화면: {d._screen_blob(nodes)}"
    assert depart_enabled(d, nodes), f"출발 버튼이 비활성. 화면: {d._screen_blob(nodes)}"
    return nodes


# ---------------------------------------------------------------- (a) lock
def scenario_lock(S):
    d = S.d
    S.begin("a_lock")
    d.reset_console()
    S.step("앱 재시작 → F-01", d.restart_app)
    S.step("오답 1회 → 남은 횟수 4회", lambda: (d.type_code("1111"), d.wait_text("번호가 달라요 (남은 횟수 4회)", timeout=8)))

    def lock5():
        for i, c in enumerate(["2222", "3333", "4444", "5555"]):
            d.type_code(c)
            if i < 3:
                d.wait_text(f"번호가 달라요 (남은 횟수 {3 - i}회)", timeout=8)
        d.wait_text("본인 확인이 잠겼어요", timeout=10)

    S.step("오답 누적 5회 → 본인 확인이 잠겼어요", lock5)
    S.step("잠김 화면 버튼 확인", lambda: (d.wait_text("업체에 전화 요청", timeout=5), d.wait_text("다시 시도", timeout=5)))


# ---------------------------------------------------------------- (b) blockers
def scenario_blockers(S):
    d = S.d
    S.begin("b_blockers")
    d.reset_console()
    if not S.step("F-02 진입(정답 1234)", lambda: go_to_f02(d)):
        return
    S.step("정상 상태 표시 = 콘솔 값(주행거리/SOC/타이어/등화)", lambda: normal_f02(d))

    notice_tail = ". 해결되면 자동으로 다시 확인해요."
    cases = [
        ("트렁크 열림", lambda: d.put("body", trunk="true"), lambda: d.put("body", trunk="false"),
         ["열림 있음", "트렁크 열림"], "트렁크 열림" + notice_tail),
        ("프렁크 열림", lambda: d.put("body", frunk="true"), lambda: d.put("body", frunk="false"),
         ["열림 있음", "프렁크 열림"], "프렁크 열림" + notice_tail),
        ("창문 열림(50)", lambda: d.put("body", window_fl=50), lambda: d.put("body", window_fl=0),
         ["열림 있음", "앞 왼쪽 창문 열림"], "앞 왼쪽 창문 열림" + notice_tail),
        ("앞 왼쪽 문 열림", lambda: d.put("body", door_fl="true"), lambda: d.put("body", door_fl="false"),
         ["열림 있음", "앞 왼쪽 문 열림"], "앞 왼쪽 문 열림" + notice_tail),
        ("타이어 150 kPa(앞 왼쪽)", lambda: d.set_tire("FL", 150), lambda: d.set_tire("FL", 230),
         ["공기압 낮음", "앞 왼쪽 150 kPa"], "앞 왼쪽 타이어 공기압 낮음 (150 kPa)" + notice_tail),
        ("TPMS 경고등 On", lambda: d.set_telltale("TPMS", True), lambda: d.set_telltale("TPMS", False),
         ["공기압 낮음", "공기압 경고등 켜짐"], "타이어 공기압 경고등" + notice_tail),
        ("ABS 경고등", lambda: d.set_telltale("ABS", True), lambda: d.set_telltale("ABS", False),
         ["1개", "ABS"], "ABS 경고등" + notice_tail),
        ("에어백 경고등", lambda: d.set_telltale("Airbag", True), lambda: d.set_telltale("Airbag", False),
         ["1개", "에어백"], "에어백 경고등" + notice_tail),
        ("LED 헤드램프 램프 이상", lambda: d.set_telltale("LEDHeadlamp", True, "Yellow"),
         lambda: d.set_telltale("LEDHeadlamp", False, "Yellow"),
         ["이상 있음", "LED 헤드램프"], "LED 헤드램프 경고" + notice_tail),
        ("AFS 램프 이상", lambda: d.set_telltale("AFS", True, "Yellow"), lambda: d.set_telltale("AFS", False, "Yellow"),
         ["이상 있음", "AFS"], "AFS 경고" + notice_tail),
        ("배터리 SOC 15 %", lambda: d.set_soc(15), lambda: d.set_soc(80),
         ["15 %", "충전이 필요해요"], "배터리 15%" + notice_tail),
    ]
    for name, apply_, fix, subs, notice in cases:
        if name.startswith("타이어") and not TIRE_OK[0]:
            S.skip(f"{name}", "에뮬레이터 CarService 가 TIRE_PRESSURE 를 지원하지 않아 앱이 kPa 를 읽을 수 없음 "
                              "(logcat PleosVehicle: 'property ID is not in carPropertyConfig list ... TIRE_PRESSURE'). "
                              "TPMS 경고등 경로는 별도 케이스로 검증")
            continue
        def blocked(subs=subs, notice=notice, apply_=apply_):
            nodes = d.ui_dump()
            if not d.has_text("확인, 출발하기", True, nodes):
                raise DriverError(f"F-02 화면이 아님: {d._screen_blob(nodes)}")
            apply_()
            nodes = wait_sub(d, subs + [notice], timeout=12) if notice else wait_sub(d, subs)
            assert not depart_enabled(d, nodes), f"'확인, 출발하기' 가 활성 상태. 화면: {d._screen_blob(nodes)}"
        S.step(f"{name}: 문제 표시 + 출발 비활성", blocked)

        def fixed(fix=fix):
            fix()
            normal_f02(d, nodes_timeout=15)
        ok = S.step(f"{name}: 해결 → 정상 + 출발 활성", fixed)
        if not ok:   # 다음 케이스를 위해 콘솔 상태 정리
            d.reset_console()
            time.sleep(2)

    # 복합: 트렁크 + ABS + 에어백
    def multi():
        d.put("body", trunk="true")
        d.set_telltale("ABS", True)
        d.set_telltale("Airbag", True)
        nodes = wait_sub(d, ["트렁크 열림 외 2건" + notice_tail, "2개", "ABS · 에어백", "열림 있음"], timeout=12)
        assert not depart_enabled(d, nodes), "복합 차단인데 출발 버튼이 활성"
    S.step("복합(트렁크+ABS+에어백): '외 2건' 표시 + 출발 비활성", multi)

    def multi_fix():
        d.put("body", trunk="false")
        d.set_telltale("ABS", False)
        d.set_telltale("Airbag", False)
        normal_f02(d, nodes_timeout=15)
    S.step("복합 해결 → 정상", multi_fix)

    # 차단 중 버튼 눌러도 출발 안 되는지(비활성이면 탭 거부) - 실제 탭 시도
    def tap_blocked():
        d.put("body", trunk="true")
        wait_sub(d, ["트렁크 열림"], timeout=10)
        n = d.find("확인, 출발하기")
        x, y = n.center()
        d.tap_xy(x, y)
        time.sleep(1.5)
        nodes = d.ui_dump()
        assert d.has_text("확인, 출발하기", True, nodes) and not d.has_text("기록 중 · 이상 없음", True, nodes), \
            f"차단 중인데 화면이 전환됨: {d._screen_blob(nodes)}"
        d.put("body", trunk="false")
        normal_f02(d, nodes_timeout=15)
    S.step("차단 중 버튼을 눌러도 F-03 으로 넘어가지 않음", tap_blocked)


# ---------------------------------------------------------------- (c) flow
def scenario_flow(S):
    d = S.d
    S.begin("c_flow")
    d.set_telltale("ABS", False)
    base = d.reset_console()
    print(f"  인수 기준 주행거리 = {base:,} km (현재 콘솔 값)", flush=True)
    if not S.step("F-01 → 정답 1234 → F-02", lambda: go_to_f02(d)):
        return
    S.step("F-02 정상 표시", lambda: normal_f02(d))
    t = {}

    def depart():
        t["pickup_odo"] = d.odometer()
        d.tap_text("확인, 출발하기")
        d.wait_text("확인, 출발하기", absent=True, timeout=10)
        t["depart"] = time.time()
    S.step("확인, 출발하기 → 주행 화면", depart)
    if "depart" not in t:
        return

    def gear_d():
        d.set_gear("D")
        wait_sub(d, ["기록 중 · 이상 없음"], timeout=12)
    S.step("F-03 기록 중 · 이상 없음 (gear D)", gear_d)

    def tpms():
        d.set_telltale("TPMS", True)
        wait_sub(d, ["타이어 공기압 경고등이 켜졌어요.", "주의 · 운행 가능"], timeout=15)
    S.step("F-04 TPMS On → 주의 경고", tpms)

    def park():
        d.set_gear("P")
        wait_sub(d, ["타이어 공기압 경고 (TPMS)", "업체 지정 정비소로 안내", "업체에 전화 요청", "계속 운행"], timeout=15)
    S.step("F-05 gear P → 정차 후 대응(선택지 3개)", park)

    def cont():
        d.tap_text("계속 운행")
        time.sleep(1.0)
        d.set_telltale("TPMS", False)
        d.set_gear("D")
        wait_sub(d, ["기록 중 · 이상 없음"], timeout=15)
    S.step("계속 운행 → F-03 복귀(TPMS Off, D)", cont)

    def abs_on():
        d.set_telltale("ABS", True)
        wait_sub(d, ["브레이크 경고등이 켜졌어요. 운행을 멈춰 주세요"], timeout=15)
    S.step("F-10 ABS On → 긴급 경고", abs_on)

    S.step("F-10 상태 배지 '확인 완료' (~6s)", lambda: wait_sub(d, ["확인 완료"], timeout=15))
    S.step("F-10 상태 배지 '출동 접수됨' (~12s)", lambda: wait_sub(d, ["출동 접수됨"], timeout=20))

    def back():
        d.tap_text("확인했어요 · 운행 화면으로")
        wait_sub(d, ["기록 중 · 이상 없음"], timeout=15)
        d.set_telltale("ABS", False)
    S.step("확인했어요 · 운행 화면으로 → F-03", back)

    def km_soc():
        d.set_odometer(t["pickup_odo"] + DRIVE_KM)
        d.set_soc(62)
        # F-06 은 출발 약 60 s 뒤 운행 화면에서만 뜸
        wait_sub(d, ["반납까지 2시간 남았어요"], timeout=120)
        nodes = wait_sub(d, [FEE], timeout=15)
        t["f06"] = time.time() - t["depart"]
        print(f"         (출발 후 {t['f06']:.0f}s 에 F-06 표시)", flush=True)
    S.step(f"F-06 반납 전 안내 (+{DRIVE_KM} km, SOC 62 → {FEE})", km_soc)

    def navi():
        d.tap_text("반납 장소로 안내")
        end = time.time() + 15
        while time.time() < end:
            if d.foreground_pkg() == "ai.umos.maps.android.navigation.app":
                break
            time.sleep(0.7)
        else:
            raise DriverError(f"내비 앱이 전면에 오지 않음. foreground={d.foreground_pkg()} 앱 화면: {d._screen_blob()}")
        time.sleep(2.0)
    S.step("반납 장소로 안내 → 내비 앱 실행", navi)
    d.screenshot("c_flow_navi_extra")

    def back_app():
        d.bring_app_front()
        d.dismiss_system_dialogs()
        assert d.foreground_pkg() == "com.example.pleosrantlog", f"렌트 앱 전면 아님: {d.foreground_pkg()}"
    S.step("렌트 앱으로 복귀", back_app)

    def f07():
        d.set_gear("P")
        wait_sub(d, ["인수 때와 비교했어요", "정산 미리보기", FEE, "반납 확정 · 명세서 받기"], timeout=20)
        texts = d.screen_text()
        assert any("272" in x for x in texts), f"비교표에 272 km 없음: {d._screen_blob()}"
    S.step("F-07 gear P → 인수 때와 비교했어요(+정산 67,760원)", f07)

    def f11():
        d.tap_text("반납 확정 · 명세서 받기")
        nodes = wait_sub(d, ["반납이 완료됐어요", "272 km", FEE], timeout=20)
        blob = " ".join(d.screen_text(nodes))
        assert re.search(r"[0-9A-Za-z]{4}·[0-9A-Za-z]{4}", blob) or re.search(r"[0-9A-Za-z]{4}\s*[·-]\s*[0-9A-Za-z]{4}", blob), \
            f"기록 검증 코드(xxxx·xxxx) 없음: {d._screen_blob(nodes)}"
    S.step("반납 확정 → F-11 반납이 완료됐어요 (272 km, 67,760원, 검증 코드)", f11)

    S.step("명세서 미리 보기 → 반납 정산 명세서",
           lambda: (d.tap_text("명세서 미리 보기"), wait_sub(d, ["반납 정산 명세서"], timeout=15)))
    S.step("닫기 → F-11", lambda: (d.tap_text("닫기"), wait_sub(d, ["반납이 완료됐어요"], timeout=10)))
    S.step("닫기 → F-01 본인 확인", lambda: (d.tap_text("닫기"), wait_sub(d, ["본인 확인 · 휴대폰 번호 뒤 4자리"], timeout=15)))
    d.reset_console()


# ---------------------------------------------------------------- (d) tire drop while driving
def scenario_tire(S):
    d = S.d
    S.begin("d_tire_drop")
    d.reset_console()
    if not S.step("F-02 진입 + 출발", lambda: (go_to_f02(d), d.tap_text("확인, 출발하기"), d.set_gear("D"),
                                           wait_sub(d, ["기록 중 · 이상 없음"], timeout=15))):
        return

    if not TIRE_OK[0]:
        S.skip("주행 중 앞 왼쪽 150 kPa → F-04", "TIRE_PRESSURE 미지원 에뮬레이터(위와 동일). 대신 TPMS 경고등 경로(flow 시나리오)로 F-04 검증")
        d.reset_console()
        return

    def drop():
        d.set_tire("FL", 150)
        wait_sub(d, ["타이어 공기압 경고등이 켜졌어요.", "주의 · 운행 가능"], timeout=15)
    S.step("주행 중 앞 왼쪽 150 kPa → F-04", drop)

    def restore():
        d.set_tire("FL", 230)
        time.sleep(2)
        nodes = d.ui_dump()
        print(f"         (복구 후 화면: {d._screen_blob(nodes)[:200]})", flush=True)
        # F-04 자동 복귀 여부는 앱 정책에 맡기고, 닫을 수 있으면 닫아서 F-03 확인
        for label in ("확인했어요 · 운행 화면으로", "확인했어요", "닫기", "계속 운행"):
            if d.has_text(label, True, nodes):
                d.tap_text(label)
                break
        wait_sub(d, ["기록 중 · 이상 없음"], timeout=15)
    S.step("공기압 복구(230) → 운행 화면 복귀 · 이상 없음", restore)
    d.reset_console()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="lock,blockers,flow,tire")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    drv = Driver(out_root=a.out)
    S = Suite(drv)
    t0 = time.time()
    try:
        drv.start_heartbeat()
        drv.dismiss_system_dialogs()
        fns = {"lock": scenario_lock, "blockers": scenario_blockers, "flow": scenario_flow, "tire": scenario_tire}
        for name in a.only.split(","):
            try:
                fns[name.strip()](S)
            except DriverError as e:
                S.results.append((S.scenario, "(시나리오 중단)", False, str(e)))
                print(f"  [FAIL] 시나리오 중단: {e}")
    finally:
        try:
            drv.neutral()
        finally:
            drv.stop_heartbeat()
    failed = S.summary()
    print(f"소요 {time.time() - t0:.0f}s")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
