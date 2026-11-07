"""RentLog 에뮬레이터 자동화 드라이버 (Python 3 표준 라이브러리만 사용).

- Virtual Console(HTTP 8088) 하트비트/쓰기/상태 읽기
- adb + uiautomator 로 앱 화면 읽기/탭/대기/스크린샷
"""
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

def _find_adb():
    """$ADB -> $ANDROID_HOME/$ANDROID_SDK_ROOT -> PATH -> 기본 SDK 위치 순서로 adb 를 찾는다."""
    if os.environ.get("ADB"):
        return os.environ["ADB"]
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(var)
        if root and os.path.exists(os.path.join(root, "platform-tools", "adb")):
            return os.path.join(root, "platform-tools", "adb")
    found = shutil.which("adb")
    if found:
        return found
    for root in ("~/Library/Android/sdk", "~/Android/Sdk", "~/AppData/Local/Android/Sdk"):
        cand = os.path.join(os.path.expanduser(root), "platform-tools", "adb")
        if os.path.exists(cand):
            return cand
    return "adb"


ADB = _find_adb()
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")
CONSOLE = os.environ.get("CONSOLE", "http://127.0.0.1:8088")
APP_PKG = "com.example.pleosrantlog"
NAVI_PKG = "ai.umos.maps.android.navigation.app"
APP_ACTIVITY = f"{APP_PKG}/.MainActivity"
SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
APP_X = (853, 2535)   # 앱 창 가로 범위(px)
APP_Y = (90, 1300)

TIRE_AREAS = {"FL": 1114113, "FR": 1114191, "RL": 1179649, "RR": 1179727}
TELLTALES = ["TPMS", "ABS", "ESC", "Airbag", "MasterWarning", "MDPS", "EVSystem", "Engine",
             "EngineOilPressure", "LowVoltBattery", "LEDHeadlamp", "AFS"]


class DriverError(Exception):
    pass


class Node:
    __slots__ = ("text", "bounds", "enabled", "clickable", "pkg", "anc_clickable", "anc_enabled", "anc_bounds")

    def __init__(self, text, bounds, enabled, clickable, pkg):
        self.text, self.bounds, self.enabled, self.clickable, self.pkg = text, bounds, enabled, clickable, pkg
        self.anc_clickable = clickable
        self.anc_enabled = enabled
        self.anc_bounds = bounds

    def center(self, use_ancestor=True):
        b = self.anc_bounds if use_ancestor else self.bounds
        return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def _parse_bounds(s):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", s or "")
    return tuple(int(x) for x in m.groups()) if m else (0, 0, 0, 0)


class Driver:
    def __init__(self, out_root=None, verbose=True):
        self.verbose = verbose
        self._hb_stop = threading.Event()
        self._hb_thread = None
        self.hb_errors = 0
        stamp = time.strftime("%Y%m%d-%H%M%S")
        self.out_dir = out_root or os.path.join(SCRIPTS_DIR, "out", stamp)
        self._shot_n = 0
        self._last_nodes = []

    # ------------------------------------------------------------ logging
    def log(self, msg):
        if self.verbose:
            print(msg, flush=True)

    # ------------------------------------------------------------ console
    def _http(self, method, path, timeout=5):
        req = urllib.request.Request(CONSOLE + path, method=method, data=b"" if method in ("POST", "PUT") else None)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.status, r.read().decode("utf8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf8", "replace")
        except Exception as e:  # noqa: BLE001
            return 0, f"{type(e).__name__}: {e}"

    def start_heartbeat(self, client="rentlog-driver"):
        if self._hb_thread and self._hb_thread.is_alive():
            return
        self._hb_stop.clear()

        def run():
            while not self._hb_stop.is_set():
                code, _ = self._http("POST", f"/browser/session?client={client}", timeout=3)
                if code != 200:
                    self.hb_errors += 1
                self._hb_stop.wait(1.0)

        self._hb_thread = threading.Thread(target=run, name="console-heartbeat", daemon=True)
        self._hb_thread.start()
        # 활성화 대기
        end = time.time() + 10
        while time.time() < end:
            try:
                if self.state().get("browser", {}).get("active"):
                    return
            except Exception:  # noqa: BLE001
                pass
            time.sleep(0.3)
        raise DriverError("콘솔 browser.active 가 true 가 되지 않음 (adb forward tcp:8088 tcp:8088 확인)")

    def stop_heartbeat(self):
        self._hb_stop.set()
        if self._hb_thread:
            self._hb_thread.join(timeout=3)
            self._hb_thread = None

    def put(self, endpoint, **params):
        """PUT /vehicle/<endpoint>?params. 실패 시 DriverError(상태코드+본문)."""
        qs = urllib.parse.urlencode(params)
        path = f"/vehicle/{endpoint}" + (f"?{qs}" if qs else "")
        code, body = self._http("PUT", path)
        if code != 200:
            raise DriverError(f"PUT {path} -> HTTP {code}: {body[:200]}")
        return body

    def state(self):
        code, body = self._http("GET", "/vehicle/state")
        if code != 200:
            raise DriverError(f"GET /vehicle/state -> HTTP {code}: {body[:200]}")
        s = json.loads(body)
        if "browser" not in s:
            try:
                c2, b2 = self._http("GET", "/state")
                if c2 == 200:
                    s["browser"] = json.loads(b2).get("browser", {})
            except Exception:  # noqa: BLE001
                pass
        return s

    # convenience wrappers
    def set_gear(self, g):
        self.put("gear", gear={"P": "Park", "D": "Drive"}.get(g, g))

    def set_telltale(self, name, on, color="Red"):
        self.put("telltales/simulator", color=color, state="On" if on else "Off", type=name)

    def set_soc(self, v):
        self.put("ev-battery", field="soc", value=v)

    def set_odometer(self, km):
        self.put("odometer", traveled_distance_km=km)

    def set_tire(self, wheel, kpa):
        self.put("tire-pressure", pressure_kpa=kpa, area=TIRE_AREAS[wheel])

    def odometer(self):
        return int(round(float(self.state()["odometer"]["traveled_distance_km"])))

    def soc(self):
        return int(round(float(self.state()["ev_battery"]["state_of_charge"])))

    def tire_kpa(self):
        return self.state()["tire_pressure"]["pressure_kpa"]

    def reset_console(self, odometer_target=None, tire_kpa=230, soc=80):
        """차량 신호를 중립으로. 주행거리는 현재값이 target보다 낮을 때만 올림. 현재 기준값을 반환."""
        self.put("gear", gear="Park")
        for t in TELLTALES:
            for color in ("Red",):
                try:
                    self.put("telltales/simulator", color=color, state="Off", type=t)
                except DriverError as e:
                    if color == "Red":
                        self.log(f"  (경고등 {t} Off 실패: {e})")
        self.put("body", trunk="false", frunk="false", door_fl="false", door_fr="false",
                 door_rl="false", door_rr="false", window_fl=0, window_fr=0, window_rl=0, window_rr=0)
        for w in TIRE_AREAS:
            self.set_tire(w, tire_kpa)
        self.set_soc(soc)
        try:
            self.put("lights", feature="headlamp_mode", value="Auto")
        except DriverError as e:
            self.log(f"  (headlamp 설정 실패: {e})")
        cur = self.odometer()
        if odometer_target is not None and cur < odometer_target:
            self.set_odometer(odometer_target)
            cur = self.odometer()
        time.sleep(0.5)
        return cur

    # ------------------------------------------------------------ adb
    def adb(self, *args, timeout=30, binary=False):
        r = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, timeout=timeout)
        if r.returncode != 0:
            raise DriverError(f"adb {' '.join(args)} 실패: {r.stderr.decode('utf8', 'replace')[:300]}")
        return r.stdout if binary else r.stdout.decode("utf8", "replace")

    def ui_dump(self):
        """uiautomator dump 를 한 번 파싱해 Node 리스트로 반환 (조상 clickable/enabled 상속)."""
        last_err = None
        for _ in range(3):
            try:
                self.adb("shell", "uiautomator", "dump", "/sdcard/rl_ui.xml", timeout=20)
                xml = self.adb("exec-out", "cat", "/sdcard/rl_ui.xml", timeout=20)
                root = ET.fromstring(xml)
                break
            except (DriverError, ET.ParseError, subprocess.TimeoutExpired) as e:
                last_err = e
                time.sleep(0.5)
        else:
            raise DriverError(f"ui dump 실패: {last_err}")
        nodes = []

        def walk(el, anc):
            text = el.get("text", "") or ""
            cl = el.get("clickable") == "true"
            en = el.get("enabled") != "false"
            b = _parse_bounds(el.get("bounds"))
            if cl:
                anc = (True, en, b)
            if text:
                n = Node(text, b, en, cl, el.get("package", ""))
                if anc:
                    n.anc_clickable, n.anc_enabled, n.anc_bounds = anc
                    n.enabled = n.enabled and anc[1]
                nodes.append(n)
            for ch in el:
                walk(ch, anc)

        walk(root, None)
        self._last_nodes = nodes
        return nodes

    @staticmethod
    def _in_app(n):
        return n.pkg == APP_PKG

    def screen_text(self, nodes=None, pkg=APP_PKG):
        nodes = nodes if nodes is not None else self.ui_dump()
        return [n.text.replace("&#10;", "\n") for n in nodes if n.pkg == pkg]

    def _screen_blob(self, nodes=None):
        t = self.screen_text(nodes)
        return " | ".join(x.replace("\n", " ") for x in t) if t else "(앱 텍스트 없음)"

    @staticmethod
    def _match(n, text, exact, ):
        return n.text == text if exact else text in n.text

    def find(self, text, exact=True, nodes=None, prefer_clickable=True, pkg=APP_PKG):
        nodes = nodes if nodes is not None else self.ui_dump()
        ms = [n for n in nodes if n.pkg == pkg and self._match(n, text, exact)]
        if prefer_clickable:
            cl = [n for n in ms if n.anc_clickable]
            if cl:
                ms = cl
        if not ms:
            return None
        # 키패드 숫자 등 중복: 화면 가장 아래(마지막) 일치 항목
        return max(ms, key=lambda n: (n.bounds[1], n.bounds[0]))

    def has_text(self, text, exact=True, nodes=None, pkg=APP_PKG):
        nodes = nodes if nodes is not None else self.ui_dump()
        return any(n.pkg == pkg and self._match(n, text, exact) for n in nodes)

    def wait_text(self, text, timeout=15, exact=True, absent=False, interval=0.5, pkg=APP_PKG):
        """text 가 나타날 때까지(absent=True 이면 사라질 때까지) 대기. 실패 시 DriverError(화면 텍스트 포함)."""
        end = time.time() + timeout
        while True:
            nodes = self.ui_dump()
            present = self.has_text(text, exact, nodes, pkg)
            if present != absent:
                return nodes
            if time.time() > end:
                raise DriverError(
                    f"{'사라지길' if absent else '나타나길'} 기다린 텍스트 '{text}' ({timeout}s 초과). "
                    f"현재 화면: {self._screen_blob(nodes)}")
            time.sleep(interval)

    def wait_any(self, texts, timeout=15, exact=True):
        end = time.time() + timeout
        while True:
            nodes = self.ui_dump()
            for t in texts:
                if self.has_text(t, exact, nodes):
                    return t
            if time.time() > end:
                raise DriverError(f"{texts} 중 어느 것도 나타나지 않음 ({timeout}s). 현재 화면: {self._screen_blob(nodes)}")
            time.sleep(0.5)

    def is_enabled(self, text, exact=True, nodes=None):
        n = self.find(text, exact, nodes)
        if n is None:
            raise DriverError(f"버튼 '{text}' 없음. 현재 화면: {self._screen_blob(nodes)}")
        return n.enabled

    def tap_xy(self, x, y):
        self.adb("shell", "input", "tap", str(x), str(y))

    def tap_text(self, text, exact=True, timeout=8, nodes=None):
        end = time.time() + timeout
        while True:
            nodes = self.ui_dump() if nodes is None else nodes
            n = self.find(text, exact, nodes)
            if n is not None:
                if not n.enabled:
                    raise DriverError(f"버튼 '{text}' 이(가) 비활성(enabled=false). 현재 화면: {self._screen_blob(nodes)}")
                x, y = n.center()
                self.tap_xy(x, y)
                return
            nodes = None
            if time.time() > end:
                raise DriverError(f"탭할 '{text}' 없음 ({timeout}s). 현재 화면: {self._screen_blob(self._last_nodes)}")
            time.sleep(0.5)

    def screenshot(self, name):
        os.makedirs(self.out_dir, exist_ok=True)
        self._shot_n += 1
        safe = re.sub(r"[^0-9A-Za-z가-힣_.-]+", "_", name)[:80]
        path = os.path.join(self.out_dir, f"{self._shot_n:03d}_{safe}.png")
        try:
            data = self.adb("exec-out", "screencap", "-p", binary=True, timeout=30)
            with open(path, "wb") as f:
                f.write(data)
        except Exception as e:  # noqa: BLE001
            self.log(f"  (스크린샷 실패: {e})")
            return None
        return path

    # ------------------------------------------------------------ app
    def dismiss_system_dialogs(self):
        for _ in range(3):
            nodes = self.ui_dump()
            hit = [n for n in nodes if n.pkg != APP_PKG and n.text in ("Done", "OK", "Close", "Got it", "완료")
                   and n.anc_clickable]
            if not hit:
                return
            x, y = hit[0].center()
            self.tap_xy(x, y)
            time.sleep(0.6)

    def restart_app(self, wait=True):
        self.adb("shell", "am", "force-stop", APP_PKG)
        time.sleep(0.5)
        self.adb("shell", "am", "start", "-n", APP_ACTIVITY)
        time.sleep(2.0)
        self.dismiss_system_dialogs()
        if wait:
            try:
                self.wait_text("본인 확인 · 휴대폰 번호 뒤 4자리", timeout=20)
            except DriverError:
                self.dismiss_system_dialogs()
                self.wait_text("본인 확인 · 휴대폰 번호 뒤 4자리", timeout=10)

    def bring_app_front(self):
        self.adb("shell", "am", "start", "-n", APP_ACTIVITY)
        time.sleep(1.5)

    def type_code(self, code, settle=1.5):
        for ch in code:
            nodes = self.ui_dump()
            cand = [n for n in nodes if n.pkg == APP_PKG and n.text == ch and n.anc_clickable]
            if not cand:
                raise DriverError(f"키패드 '{ch}' 없음. 현재 화면: {self._screen_blob(nodes)}")
            x, y = max(cand, key=lambda n: n.bounds[1]).center()
            self.tap_xy(x, y)
            time.sleep(0.25)
        time.sleep(settle)

    def enter_wrong_codes(self, n, start=0):
        """오답 n회 (0000, 1111, ... 정답 1234 와 다른 코드)."""
        for i in range(n):
            code = str((i + start) % 9 + 1) * 4  # 1111..9999 (1234 아님)
            self.type_code(code)
            if i < n - 1:
                time.sleep(0.3)

    def login(self, code="1234"):
        self.type_code(code)
        self.wait_text("확인, 출발하기", timeout=15)

    # ------------------------------------------------------------ misc
    def wait_until(self, cond, timeout=10, interval=0.5, desc="조건"):
        end = time.time() + timeout
        last = None
        while time.time() < end:
            try:
                last = cond()
                if last:
                    return last
            except DriverError:
                pass
            time.sleep(interval)
        raise DriverError(f"{desc} 만족 안 됨 ({timeout}s)")

    def foreground_pkg(self):
        """포커스된 창의 패키지 (멀티 디스플레이면 앱/내비 쪽 우선)."""
        out = self.adb("shell", "dumpsys", "window")
        pkgs = re.findall(r"mCurrentFocus=Window\{[0-9a-f]+ u\d+ ([\w.]+)/", out)
        for p in (NAVI_PKG, APP_PKG):
            if p in pkgs:
                return p
        return pkgs[0] if pkgs else ""

    def neutral(self):
        """종료 시 중립 상태 (주행거리는 건드리지 않음)."""
        try:
            self.reset_console()
        except DriverError as e:
            self.log(f"  (중립 복원 실패: {e})")
