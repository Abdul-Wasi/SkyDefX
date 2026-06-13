# SkyDefX Developer & Project Guide

Welcome to the **SkyDefX** project! This document serves as a comprehensive guide for developers, engineers, and users. It details the project's goals, architecture, codebase structure, current progress, and step-by-step instructions on how to set up, run, and extend the system.

---

## 1. Project Overview & Goal

**SkyDefX** is an intelligent, real-time drone detection and radar visualization system. The primary goal of the project is to provide a security interface that captures video feeds (via a camera or webcam), runs real-time object detection models to identify drones, and maps their physical locations (azimuth, elevation, distance) onto a simulated radar screen.

### High-Level Capabilities:
*   **Secure Access Control**: A multi-step secure login system built into the desktop client.
*   **Computer Vision-Based Detection**: Real-time object detection using **YOLOv5** with a custom-trained model (`best.pt`) optimized for identifying drones.
*   **Persistent Tracking**: Intersection over Union (IoU) tracking of detected drones across consecutive frames to maintain consistent drone IDs.
*   **Real-Time Satellite Map Background**: Shows a live satellite view of the deployment area (using 100% free ArcGIS Esri World Imagery tiles) as the background of the radar scope.
*   **Interactive Click-and-Drag Panning**: Click and drag anywhere inside the circular radar boundary to pan/scroll the map, which automatically updates the sidebar coordinates on the fly.
*   **Live Autocomplete Address Search**: Type an address, city, or building name in the search box to see up to 5 real-time geocoded suggestions (using Nominatim). Clicking a suggestion centers the map instantly.
*   **GPS Live Location Sync**: A button to automatically detect current coordinates (Latitude/Longitude) using Windows Location Services (or public IP Geolocation fallbacks if permission is blocked), instantly applying them to center the map.
*   **Dynamic Range Scaling**: Visual target coordinates and range rings adjust dynamically when zooming or changing locations, maintaining coordinate physical accuracy.
*   **Intrusion Warning System**: Custom restricted areas can be drawn on the camera feed to sound an alert/warning if a drone crosses the boundary.
*   **Mock Simulation**: A test node that generates simulated drone coordinates to test the UI and networking independent of hardware/models.

---

## 2. System Architecture

The project is structured as a client-server architecture split between **Java** (Frontend Dashboard & Radar Renderer) and **Python** (YOLOv5 Detection Server & Telemetry Transmitter).

### Architecture Diagram
```mermaid
graph TD
    subgraph Python Backend (Sensor Node)
        Cam[Camera / Webcam Feed] --> DetectionScript[Advanced_Drone_Detection.py]
        Weights[(best.pt YOLOv5 Model)] --> DetectionScript
        DetectionScript -->|Process Bounding Box| Track[IoU Tracking & Persistent IDs]
        Track -->|Convert to Polar Coordinates| Telemetry[Calculate Azimuth, Elevation, Distance]
        Telemetry -->|TCP Socket Connection| SocketClient[Python TCP Socket Client]
    end

    subgraph Java Frontend (Dashboard)
        SocketServer[Java Socket Server - Port 12345] -->|Receive JSON Telemetry| Dashboard[MainControlDashboard]
        Dashboard -->|Update UI Data| Radar[RadarPanel]
        Radar -->|Stitch & Render Map| MapManager[SatelliteMapManager]
        MapManager -->|Fetch Tiles| Esri[Esri World Imagery Tiles]
        Radar -->|Render Circular Scope| Sweep[Visual Sweeper, Trails, Target Selection]
        UIFlow[Identity -> Passcode -> Boot Sequence] -->|On Startup Success| SocketServer
    end

    SocketClient -.->|JSON over Port 12345| SocketServer
```

---

## 3. Component Breakdown & Codebase Walkthrough

### 📂 Java Frontend Workspace: `c:\Users\bhata\Desktop\SkyDefX`

This is the UI dashboard written using Java Swing. It contains the following files in `src/`:

1.  **[SkyDefXRadarSimulator.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/SkyDefXRadarSimulator.java)**:
    *   The entry point and main window manager (`JFrame`) using `CardLayout`.
    *   Handles the initialization screens and transition callbacks.
    *   **Port `12345` Server**: Starts a background TCP socket server (`startServer()`) when the user successfully boots into the Main Dashboard. It listens for incoming connections from Python and updates the UI on the Event Dispatch Thread (EDT).
2.  **[IdentityScreenPanel.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/IdentityScreenPanel.java)**:
    *   The first screen prompting the user to enter their name (defaults to "Guest" if blank). Pressing Enter proceeds to the passcode screen.
3.  **[PasscodeScreenPanel.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/PasscodeScreenPanel.java)**:
    *   The authorization screen. Requires entering the passcode `1234`. Shows an animated green checkmark on success (`ACCESS GRANTED`) or red X on failure (`ACCESS DENIED`).
4.  **[InitializationSequencePanel.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/InitializationSequencePanel.java)**:
    *   Simulates a system boot sequence using a typewriter animation. 
    *   Triggers background startup audio, runs fake diagnostics, and prompts the user to press **ENTER** to access the dashboard when the boot completes.
5.  **[MainControlDashboard.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/MainControlDashboard.java)**:
    *   Layout organizer. Contains a **West panel for Radar Node Deployment** (search box, latitude, longitude, GPS Sync, zoom level, presets), a **Center panel for Radar Panel**, an **East panel for Target Tracking List**, and a **South panel for Console Logs**.
    *   **Live Autocomplete Address Search**: Features a geocoding search input that displays up to 5 real-time geocoded address suggestions (queried from OpenStreetMap's Nominatim API) inside a popup menu as you type. Selecting an option centers the map.
    *   **GPS Sync**: Contains the **SYNC LIVE LOCATION [GPS]** feature which first queries native Windows `GeoCoordinateWatcher` (via `.cache/get_location.ps1` script), and automatically falls back to IP-based APIs (`ipapi.co` -> `ip-api.com` -> `freeipapi.com`) if OS permission is disabled.
6.  **[RadarPanel.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/RadarPanel.java)**:
    *   The core renderer. Draws the circular radar scope overlaying the satellite map.
    *   Clips the satellite tiles to a circular area inside the scope.
    *   **Click-and-Drag Panning**: Installs mouse listeners that capture drag movements, convert pixel offsets to geographical coordinates, shift the map center, and update the sidebar coordinates.
    *   Generates a sweeping radar beam with a translucent afterglow trail.
    *   Transforms incoming Polar coordinate data (azimuth, elevation, distance) from the Python socket into physical displacement meters (East/North) from the radar center.
    *   Draws physical scale range rings (e.g. 500m, 1km, 1.5km) dynamically matching the map's current zoom level.
    *   Manages the lifecycle of objects: renders path trails, highlights selected targets (orange border), and expires targets if not updated within 5 seconds.
7.  **[SatelliteMapManager.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/SatelliteMapManager.java)**:
    *   Handles tile-based Web Mercator projection, mapping latitude and longitude to $(X, Y)$ tiles at a specified zoom level.
    *   Fetches tiles asynchronously from **Esri World Imagery** (`https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`).
    *   Caches tiles locally in a `.cache/map_tiles` folder inside the workspace.
    *   Triggers UI repaints on the radar panel as soon as tiles are downloaded and ready.
8.  **[AirborneObject.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/AirborneObject.java)**:
    *   Represents a tracked drone inside the Java visualizer. Stores physical offset coordinates (in meters) and historical path trails (in meters), allowing target rendering to scale perfectly when the user zooms in or out.
9.  **[SoundPlayer.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/SoundPlayer.java)**:
    *   A utility player that plays WAV sound effects (`system_startup.wav`, `system_online_beep.wav`) asynchronously in background threads.
10. **[DroneDetectionClient.java](file:///c:/Users/bhata/Desktop/SkyDefX/src/DroneDetectionClient.java)**:
    *   *Legacy Client Script*: Originally designed to connect Java *to* Python, but modified to run in reverse where Java acts as the server listening for Python connections for robustness.

---

### 📂 Python Backend Workspace: `c:\Users\bhata\Desktop\Drone_detector_python`

This is the detection script using PyTorch, YOLOv5, and OpenCV.

1.  **[Advanced_Drone_Detection.py](file:///c:/Users/bhata/Desktop/Drone_detector_python/Advanced_Drone_Detection.py)**:
    *   **Model Loading**: Loads the custom YOLOv5 model weight file `best.pt`.
    *   **Webcam Source**: Captures video frames from Camera ID `0`.
    *   **Persistent Tracking**: Runs IoU calculations. If a detected bounding box overlaps significantly with a tracked box from a previous frame, it retains the existing drone ID (e.g. `DRONE_1000`); otherwise, it registers a new ID.
    *   **Virtual Fencing**: Allows users to click and draw a rectangle (restricted area) on the camera window. If a drone's center crosses into this rectangle, a visual red warning is printed on screen and console.
    *   **Telemetry Calculation**:
        *   **Azimuth**: Derived from the horizontal position of the target box center relative to the video frame width, scaled by the camera's Horizontal Field of View (HFOV = 90°).
        *   **Elevation**: Derived from the vertical position of the target box center relative to the frame height, scaled by the camera's Vertical Field of View (VFOV = 60°).
        *   **Distance**: Estimated based on the bounding box height. Drones that appear smaller (smaller bounding box height) are mapped as further away, while larger boxes are closer.
    *   **TCP socket client**: Connects to the Java server running on `127.0.0.1:12345` and streams coordinates continuously.
2.  **[mock_sensor_node.py](file:///c:/Users/bhata/Desktop/Drone_detector_python/mock_sensor_node.py)**:
    *   A simulation node that generates mock coordinates representing a drone flying in a circular orbit with slight altitude/distance wobbles. Ideal for GUI testing without camera setups or PyTorch dependencies.
3.  **[test_camera.py](file:///c:/Users/bhata/Desktop/Drone_detector_python/test_camera.py)**:
    *   A simple diagnostics script to verify webcam access and OpenCV functionality.

---

## 4. Communication Protocol

The data transfer between Python (client) and Java (server) uses standard TCP socket streaming.

### Connection Parameters:
*   **Host**: `127.0.0.1` (localhost)
*   **Port**: `12345`
*   **Encoding**: UTF-8 String, newline-terminated (`\n`)

### JSON Telemetry Packet Format:
Every update is sent as a JSON object on a single line:
```json
{
  "id": "DRONE_1000",
  "azimuth": 245.50,
  "elevation": 12.30,
  "distance": 1420.00
}
```

---

## 5. Setting Up and Running the Project

Follow these steps to configure your environment and run the application.

### Step 1: Prepare the Python Virtual Environment
Navigate to the Python directory and install dependencies:
1. Open a terminal and change directory to the Python folder:
   ```bash
   cd c:\Users\bhata\Desktop\Drone_detector_python
   ```
2. Activate the pre-configured virtual environment:
   ```bash
   .venv\Scripts\activate
   ```
3. Install required packages:
   ```bash
   pip install torch opencv-python numpy pillow pandas matplotlib
   ```

### Step 2: Run the Java Dashboard
1. Open the project folder `c:\Users\bhata\Desktop\SkyDefX` in VS Code.
2. Compile the project files:
   ```bash
   javac -cp "lib/*" -d bin src/*.java
   ```
3. Launch the simulator:
   ```bash
   java -cp "bin;lib/*" SkyDefXRadarSimulator
   ```
4. Perform the startup sequence:
   * **Identify Screen**: Type a name (e.g. `Commander`) and press **Enter**.
   * **Passcode Screen**: Type `1234` and press **Enter**.
   * **Diagnostics Screen**: Wait for the typing sequence to complete. Once `Please Proceed...` appears, press **Enter**.
   * The application switches to the dashboard, and a server starts listening on port `12345`.
5. Locate your position:
   * **Option A (Interactive Drag)**: Click and drag anywhere inside the circular radar map to slide/scroll the map and update coordinate text boxes dynamically.
   * **Option B (Autocomplete Address Search)**: Type a name/address (e.g., *"Burj Khalifa"*, *"Empire State Building"*) in the search field, wait for the suggestion dropdown to appear, and select an option.
   * **Option C (OS GPS Access)**: Turn on Windows Location Services in your OS settings and click **SYNC LIVE LOCATION [GPS]** to capture your device's exact coordinates.
   * **Option D**: Use the preset drop-down or enter decimals manually in the Latitude/Longitude boxes and click **DEPLOY RADAR NODE**.
6. Use the **ZOOM +** and **ZOOM -** buttons to adjust scale.

### Step 3: Run the Detection Backend (Choose A or B)

#### Option A: Running the Mock Simulator (Testing Dashboard UI)
To test the visualizer without launching cameras or models, run:
```bash
python mock_sensor_node.py
```
You will immediately see target telemetry logged in the Python terminal, and an object labeled `MOCK_TARGET_01` orbiting the radar panel on top of the satellite map in the Java GUI.

#### Option B: Running the Real-Time YOLOv5 Webcam Detector
To run the full camera and object detection pipeline:
1. Ensure your webcam is connected.
2. Run the main detection script:
   ```bash
   python Advanced_Drone_Detection.py
   ```
3. If a drone is detected, its calculated distance and coordinates will immediately appear on the Java radar screen.
