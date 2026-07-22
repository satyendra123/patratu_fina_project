# Pi4 Touch HMI Access System

Ye project Raspberry Pi 4 + touch HMI ko QR scanner + gate controller device banane ke liye hai.
Cloud side Node.js Express + MySQL hai, aur Pi device side PyQt HMI hai.

## Parts

- `cloud-server/`: central Express + WebSocket server. Device connect hote hi inventory bhejta hai, user snapshot sync hota hai, pending command queue dispatch hoti hai.
- `pi4-hmi/main.py`: Pi par chalne wala single-file PyQt touch HMI. Scanner input, offline SQLite cache, cloud sync, settings, aur gate relay pulse sab isi file me hai.

## Express Structure

```text
cloud-server/
  app.js
  config/
  controllers/
  models/
  routes/
  utils/
```

## Quick Run

MySQL production run:

```powershell
cd pi4-node-kiosk
copy .env.example .env
npm install
node cloud-server/app.js
```

Pi HMI run:

```bash
cd pi4-node-kiosk
python -m pip install -r pi4-hmi/requirements.txt
python pi4-hmi/main.py
```

Demo without MySQL ke liye `.env` me temporary set karein:

```text
DB_DRIVER=json
```

UI:

```text
PyQt fullscreen touch HMI on Pi display
```

Demo mode me `DB_DRIVER=json` hota hai aur sample users `1001`, `1002` scan karke valid result milta hai. Production ke liye `DB_DRIVER=mysql` use karein.

## MySQL Mode

`.env` me:

```text
DB_DRIVER=mysql
MYSQL_HOST=192.168.1.10
MYSQL_PORT=3306
MYSQL_DATABASE=pi4_kiosk
MYSQL_USER=pi4_user
MYSQL_PASSWORD=your-password
CLOUD_WS_URL=ws://192.168.1.10:8088/ws/device
```

Node MySQL mapping:

- Users: `users`
- Device status: `devices`
- Attendance/scans: `scan_events`
- Live command queue: `device_commands`

MySQL me in 4 tables ko same names ke saath create karna hoga. Cloud server inhi tables ko read/write karta hai.

## PyQt HMI Features

- Company splash with background-style panel and progress bar.
- All Pi4 HMI code is in one file: `pi4-hmi/main.py`.
- Home screen with date/time, IP, cloud status, device serial, location, and IN/OUT mode.
- USB QR scanner support as keyboard input.
- Valid/invalid scan result panel with user details.
- Bottom Settings and Sync actions.
- Settings screen with 6 tiles: Device Network, Roles, Data Management, Sync, Gate & Scanner, Diagnostics.
- Offline SQLite cache at `pi4-hmi/data/hmi-cache.sqlite3`.
- WebSocket sync with Node cloud server.

## Pi4 Gate Setup

For dry run:

```text
GATE_MODE=mock
```

For GPIO using libgpiod:

```bash
sudo apt install gpiod
GATE_MODE=gpiod
GATE_GPIO_PIN=17
GATE_PULSE_MS=1200
```

Relay module should be isolated and powered properly. Do not drive a heavy lock directly from GPIO.

## WebSocket Flow

Device sends:

```json
{ "type": "device.hello", "serial": "PI4-GATE-001", "ip": "192.168.1.50" }
```

Cloud replies:

```json
{ "type": "sync.snapshot", "users": [], "deletedUserIds": [], "commands": [] }
```

Scan event:

```json
{ "type": "scan.event", "scanId": "scan-1", "code": "1001", "inout": "IN" }
```

Cloud result:

```json
{ "type": "scan.result", "scanId": "scan-1", "result": { "valid": true, "openGate": true } }
```

## Offline Behavior

- Latest users are cached locally in SQLite: `pi4-hmi/data/hmi-cache.sqlite3`.
- If cloud is down, valid cached QR still opens the gate.
- Attendance events are queued in `pendingScans`.
- On reconnect, `scan.batch` syncs pending records to cloud/server DB.

## Kiosk Boot On Pi

1. Copy this folder to `/opt/pi4-node-kiosk`.
2. Copy `.env.example` to `.env` and set server IP, device serial, and gate mode.
3. Install PyQt dependencies:

```bash
sudo apt install python3-pyqt5 python3-pyqt5.qtwebsockets
```

The HMI intentionally starts with company splash + progress bar. When progress reaches 100%, home screen opens and scanner input stays focused.
