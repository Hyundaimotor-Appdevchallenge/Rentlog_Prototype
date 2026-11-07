#!/usr/bin/env python3
"""렌트로그 시연 영상용 자동 재생 (사람 개입 없음).

사용: python3 scripts/demo_autoplay.py [--pause 4] [--skip-lock] [--no-problem-scene]
전제: 에뮬레이터 콜드 부팅 후 `adb forward tcp:8088 tcp:8088`. 브라우저 불필요(하트비트 내장).
주행거리는 줄일 수 없으므로 '현재 값'에서 +272 km 진행한다(요금 67,760원 동일).
"""
import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rentlog_driver import Driver, DriverError, NAVI_PKG, APP_PKG  # noqa: E402

DRIVE_KM = 272


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pause", type=float, default=4.0, help="장면마다 정지(초)")
    ap.add_argument("--skip-lock", action="store_true", help="마지막 오답 5회 잠김 장면 생략")
    ap.add_argument("--no-problem-scene", action="store_true", help="F-02 문제 상태 연출 생략")
    ap.add_argument("--shots", action="store_true", help="장면마다 스크린샷 저장(scripts/out/<시각>/)")
    a = ap.parse_args()

    d = Driver(verbose=False)
    ctx = {"pickup": 0}

    def hold(extra=0.0):
        time.sleep(a.pause + extra)

    def wait_sub(subs, timeout=15):
        end = time.time() + timeout
        while True:
            nodes = d.ui_dump()
            texts = d.screen_text(nodes)
            miss = [s for s in subs if not any(s in t for t in texts)]
            if not miss:
                return
            if time.time() > end:
                raise DriverError(f"{miss} 없음 ({timeout}s). 화면: {d._screen_blob(nodes)}")
            time.sleep(0.5)

    # ---------------------------------------------------------------- scenes
    def s_prepare():
        ctx["pickup"] = d.reset_console()
        d.restart_app()
        print(f"      (주행거리 기준 {ctx['pickup']:,} km)")

    def s_wrong1():
        d.type_code("1111", settle=1.0)
        wait_sub(["번호가 달라요 (남은 횟수 4회)"], 8)

    def s_login():
        d.type_code("1234", settle=0.5)
        d.wait_text("확인, 출발하기", timeout=15)

    def s_problem():
        d.put("body", trunk="true")
        d.set_tire("FL", 150)
        d.set_telltale("TPMS", True)   # 에뮬레이터는 kPa 를 앱에 전달하지 않으므로 TPMS 경고등을 함께 켠다
        wait_sub(["열림 있음", "공기압 낮음"], 12)
        assert not d.is_enabled("확인, 출발하기"), "출발 버튼이 비활성이 아님"

    def s_fix():
        d.put("body", trunk="false")
        d.set_tire("FL", 230)
        d.set_telltale("TPMS", False)
        wait_sub(["모두 닫힘", "경고 없음"], 15)
        end = time.time() + 15
        while not d.is_enabled("확인, 출발하기"):
            if time.time() > end:
                raise DriverError("해결 후에도 출발 버튼 비활성")
            time.sleep(0.5)

    def s_normal():
        pass

    def s_depart():
        ctx["pickup"] = d.odometer()
        d.tap_text("확인, 출발하기")
        ctx["t_depart"] = time.time()
        d.wait_text("확인, 출발하기", absent=True, timeout=10)
        d.set_gear("D")
        wait_sub(["기록 중 · 이상 없음"], 15)

    def s_tpms():
        d.set_telltale("TPMS", True)
        wait_sub(["타이어 공기압 경고등이 켜졌어요."], 15)

    def s_park():
        d.set_gear("P")
        wait_sub(["타이어 공기압 경고 (TPMS)", "계속 운행"], 15)

    def s_continue():
        d.tap_text("계속 운행")
        time.sleep(1.0)
        d.set_telltale("TPMS", False)
        d.set_gear("D")
        wait_sub(["기록 중 · 이상 없음"], 15)

    def s_abs():
        d.set_telltale("ABS", True)
        wait_sub(["브레이크 경고등이 켜졌어요. 운행을 멈춰 주세요"], 15)
        wait_sub(["확인 완료"], 15)
        wait_sub(["출동 접수됨"], 20)

    def s_ack():
        d.tap_text("확인했어요 · 운행 화면으로")
        wait_sub(["기록 중 · 이상 없음"], 15)
        d.set_telltale("ABS", False)

    def s_f06():
        d.set_odometer(ctx["pickup"] + DRIVE_KM)
        d.set_soc(62)
        wait_sub(["반납까지 2시간 남았어요"], 150)
        wait_sub(["67,760원"], 15)

    def s_navi():
        d.tap_text("반납 장소로 안내")
        end = time.time() + 15
        while d.foreground_pkg() != NAVI_PKG:
            if time.time() > end:
                raise DriverError(f"내비 앱이 전면에 오지 않음 (foreground={d.foreground_pkg()!r})")
            time.sleep(0.7)

    def s_back():
        d.bring_app_front()
        d.dismiss_system_dialogs()
        assert d.foreground_pkg() == APP_PKG, f"렌트 앱 전면 아님: {d.foreground_pkg()!r}"

    def s_f07():
        d.set_gear("P")
        wait_sub(["인수 때와 비교했어요", "정산 미리보기", "67,760원"], 20)

    def s_f11():
        d.tap_text("반납 확정 · 명세서 받기")
        wait_sub(["반납이 완료됐어요", "272 km", "67,760원"], 20)

    def s_statement():
        d.tap_text("명세서 미리 보기")
        wait_sub(["반납 정산 명세서"], 15)

    def s_close():
        d.tap_text("닫기")
        wait_sub(["반납이 완료됐어요"], 10)
        time.sleep(1.5)
        d.tap_text("닫기")
        wait_sub(["본인 확인 · 휴대폰 번호 뒤 4자리"], 15)

    def s_lock():
        for i, c in enumerate(["1111", "2222", "3333", "4444", "5555"]):
            d.type_code(c, settle=1.0 if i < 4 else 1.5)
        d.wait_text("본인 확인이 잠겼어요", timeout=10)

    scenes = [("준비: 콘솔 초기화 + 앱 재시작", s_prepare, 0),
              ("F-01 본인 확인 · 오답 1회", s_wrong1, 0),
              ("F-01 정답 1234 → F-02 출발 전 검수", s_login, 0)]
    if not a.no_problem_scene:
        scenes += [("F-02 문제 연출: 트렁크 열림 + 타이어 이상 → 출발 막힘", s_problem, 0),
                   ("F-02 콘솔에서 해결 → 정상 표시", s_fix, 0)]
    else:
        scenes += [("F-02 정상 표시", s_normal, 0)]
    scenes += [("확인, 출발하기 → F-03 주행 중 (기어 D)", s_depart, 0),
               ("F-04 TPMS 주의 경고", s_tpms, 0),
               ("F-05 정차(P) 후 대응 선택", s_park, 0),
               ("계속 운행 → 다시 주행(D)", s_continue, 0),
               ("F-10 ABS 긴급 경고 (확인 완료 → 출동 접수됨)", s_abs, 0),
               ("확인했어요 → 운행 화면 복귀", s_ack, 0),
               (f"주행거리 +{DRIVE_KM} km / 배터리 62 % → F-06 반납 전 안내", s_f06, 0),
               ("반납 장소로 안내 → 내비게이션 앱", s_navi, 1.5),
               ("렌트로그 앱으로 복귀", s_back, 0),
               ("F-07 도착(P) → 인수/반납 비교", s_f07, 0),
               ("반납 확정 → F-11 반납 완료", s_f11, 0),
               ("명세서 미리 보기", s_statement, 0),
               ("닫기 ×2 → F-01 처음 화면", s_close, 0)]
    if not a.skip_lock:
        scenes += [("F-01 오답 5회 → 본인 확인 잠김", s_lock, 0)]

    total = len(scenes)
    t0 = time.time()
    failed = None
    done = 0
    try:
        d.start_heartbeat()
        d.dismiss_system_dialogs()
        for i, (title, fn, extra) in enumerate(scenes, 1):
            print(f"[장면 {i}/{total}] {title}", flush=True)
            try:
                fn()
            except (DriverError, AssertionError) as e:
                failed = (i, title, str(e))
                d.screenshot(f"FAIL_scene{i}")
                break
            done += 1
            if a.shots:
                d.screenshot(f"scene{i:02d}")
            hold(extra)
    finally:
        try:
            d.neutral()
        finally:
            d.stop_heartbeat()
    dur = time.time() - t0
    print("\n---------------- 요약 ----------------")
    print(f"완료 장면 {done}/{total}, 소요 {dur:.0f}s, pause={a.pause}s")
    if failed:
        print(f"실패: 장면 {failed[0]} '{failed[1]}': {failed[2]}")
        sys.exit(1)
    print("완주. 콘솔은 중립 상태로 복원했습니다(주행거리는 줄일 수 없어 유지).")


if __name__ == "__main__":
    main()
