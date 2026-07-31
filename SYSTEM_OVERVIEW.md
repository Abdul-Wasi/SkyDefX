# SkyDefX: Integrated System Overview & File Structure

This document provides a comprehensive summary of the file structure and system architecture for the **SkyDefX Drone Detection & Tracking System**. 

The project is divided into three physical environments:
1. **Java Radar Console (Laptop Dashboard)**
2. **Laptop YOLO Python Workspace (Computer Vision & Servo Control)**
3. **Raspberry Pi Hardware Node (Sensors & Motor Driver)**

---

## 1. Java Radar Console (`c:\Users\bhata\Desktop\SkyDefX`)
This workspace runs the primary tactical control center. It is written in Java Swing and renders the circular radar scope overlaid on top of dynamic satellite map imagery.

### 📂 Source Files (`src/`)
* **[SkyDefXRadarSimulator.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/SkyDefXRadarSimulator.java)**: The main entry point. Sets up the window manager, manages user login screens, and initializes the cached thread pool servers for telemetry (port `12345`) and MJPEG video feed (port `12346`).
* **[MainControlDashboard.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/MainControlDashboard.java)**: Coordinates the dashboard UI. Features the threat assessment sidebar, the selected target kinematics card, the system console log, the active target list table, and a collapsible Node Configuration settings drawer (hidden by default).
* **[RadarPanel.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/RadarPanel.java)**: The core renderer. Generates the sweeping beam and afterglow trail, calculates target positions, handles clicks to select targets, manages the threat warning system (1500m/2500m rings), and runs the **Spatial Gating Sensor Fusion** logic.
* **[AirborneObject.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/AirborneObject.java)**: Represents a target track. Maintains physical coordinates, velocity, heading, historical trail, and sensor fusion flags (retains cyan/magenta indicator ring details).
* **[SatelliteMapManager.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/SatelliteMapManager.java)**: Asynchronously fetches, projects, and tiles ArcGiS Esri satellite maps for the radar background. Caches map tiles locally under `.cache/map_tiles/`.
* **[IdentityScreenPanel.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/IdentityScreenPanel.java)**: Login screen requesting operator credentials.
* **[PasscodeScreenPanel.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/PasscodeScreenPanel.java)**: Security authorization screen (requires passcode `1234`).
* **[InitializationSequencePanel.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/InitializationSequencePanel.java)**: Tactical boot-up diagnostic screen with a green green typing output effect.
* **[SoundPlayer.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/SoundPlayer.java)**: Plays system boot-up and online beep WAV sounds in background threads.
* **[DroneDetectionClient.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/DroneDetectionClient.java)**: *Legacy Script*. Originally designed to connect from Java to Python (now Java operates as the server for robustness).

### 📂 Other Directories
* **`bin/`**: Holds compiled `.class` bytecodes.
* **`lib/`**: Contains external jar libraries (e.g., `org.json` parser).
* **`.cache/`**: Local cache folder storing downloaded ESRI satellite map tiles and OS geocoding details.

---

## 2. Laptop YOLO Python Workspace (`c:\Users\bhata\Desktop\Drone_detector_python`)
This workspace runs the object detection and servo-command loop on the laptop. It captures the camera feed, executes YOLOv5, sends servo commands, and streams telemetry and video to the Java dashboard.

### 🚀 Active Files in Root
* **[Advanced_Drone_Detection.py](file:///c:/Users/bhata/Desktop/Drone_detector_python/Advanced_Drone_Detection.py)**: The main script. Runs YOLOv5 inference (`best.pt`), runs the **closed-loop tracking tracking mount**, sends UDP pan commands to the Pi, draws HUD overlays on the camera window, and connects to the Java server (port 12345 for telemetry, port 12346 for video).
* **[mock_sensor_node.py](file:///c:/Users/bhata/Desktop/Drone_detector_python/mock_sensor_node.py)**: Telemetry simulator. Generates a mock drone orbiting the radar station. Excellent for testing the Java Dashboard and mapping without requiring physical hardware or a camera.
* **[upload_to_pi.py](file:///c:/Users/bhata/Desktop/Drone_detector_python/upload_to_pi.py)**: Deployment utility. Connects to the Pi via SSH/SFTP and copies active hardware scripts to `/home/pi/`.
* **[skydef_fusion.py](file:///c:/Users/bhata/Desktop/Drone_detector_python/skydef_fusion.py)**: Local reference copy of the Pi's active hardware node script.
* **[servo_config.json](file:///c:/Users/bhata/Desktop/Drone_detector_python/servo_config.json)**: Local copy of the servo hardware config.
* **[best.pt](file:///c:/Users/bhata/Desktop/Drone_detector_python/best.pt)**: The custom PyTorch weights file for YOLOv5 trained to detect drones.
* **`.venv/`**: Local virtual environment containing PyTorch, OpenCV, Paramiko, and serial COM libraries.

### 🗃️ Archived Test Scripts (`archive_tests/`)
All diagnostic, calibration, and single-sensor check scripts have been archived here to keep the workspace clean:
* `easy_servo_limits.py` & `fix_servo_limits.py`: Servo limit checking and deployment code.
* `servo_manual_calibration.py` & `servo_center.py`: Calibration assistants.
* `diagnose_pi.py`: Pi diagnostic script.
* `e2e_servo_test.py` & `test_physical_servo.py` & `test_pi_servo_network.py`: Servo connection checkers.
* `test_camera.py`: OpenCV webcam verifier.
* `stop_servo.py`: Quick servo halt script.
* `download_fusion.py`: Pi backup helper.

---

## 3. Raspberry Pi Hardware Node (`/home/pi/`)
This environment runs directly on the Raspberry Pi mounted to the sensor tripod/base. It reads sensors, commands the servo, and connects to the Java Radar.

### 🚀 Active Files in `/home/pi/`
* **`skydef_fusion.py`**: The primary hardware daemon. Runs three concurrent threads:
  1. *LiDAR Thread*: Scans RPLiDAR on `/dev/ttyUSB0` at 256000 baud, filters out distant readings, and streams points to the Java Radar.
  2. *Ultrasonic Thread*: Measures distance using the HC-SR04 ultrasonic sensor.
  3. *Servo Listener Thread*: Listens on UDP port `12347` for angle commands. Calculates budget and enforces the strict **3.0-second limits (135° left/right from center)** to protect wiring.
  4. *Main Loop*: Connects over TCP to the laptop (port 12345) and streams LiDAR (`LIDAR_TGT_xx`) and ultrasonic (`ULTRA_TGT_01`) measurements.
* **`servo_config.json`**: JSON settings defining the active GPIO pin (`18`), servo type (`continuous`), pulse width settings (`1000us` min, `2000us` max, `1500us` neutral), and travel limits.
* **`pi_servo_daemon.py`**: Interactive calibration tool that allows operators to calibrate continuous servo creep and save settings.
* **`easy_servo_limits.py`**: Stepper limit finder.
* **`best.pt`**: Backup YOLOv5 model weights.
* **`skydef_env/`**: Pi's virtual environment containing Python packages (`RPi.GPIO`, `pigpio`, `rplidar`, `gpiozero`).

### 🗃️ Archived Test Scripts (`archive_tests/`)
Dozens of diagnostic test scripts used during sensor assembly have been cleaned up and moved into this backup folder:
* `pi_test_ultrasonic_raw.py` & `pi_test_ultrasonic_interactive.py`: HC-SR04 test scripts.
* `lidar_dashboard_streamer.py`, `lidar_edge_node.py`, `lidar_test.py`, `test_lidar.py`: RPLiDAR diagnostic scripts.
* `pi_test_servo_raw.py`, `test_gpiozero_servo.py`, `test_positional_raw.py`, `test_servo.py`, `test_servo_sweep.py`: Servo driver tests.
* `pi_diagnostic_test.py` & `test_sensors.py`: Overall board diagnostic checkers.
* `pi_test_camera_opencv.py` & `pi_test_yolo.py`: Local PyTorch/OpenCV webcam tests.
* `servo_center.py` & `servo_center.json` & `servo_e2e_test.py` & `servo_manual_calibration.py`: Initial calibration scripts.
* `pi_test_port.py`: Socket listener check.

---

## 🔄 Live System Data Flow
```mermaid
sequenceDiagram
    participant Pi as Raspberry Pi Node
    participant LaptopCV as Laptop YOLO Script
    participant Java as Java Radar Dashboard

    Note over Pi, LaptopCV: 1. Sweeping Airspace (Patrol Mode)
    LaptopCV->>Pi: Send alternating left/right UDP sweep commands (Port 12347)
    Pi->>Pi: Rotate servo (Capped by 135° safety limits)

    Note over LaptopCV, Java: 2. Drone Detected
    LaptopCV->>LaptopCV: YOLOv5 detects drone on frame
    LaptopCV->>Pi: Send UDP angle command to center drone
    Pi->>Pi: Rotate servo to lock and track target bearing
    LaptopCV->>Java: Stream Target Telemetry (DRONE_1000, bearing, estimated distance) (Port 12345)
    LaptopCV->>Java: Stream Annotated Video Feed (Port 12346)

    Note over Pi, Java: 3. Sensor Fusion
    Pi->>Java: Stream LiDAR reflections (LIDAR_TGT_xx) and Ultrasonic (ULTRA_TGT_01) (Port 12345)
    Java->>Java: Spatial Gating: Correlate DRONE_1000 with LIDAR/Ultrasonic bearing (within ±6°)
    Java->>Java: Overwrite estimated distance with precise sensor measurement
    Java->>Java: Hide raw sensor dots; Render Cyan/Magenta fusion ring on Radar
    Java->>Java: Update Sidebar: Source: LIDAR [FUSED]
```
