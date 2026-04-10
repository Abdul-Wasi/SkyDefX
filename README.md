# SkyDefX 🛡️

**A real-time airborne object detection and radar visualization system — Java GUI meets Python sensor simulation.**

> Repo → [github.com/Abdul-Wasi/SkyDefX](https://github.com/Abdul-Wasi/SkyDefX)

---

## Overview

SkyDefX is a desktop radar simulation system with a cinematic boot sequence, passcode-gated access, and a live radar display that visualizes airborne objects detected by a connected Python sensor script over a TCP socket.

The Java application acts as a **TCP server**. A Python script acts as the **sensor client**, sending JSON detection payloads (azimuth, elevation, distance) in real time. The radar panel converts polar coordinates to screen-space Cartesian positions, renders a sweeping beam with afterglow, draws object trails, and expires stale detections automatically.

---

## System Architecture

```
Python Sensor Script  ──── TCP (port 12345) ────►  Java Radar Server
  (sends JSON)                                        (SkyDefXRadarSimulator)
                                                            │
                                                            ▼
                                                     MainControlDashboard
                                                            │
                                                            ▼
                                                       RadarPanel
                                                    (renders objects,
                                                     beam, trails)
```

### Data Flow
1. Python connects to Java on `localhost:12345`.
2. Python sends newline-delimited JSON: `{"id": "drone_01", "azimuth": 45.0, "elevation": 15.0, "distance": 1200.0}`
3. Java parses each JSON line, converts polar → Cartesian using the radar panel's pixel-per-meter scale, and updates or creates an `AirborneObject`.
4. The Swing `Timer` (20ms) sweeps the beam, manages object lifecycle, and repaints.

---

## Features

### 🚀 Boot Sequence
- **Identity Screen** — Prompts for user identification before proceeding.
- **Passcode Screen** — 4-digit authorization code with ACCESS GRANTED / DENIED feedback.
- **Initialization Sequence** — Typewriter-style terminal animation with synchronized sound effects (`system_startup.wav`).
- **Enter to Proceed** — Manual confirmation before the radar goes live.

### 📡 Radar Panel
- Rotating sweep beam with **multi-frame afterglow** (50-frame history, alpha-faded).
- Detected objects rendered as colored squares: **yellow** when freshly detected, **red** as detection ages.
- Per-object **movement trails** (up to 30 positions) drawn with decreasing opacity.
- **"DETECTED!" overlay** with a dashed line from center to object, visible for 1 second after detection.
- Click any object to **select/target** it — highlighted in orange with log entry.
- Objects automatically **expire** after 5 seconds without a new Python update.
- Coordinate system: radar 0° (North) maps to screen top; azimuth increases clockwise.

### 📊 Dashboard
- **Detected Objects Panel** — Live table showing ID, relative X/Y, altitude, and status (ACTIVE / DETECTED / LAST SEEN / TARGETED).
- **System Log** — Timestamped event log: new detections, expirations, selections, errors.

### 🔊 Sound
- `system_startup.wav` — plays during the initialization typewriter sequence.
- `system_online_beep.wav` — plays on ENTER to transition to the radar dashboard.
- Graceful stop/cleanup on window close.

---

## Tech Stack

| Component | Technology |
|---|---|
| GUI Framework | Java Swing (`JFrame`, `JPanel`, `CardLayout`) |
| Animation | `javax.swing.Timer` (20ms repaint loop) |
| Rendering | `Graphics2D` with antialiasing, alpha compositing |
| Networking | `ServerSocket` / `Socket` (TCP, port 12345) |
| JSON Parsing | `org.json` |
| Audio | `javax.sound.sampled` |
| Concurrency | `ExecutorService`, `ConcurrentHashMap`, `CopyOnWriteArrayList` |
| Sensor Client | Python (connects as TCP client, sends JSON detections) |

---

## Project Structure

```
src/
├── SkyDefXRadarSimulator.java     # Main JFrame, CardLayout, screen orchestration, TCP server
├── MainControlDashboard.java      # Dashboard layout, connects radar panel to UI
├── RadarPanel.java                # Core rendering: beam, objects, trails, lifecycle
├── AirborneObject.java            # Object model: position, trail, detection state
├── IdentityScreenPanel.java       # Boot screen 1: identity input
├── PasscodeScreenPanel.java       # Boot screen 2: 4-digit passcode
├── InitializationSequencePanel.java # Boot screen 3: typewriter terminal
├── DroneDetectionClient.java      # (Legacy) TCP client — replaced by server model
└── SoundPlayer.java               # Audio playback with dedicated startup clip control

res/sounds/
├── system_startup.wav
└── system_online_beep.wav
```

---

## Getting Started

### Prerequisites
- Java 11+
- `org.json` JAR on the classpath ([download](https://mvnrepository.com/artifact/org.json/json))
- Python 3.x (for the sensor script)

### Run

1. Compile all `.java` files with `org.json` on the classpath:
```bash
javac -cp .:lib/json.jar src/*.java -d bin/
```

2. Run the Java radar server:
```bash
java -cp bin:lib/json.jar SkyDefXRadarSimulator
```

3. Run your Python sensor script (must connect to `localhost:12345` and send JSON lines):
```python
import socket, json, time, math, random

sock = socket.socket()
sock.connect(('localhost', 12345))

azimuth = 0
while True:
    payload = {
        "id": "drone_01",
        "azimuth": azimuth % 360,
        "elevation": 15.0,
        "distance": 1500 + random.uniform(-100, 100)
    }
    sock.sendall((json.dumps(payload) + '\n').encode())
    azimuth += 5
    time.sleep(0.1)
```

### Passcode
Default authorization code: **`1234`**

---

## Coordinate System

| Radar Concept | Screen Mapping |
|---|---|
| Azimuth 0° (North) | Top of panel |
| Azimuth 90° (East) | Right of panel |
| Azimuth 180° (South) | Bottom of panel |
| Distance (meters) | Scaled by `panelRadius / MAX_RANGE` (default 5000m max) |
| Altitude | `distance × sin(elevation)` |

---

## Author

**Abdul Wasi** — [abdulwasi.site](https://abdulwasi.site) · [LinkedIn](https://linkedin.com/in/abdulwasibhat) · [GitHub](https://github.com/Abdul-Wasi)

*B.Tech CSE, Islamic University of Science and Technology
