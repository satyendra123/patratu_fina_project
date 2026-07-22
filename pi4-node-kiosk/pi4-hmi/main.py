import json
import os
import re
import socket
import sqlite3
import subprocess
import sys
import time
import uuid
from copy import deepcopy
from pathlib import Path

from PyQt5.QtCore import QObject, QDateTime, Qt, QTimer, QUrl, pyqtSignal
from PyQt5.QtGui import QFont
from PyQt5.QtWebSockets import QWebSocket
from PyQt5.QtWidgets import (
    QApplication,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QPushButton,
    QProgressBar,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)


BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parent
CONFIG_FILE = BASE_DIR / "config.json"
ENV_FILE = PROJECT_DIR / ".env"
DATA_DIR = BASE_DIR / "data"
DB_FILE = DATA_DIR / "hmi-cache.sqlite3"

DEFAULT_CONFIG = {
    "companyName": "YAMUNA FINA PROJECT",
    "device": {
        "serial": "PI4-GATE-001",
        "location": "Main Gate",
        "inout": "IN",
        "ip": "",
        "dns": "8.8.8.8",
        "gateway": "",
        "serverIp": "127.0.0.1",
        "websocketPort": 8088,
        "websocketUrl": "ws://127.0.0.1:8088/ws/device",
    },
    "gate": {
        "mode": "mock",
        "gpioPin": 17,
        "pulseMs": 1200,
        "chip": "gpiochip0",
    },
    "sync": {
        "reconnectMs": 3000,
        "heartbeatMs": 15000,
        "syncMs": 30000,
    },
    "roles": {
        "managerPin": "1111",
        "adminPin": "2222",
        "superadminPin": "3333",
    },
}


def deep_merge(base, patch):
    result = deepcopy(base)
    for key, value in (patch or {}).items():
        if isinstance(value, dict) and isinstance(result.get(key), dict):
            result[key] = deep_merge(result[key], value)
        else:
            result[key] = value
    return result


def read_json(path, fallback):
    if not path.exists():
        return deepcopy(fallback)
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def load_env_file():
    if not ENV_FILE.exists():
        return
    for raw_line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def load_config():
    load_env_file()
    config = deep_merge(DEFAULT_CONFIG, read_json(CONFIG_FILE, {}))
    config["companyName"] = os.getenv("COMPANY_NAME", config["companyName"])
    config["device"]["serial"] = os.getenv("DEVICE_SERIAL", config["device"]["serial"])
    config["device"]["websocketUrl"] = os.getenv("CLOUD_WS_URL", config["device"]["websocketUrl"])
    config["gate"]["mode"] = os.getenv("GATE_MODE", config["gate"]["mode"])
    config["gate"]["gpioPin"] = int(os.getenv("GATE_GPIO_PIN", config["gate"]["gpioPin"]))
    config["gate"]["pulseMs"] = int(os.getenv("GATE_PULSE_MS", config["gate"]["pulseMs"]))
    return config


def save_config(patch):
    current = read_json(CONFIG_FILE, {})
    next_config = deep_merge(current, patch)
    with CONFIG_FILE.open("w", encoding="utf-8") as handle:
        json.dump(next_config, handle, indent=2)
        handle.write("\n")
    return load_config()


def get_primary_ip():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def normalize_qr_code(raw):
    value = str(raw or "").strip()
    if not value:
        return ""
    try:
        parsed = json.loads(value)
        if isinstance(parsed, (str, int, float)):
            return str(parsed).strip()
        for key in ("enrollId", "enrollid", "id", "userCode", "code"):
            if key in parsed:
                return str(parsed[key]).strip()
    except (ValueError, TypeError):
        pass
    return re.sub(r"^ID[:=]", "", value, flags=re.IGNORECASE).strip()


class LocalStore:
    def __init__(self, db_file=DB_FILE):
        DATA_DIR.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(str(db_file))
        self.connection.row_factory = sqlite3.Row
        self.ensure_schema()

    def ensure_schema(self):
        cur = self.connection.cursor()
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              role TEXT NOT NULL DEFAULT 'user',
              role_id INTEGER DEFAULT 0,
              status INTEGER NOT NULL DEFAULT 1,
              updated_at TEXT,
              version INTEGER DEFAULT 0
            )
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS scans (
              scan_id TEXT PRIMARY KEY,
              serial TEXT NOT NULL,
              code TEXT,
              raw_code TEXT,
              result_json TEXT,
              valid INTEGER DEFAULT 0,
              synced INTEGER DEFAULT 0,
              scanned_at TEXT NOT NULL
            )
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS meta (
              key TEXT PRIMARY KEY,
              value TEXT
            )
            """
        )
        self.connection.commit()

    def apply_snapshot(self, snapshot):
        if snapshot.get("full"):
            self.connection.execute("DELETE FROM users")
        for user in snapshot.get("users", []):
            self.connection.execute(
                """
                INSERT INTO users (id, name, role, role_id, status, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  name = excluded.name,
                  role = excluded.role,
                  role_id = excluded.role_id,
                  status = excluded.status,
                  updated_at = excluded.updated_at,
                  version = excluded.version
                """,
                (
                    str(user.get("id")),
                    user.get("name", ""),
                    user.get("role", "user"),
                    int(user.get("roleId") or 0),
                    int(user.get("status") or 0),
                    str(user.get("updatedAt") or ""),
                    int(user.get("version") or 0),
                ),
            )
        for deleted_id in snapshot.get("deletedUserIds", []):
            self.connection.execute("DELETE FROM users WHERE id = ?", (str(deleted_id),))
        self.set_meta("lastSyncCursor", str(snapshot.get("serverCursor") or "0"))
        self.connection.commit()

    def validate(self, code):
        row = self.connection.execute("SELECT * FROM users WHERE id = ?", (str(code),)).fetchone()
        valid = bool(row and int(row["status"]) == 1)
        return {
            "code": code,
            "valid": valid,
            "access": 1 if valid else 0,
            "openGate": valid,
            "user": {
                "id": row["id"],
                "name": row["name"],
                "role": row["role"],
            } if row else None,
            "message": "Valid access" if valid else "Not found in offline cache",
            "source": "pyqt-sqlite",
        }

    def save_scan(self, scan, synced=False):
        result = scan.get("result") or {}
        self.connection.execute(
            """
            INSERT INTO scans (scan_id, serial, code, raw_code, result_json, valid, synced, scanned_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(scan_id) DO UPDATE SET
              result_json = excluded.result_json,
              valid = excluded.valid,
              synced = excluded.synced
            """,
            (
                scan["scanId"],
                scan["serial"],
                scan.get("code"),
                scan.get("rawCode"),
                json.dumps(result),
                1 if result.get("valid") else 0,
                1 if synced else 0,
                scan["scannedAt"],
            ),
        )
        self.connection.commit()

    def pending_scans(self, limit=100):
        rows = self.connection.execute(
            "SELECT * FROM scans WHERE synced = 0 ORDER BY scanned_at LIMIT ?",
            (limit,),
        ).fetchall()
        return [self.row_to_scan(row) for row in rows]

    def mark_scans_synced(self, scan_ids):
        for scan_id in scan_ids:
            self.connection.execute("UPDATE scans SET synced = 1 WHERE scan_id = ?", (scan_id,))
        self.connection.commit()

    def recent_scans(self, limit=8):
        rows = self.connection.execute(
            "SELECT * FROM scans ORDER BY scanned_at DESC LIMIT ?",
            (limit,),
        ).fetchall()
        return [self.row_to_scan(row) for row in rows]

    def user_count(self):
        row = self.connection.execute("SELECT COUNT(*) AS count FROM users").fetchone()
        return int(row["count"])

    def pending_count(self):
        row = self.connection.execute("SELECT COUNT(*) AS count FROM scans WHERE synced = 0").fetchone()
        return int(row["count"])

    def set_meta(self, key, value):
        self.connection.execute(
            "INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (key, value),
        )

    def get_meta(self, key, fallback=""):
        row = self.connection.execute("SELECT value FROM meta WHERE key = ?", (key,)).fetchone()
        return row["value"] if row else fallback

    def row_to_scan(self, row):
        return {
            "scanId": row["scan_id"],
            "serial": row["serial"],
            "code": row["code"],
            "rawCode": row["raw_code"],
            "result": json.loads(row["result_json"] or "{}"),
            "scannedAt": row["scanned_at"],
        }


class GateController:
    def __init__(self, config):
        self.config = config

    def pulse(self, reason="scan"):
        gate = self.config.get("gate", {})
        mode = str(gate.get("mode", "mock")).lower()
        pin = int(gate.get("gpioPin", 17))
        pulse_ms = int(gate.get("pulseMs", 1200))

        if mode == "mock":
            print(f"[gate] mock open pin={pin} pulseMs={pulse_ms} reason={reason}")
            return True

        if mode == "gpiod":
            seconds = max(1, int((pulse_ms + 999) / 1000))
            subprocess.Popen([
                "gpioset",
                "--mode=time",
                f"--sec={seconds}",
                gate.get("chip", "gpiochip0"),
                f"{pin}=1",
            ])
            return True

        if mode == "sysfs":
            base = f"/sys/class/gpio/gpio{pin}"
            subprocess.run(["sh", "-c", f"test -d {base} || echo {pin} > /sys/class/gpio/export"], check=False)
            subprocess.run(["sh", "-c", f"echo out > {base}/direction"], check=False)
            subprocess.run(["sh", "-c", f"echo 1 > {base}/value"], check=False)
            QTimer.singleShot(
                pulse_ms,
                lambda: subprocess.run(["sh", "-c", f"echo 0 > {base}/value"], check=False),
            )
            return True

        raise ValueError(f"Unsupported gate mode: {mode}")


class CloudClient(QObject):
    connectedChanged = pyqtSignal(bool)
    messageReceived = pyqtSignal(dict)
    statusMessage = pyqtSignal(str)

    def __init__(self, config, store, parent=None):
        super().__init__(parent)
        self.config = config
        self.store = store
        self.socket = QWebSocket()
        self.connected = False
        self.reconnect_timer = QTimer(self)
        self.heartbeat_timer = QTimer(self)
        self.sync_timer = QTimer(self)

        self.socket.connected.connect(self.on_connected)
        self.socket.disconnected.connect(self.on_disconnected)
        self.socket.textMessageReceived.connect(self.on_text_message)
        self.socket.error.connect(lambda error: self.statusMessage.emit(self.socket.errorString()))

        self.reconnect_timer.timeout.connect(self.connect_to_cloud)
        self.heartbeat_timer.timeout.connect(self.send_heartbeat)
        self.sync_timer.timeout.connect(self.request_sync)

    def start(self):
        self.connect_to_cloud()

    def connect_to_cloud(self):
        self.statusMessage.emit("Connecting cloud")
        self.socket.open(QUrl(self.config["device"]["websocketUrl"]))

    def on_connected(self):
        self.connected = True
        self.connectedChanged.emit(True)
        self.statusMessage.emit("Cloud connected")
        self.reconnect_timer.stop()
        self.send_json({
            "type": "device.hello",
            "serial": self.config["device"]["serial"],
            "ip": self.config["device"].get("ip", ""),
            "agentVersion": "pyqt-hmi-0.1",
            "inventory": {
                "users": self.store.user_count(),
                "pendingScans": self.store.pending_count(),
                "lastSyncCursor": self.store.get_meta("lastSyncCursor", "0"),
            },
        })
        self.request_sync()
        self.flush_pending_scans()
        self.heartbeat_timer.start(int(self.config["sync"]["heartbeatMs"]))
        self.sync_timer.start(int(self.config["sync"]["syncMs"]))

    def on_disconnected(self):
        self.connected = False
        self.connectedChanged.emit(False)
        self.statusMessage.emit("Cloud disconnected")
        self.heartbeat_timer.stop()
        self.sync_timer.stop()
        self.reconnect_timer.start(int(self.config["sync"]["reconnectMs"]))

    def on_text_message(self, text):
        try:
            self.messageReceived.emit(json.loads(text))
        except ValueError:
            return

    def send_json(self, payload):
        if not self.connected:
            return False
        self.socket.sendTextMessage(json.dumps(payload, separators=(",", ":")))
        return True

    def send_heartbeat(self):
        self.send_json({
            "type": "heartbeat",
            "serial": self.config["device"]["serial"],
        })

    def request_sync(self):
        self.send_json({
            "type": "sync.request",
            "serial": self.config["device"]["serial"],
            "lastSyncCursor": self.store.get_meta("lastSyncCursor", "0"),
        })

    def send_scan(self, scan):
        return self.send_json({
            "type": "scan.event",
            "scanId": scan["scanId"],
            "code": scan["code"],
            "rawCode": scan["rawCode"],
            "scannedAt": scan["scannedAt"],
            "inout": self.config["device"].get("inout", "IN"),
            "localResult": scan["result"],
        })

    def flush_pending_scans(self):
        pending = self.store.pending_scans()
        if not pending:
            return
        self.send_json({
            "type": "scan.batch",
            "serial": self.config["device"]["serial"],
            "events": pending,
        })


class StatusBadge(QFrame):
    def __init__(self, label, value):
        super().__init__()
        self.setObjectName("panel")
        layout = QVBoxLayout(self)
        self.label = QLabel(label)
        self.label.setObjectName("kicker")
        self.value = QLabel(str(value))
        self.value.setStyleSheet("font-size: 18px; font-weight: 800;")
        layout.addWidget(self.label)
        layout.addWidget(self.value)

    def set_value(self, value):
        self.value.setText(str(value))


class MainWindow(QMainWindow):
    def __init__(self, config, store):
        super().__init__()
        self.config = config
        self.store = store
        self.gate = GateController(config)
        self.cloud = CloudClient(config, store)
        self.cloud.connectedChanged.connect(self.on_cloud_status)
        self.cloud.messageReceived.connect(self.on_cloud_message)
        self.cloud.statusMessage.connect(self.set_message)

        self.setWindowTitle("Pi4 Touch HMI")
        self.stack = QStackedWidget()
        self.setCentralWidget(self.stack)
        self.splash_progress = 0
        self.cloud_online = False
        self.message = "Starting HMI"
        self.last_sync = "Never"
        self.opened_scans = set()

        self.splash_page = self.build_splash_page()
        self.home_page = self.build_home_page()
        self.settings_page = self.build_settings_page()
        self.stack.addWidget(self.splash_page)
        self.stack.addWidget(self.home_page)
        self.stack.addWidget(self.settings_page)

        self.clock_timer = QTimer(self)
        self.clock_timer.timeout.connect(self.refresh_clock)
        self.clock_timer.start(500)

        self.splash_timer = QTimer(self)
        self.splash_timer.timeout.connect(self.advance_splash)
        self.splash_timer.start(80)

        self.cloud.start()
        self.apply_style()

    def build_splash_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(70, 70, 70, 70)
        layout.setSpacing(20)
        layout.addStretch()

        label = QLabel("ACCESS CONTROL")
        label.setAlignment(Qt.AlignCenter)
        label.setObjectName("splashKicker")
        layout.addWidget(label)

        title = QLabel(self.config["companyName"])
        title.setAlignment(Qt.AlignCenter)
        title.setObjectName("splashTitle")
        title.setWordWrap(True)
        layout.addWidget(title)

        subtitle = QLabel("Linux Pi4 Touch HMI")
        subtitle.setAlignment(Qt.AlignCenter)
        subtitle.setObjectName("splashSubtitle")
        layout.addWidget(subtitle)

        self.progress = QProgressBar()
        self.progress.setRange(0, 100)
        self.progress.setTextVisible(True)
        layout.addWidget(self.progress)
        layout.addStretch()
        return page

    def build_home_page(self):
        page = QWidget()
        root = QVBoxLayout(page)
        root.setContentsMargins(18, 18, 18, 18)
        root.setSpacing(14)

        top = QHBoxLayout()
        title_box = QVBoxLayout()
        kicker = QLabel("Secure Gate")
        kicker.setObjectName("kicker")
        self.company_label = QLabel(self.config["companyName"])
        self.company_label.setObjectName("company")
        title_box.addWidget(kicker)
        title_box.addWidget(self.company_label)
        top.addLayout(title_box, 1)

        self.clock_label = StatusBadge("Time", "--:--:--")
        self.ip_label = StatusBadge("IP", get_primary_ip())
        self.cloud_label = StatusBadge("Cloud", "Offline")
        top.addWidget(self.clock_label)
        top.addWidget(self.ip_label)
        top.addWidget(self.cloud_label)
        root.addLayout(top)

        middle = QHBoxLayout()
        scanner_panel = QFrame()
        scanner_panel.setObjectName("panel")
        scanner_layout = QVBoxLayout(scanner_panel)
        scan_title = QLabel("Scan QR")
        scan_title.setObjectName("sectionTitle")
        scanner_layout.addWidget(scan_title)
        self.scanner_input = QLineEdit()
        self.scanner_input.setPlaceholderText("Scanner input")
        self.scanner_input.returnPressed.connect(self.process_scan)
        self.scanner_input.setObjectName("scannerInput")
        scanner_layout.addWidget(self.scanner_input)
        scanner_layout.addStretch()

        summary = QHBoxLayout()
        self.serial_badge = StatusBadge("Device", self.config["device"]["serial"])
        self.location_badge = StatusBadge("Location", self.config["device"].get("location", "Main Gate"))
        self.inout_badge = StatusBadge("Mode", self.config["device"].get("inout", "IN"))
        summary.addWidget(self.serial_badge)
        summary.addWidget(self.location_badge)
        summary.addWidget(self.inout_badge)
        scanner_layout.addLayout(summary)
        middle.addWidget(scanner_panel, 3)

        result_panel = QFrame()
        result_panel.setObjectName("panel")
        result_layout = QVBoxLayout(result_panel)
        result_title = QLabel("Last Scan")
        result_title.setObjectName("sectionTitle")
        result_layout.addWidget(result_title)
        self.result_card = QLabel("Ready\nScanner active")
        self.result_card.setObjectName("resultIdle")
        self.result_card.setAlignment(Qt.AlignCenter)
        self.result_card.setWordWrap(True)
        result_layout.addWidget(self.result_card, 1)
        recent_title = QLabel("Recent")
        recent_title.setObjectName("sectionTitle")
        result_layout.addWidget(recent_title)
        self.recent_labels = []
        for _ in range(8):
            item = QLabel("")
            item.setObjectName("recentRow")
            item.setMinimumHeight(34)
            self.recent_labels.append(item)
            result_layout.addWidget(item)
        middle.addWidget(result_panel, 2)
        root.addLayout(middle, 1)

        bottom = QHBoxLayout()
        settings_btn = QPushButton("Settings")
        settings_btn.clicked.connect(self.open_settings)
        sync_btn = QPushButton("Sync")
        sync_btn.clicked.connect(self.cloud.request_sync)
        bottom.addWidget(settings_btn)
        bottom.addWidget(sync_btn)
        root.addLayout(bottom)
        return page

    def build_settings_page(self):
        page = QWidget()
        root = QVBoxLayout(page)
        root.setContentsMargins(18, 18, 18, 18)

        header = QHBoxLayout()
        back = QPushButton("Back")
        back.clicked.connect(self.close_settings)
        save = QPushButton("Save")
        save.clicked.connect(self.save_settings)
        title = QLabel("Settings")
        title.setObjectName("company")
        header.addWidget(back)
        header.addWidget(title, 1)
        header.addWidget(save)
        root.addLayout(header)

        grid = QGridLayout()
        grid.setSpacing(12)
        self.settings_inputs = {}
        tiles = [
            self.make_network_tile(),
            self.make_roles_tile(),
            self.make_data_tile(),
            self.make_sync_tile(),
            self.make_gate_tile(),
            self.make_diagnostics_tile(),
        ]
        for index, tile in enumerate(tiles):
            grid.addWidget(tile, index // 2, index % 2)
        root.addLayout(grid, 1)
        return page

    def make_tile(self, title):
        tile = QFrame()
        tile.setObjectName("panel")
        layout = QVBoxLayout(tile)
        heading = QLabel(title)
        heading.setObjectName("sectionTitle")
        layout.addWidget(heading)
        return tile, layout

    def make_input(self, layout, key, label, value="", password=False):
        row = QHBoxLayout()
        row.addWidget(QLabel(label))
        field = QLineEdit(str(value or ""))
        if password:
            field.setEchoMode(QLineEdit.Password)
        row.addWidget(field)
        layout.addLayout(row)
        self.settings_inputs[key] = field
        return field

    def make_network_tile(self):
        tile, layout = self.make_tile("Device Network")
        device = self.config["device"]
        self.make_input(layout, "ip", "IP", device.get("ip", ""))
        self.make_input(layout, "dns", "DNS", device.get("dns", ""))
        self.make_input(layout, "gateway", "Gateway", device.get("gateway", ""))
        self.make_input(layout, "serverIp", "Server IP", device.get("serverIp", ""))
        self.make_input(layout, "websocketPort", "WS Port", device.get("websocketPort", 8088))
        return tile

    def make_roles_tile(self):
        tile, layout = self.make_tile("Roles")
        self.make_input(layout, "managerPin", "Manager PIN", "", True)
        self.make_input(layout, "adminPin", "Admin PIN", "", True)
        self.make_input(layout, "superadminPin", "Super PIN", "", True)
        layout.addStretch()
        return tile

    def make_data_tile(self):
        tile, layout = self.make_tile("Data Management")
        self.users_metric = QLabel()
        self.pending_metric = QLabel()
        layout.addWidget(self.users_metric)
        layout.addWidget(self.pending_metric)
        layout.addStretch()
        return tile

    def make_sync_tile(self):
        tile, layout = self.make_tile("Sync")
        self.make_input(layout, "websocketUrl", "Cloud URL", self.config["device"].get("websocketUrl", ""))
        self.make_input(layout, "heartbeatMs", "Heartbeat", self.config["sync"].get("heartbeatMs", 15000))
        self.make_input(layout, "syncMs", "Sync ms", self.config["sync"].get("syncMs", 30000))
        btn = QPushButton("Sync Now")
        btn.clicked.connect(self.cloud.request_sync)
        layout.addWidget(btn)
        return tile

    def make_gate_tile(self):
        tile, layout = self.make_tile("Gate & Scanner")
        gate = self.config["gate"]
        self.make_input(layout, "gateMode", "Mode", gate.get("mode", "mock"))
        self.make_input(layout, "gpioPin", "GPIO Pin", gate.get("gpioPin", 17))
        self.make_input(layout, "pulseMs", "Pulse ms", gate.get("pulseMs", 1200))
        btn = QPushButton("Test Gate")
        btn.clicked.connect(lambda: self.gate.pulse("settings-test"))
        layout.addWidget(btn)
        return tile

    def make_diagnostics_tile(self):
        tile, layout = self.make_tile("Diagnostics")
        self.diag_cloud = QLabel("Cloud: Offline")
        self.diag_sync = QLabel("Last Sync: Never")
        self.diag_message = QLabel("Message: Starting")
        self.diag_message.setWordWrap(True)
        layout.addWidget(self.diag_cloud)
        layout.addWidget(self.diag_sync)
        layout.addWidget(self.diag_message)
        layout.addStretch()
        return tile

    def advance_splash(self):
        self.splash_progress = min(100, self.splash_progress + 4)
        self.progress.setValue(self.splash_progress)
        if self.splash_progress >= 100:
            self.splash_timer.stop()
            self.stack.setCurrentWidget(self.home_page)
            self.scanner_input.setFocus()

    def refresh_clock(self):
        self.clock_label.set_value(QDateTime.currentDateTime().toString("hh:mm:ss"))
        self.ip_label.set_value(get_primary_ip())
        self.refresh_metrics()

    def process_scan(self):
        raw_code = self.scanner_input.text()
        self.scanner_input.clear()
        code = normalize_qr_code(raw_code)
        if not code:
            return

        result = self.store.validate(code)
        scan = {
            "scanId": f"scan-{int(time.time() * 1000)}-{uuid.uuid4().hex[:8]}",
            "serial": self.config["device"]["serial"],
            "code": code,
            "rawCode": raw_code,
            "result": result,
            "scannedAt": QDateTime.currentDateTimeUtc().toString(Qt.ISODate),
        }
        if result["valid"]:
            self.gate.pulse(f"scan:{code}")
            self.opened_scans.add(scan["scanId"])
        sent = self.cloud.send_scan(scan)
        self.store.save_scan(scan, synced=sent)
        self.render_scan(scan)
        self.render_recent()

    def on_cloud_message(self, message):
        msg_type = message.get("type")
        if msg_type == "sync.snapshot":
            self.store.apply_snapshot(message)
            self.last_sync = QDateTime.currentDateTime().toString("hh:mm:ss")
            self.set_message(f"Synced {self.store.user_count()} users")
            self.render_recent()
        elif msg_type == "scan.result":
            result = message.get("result", {})
            scan = {
                "scanId": message.get("scanId", ""),
                "serial": self.config["device"]["serial"],
                "code": result.get("code", ""),
                "rawCode": result.get("code", ""),
                "result": result,
                "scannedAt": QDateTime.currentDateTimeUtc().toString(Qt.ISODate),
            }
            if result.get("valid") and scan["scanId"] not in self.opened_scans:
                self.gate.pulse(f"cloud-scan:{scan['scanId']}")
                self.opened_scans.add(scan["scanId"])
            self.store.save_scan(scan, synced=True)
            self.render_scan(scan)
        elif msg_type == "scan.batch.ack":
            self.store.mark_scans_synced(message.get("scanIds", []))
            self.set_message(f"Synced {message.get('saved', 0)} offline scans")
        elif msg_type == "command.batch":
            for command in message.get("commands", []):
                self.execute_command(command)

    def execute_command(self, command):
        payload = command.get("payload") or {}
        name = str(command.get("name", "")).lower()
        cmd = str(payload.get("cmd", "")).lower() if isinstance(payload, dict) else ""
        result = True
        try:
            if name in ("open_gate", "opendoor") or cmd == "opendoor":
                self.gate.pulse(f"command:{command.get('id')}")
            elif name == "sync_now":
                self.cloud.request_sync()
        except Exception:
            result = False
        self.cloud.send_json({
            "type": "command.ack",
            "commandId": command.get("id"),
            "result": result,
        })

    def render_scan(self, scan):
        result = scan.get("result", {})
        user = result.get("user") or {}
        title = "Gate Open" if result.get("valid") else "Access Denied"
        detail = user.get("name") or scan.get("code") or "Unknown"
        self.result_card.setText(f"{title}\n{detail}\n{result.get('message', '')}")
        self.result_card.setObjectName("resultValid" if result.get("valid") else "resultInvalid")
        self.result_card.style().unpolish(self.result_card)
        self.result_card.style().polish(self.result_card)

    def render_recent(self):
        scans = self.store.recent_scans()
        for index, label in enumerate(self.recent_labels):
            if index >= len(scans):
                label.setText("")
                continue
            result = scans[index].get("result", {})
            user = result.get("user") or {}
            status = "VALID" if result.get("valid") else "DENY"
            label.setText(f"{status}  {user.get('name') or scans[index].get('code') or ''}")

    def refresh_metrics(self):
        self.users_metric.setText(f"Users: {self.store.user_count()}")
        self.pending_metric.setText(f"Pending scans: {self.store.pending_count()}")
        self.diag_cloud.setText(f"Cloud: {'Online' if self.cloud_online else 'Offline'}")
        self.diag_sync.setText(f"Last Sync: {self.last_sync}")
        self.diag_message.setText(f"Message: {self.message}")

    def on_cloud_status(self, online):
        self.cloud_online = online
        self.cloud_label.set_value("Online" if online else "Offline")
        self.refresh_metrics()

    def set_message(self, message):
        self.message = message
        self.refresh_metrics()

    def open_settings(self):
        self.stack.setCurrentWidget(self.settings_page)

    def close_settings(self):
        self.stack.setCurrentWidget(self.home_page)
        self.scanner_input.setFocus()

    def save_settings(self):
        roles = {}
        if self.settings_inputs["managerPin"].text():
            roles["managerPin"] = self.settings_inputs["managerPin"].text()
        if self.settings_inputs["adminPin"].text():
            roles["adminPin"] = self.settings_inputs["adminPin"].text()
        if self.settings_inputs["superadminPin"].text():
            roles["superadminPin"] = self.settings_inputs["superadminPin"].text()
        patch = {
            "device": {
                "ip": self.settings_inputs["ip"].text(),
                "dns": self.settings_inputs["dns"].text(),
                "gateway": self.settings_inputs["gateway"].text(),
                "serverIp": self.settings_inputs["serverIp"].text(),
                "websocketPort": int(self.settings_inputs["websocketPort"].text() or 8088),
                "websocketUrl": self.settings_inputs["websocketUrl"].text(),
            },
            "sync": {
                "heartbeatMs": int(self.settings_inputs["heartbeatMs"].text() or 15000),
                "syncMs": int(self.settings_inputs["syncMs"].text() or 30000),
            },
            "gate": {
                "mode": self.settings_inputs["gateMode"].text(),
                "gpioPin": int(self.settings_inputs["gpioPin"].text() or 17),
                "pulseMs": int(self.settings_inputs["pulseMs"].text() or 1200),
            },
            "roles": roles,
        }
        self.config = save_config(patch)
        self.gate.config = self.config
        self.cloud.config = self.config
        self.set_message("Settings saved")
        self.close_settings()

    def apply_style(self):
        self.setFont(QFont("Arial", 12))
        self.setStyleSheet(
            """
            QWidget { background: #eef3f0; color: #17201b; }
            #splashKicker { color: #d8eee4; background: #24302b; padding: 8px; border-radius: 4px; font-weight: 700; }
            #splashTitle { color: #ffffff; background: #24302b; font-size: 44px; font-weight: 800; padding: 24px; border-radius: 8px; }
            #splashSubtitle { color: #24302b; font-size: 20px; font-weight: 700; }
            #kicker { color: #66736d; font-weight: 700; }
            #company { font-size: 28px; font-weight: 800; }
            #panel { background: #ffffff; border: 1px solid #d7dfda; border-radius: 8px; }
            #sectionTitle { font-size: 20px; font-weight: 800; color: #24302b; }
            #scannerInput { background: #ffffff; border: 2px solid #15804f; border-radius: 8px; padding: 18px; font-size: 28px; }
            QPushButton { background: #2d6f9f; color: white; border: 0; border-radius: 6px; padding: 16px; font-size: 20px; font-weight: 800; }
            QLineEdit { background: #ffffff; border: 1px solid #d7dfda; border-radius: 6px; padding: 10px; }
            QProgressBar { border: 1px solid #d7dfda; border-radius: 8px; text-align: center; height: 28px; background: #ffffff; }
            QProgressBar::chunk { background: #15804f; border-radius: 8px; }
            #resultIdle { background: #eef6f2; border-radius: 8px; padding: 18px; font-size: 26px; font-weight: 800; }
            #resultValid { background: #e7f7ee; color: #15804f; border-radius: 8px; padding: 18px; font-size: 28px; font-weight: 800; }
            #resultInvalid { background: #fff0ed; color: #c43f33; border-radius: 8px; padding: 18px; font-size: 28px; font-weight: 800; }
            #recentRow { background: #f8fbf9; border: 1px solid #d7dfda; border-radius: 4px; padding: 8px; }
            """
        )


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Pi4 Touch HMI")
    window = MainWindow(load_config(), LocalStore())
    window.showFullScreen()
    return app.exec_()


if __name__ == "__main__":
    sys.exit(main())
