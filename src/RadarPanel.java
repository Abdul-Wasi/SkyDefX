// RadarPanel.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import org.json.JSONObject;

class RadarPanel extends JPanel implements ActionListener {

    private double beamAngle = 0;
    private Timer timer;
    private Map<String, AirborneObject> detectedObjectsMap;
    private List<AirborneObject> activeObjectsList;
    private JTextArea detectedObjectsListArea;
    private JTextArea systemLogArea;

    private AirborneObject selectedObject;

    private static final long DETECTION_DISPLAY_DURATION = 1000;
    private static final long OBJECT_EXPIRATION_TIME = 5000;
    private static final double MAX_RADAR_RANGE_METERS = 5000.0;

    private LinkedList<Double> beamAngleHistory;
    private static final int MAX_BEAM_HISTORY_SIZE = 50;

    private double pixelsPerMeter; 
    private SatelliteMapManager mapManager;

    private Point dragStartPoint;
    private boolean isPanning = false;
    private java.util.function.BiConsumer<Double, Double> onCoordinatesMoved;

    // Tactical Threat Assessment Core Fields
    private MainControlDashboard dashboard;
    private String currentThreatLevel = "GREEN";
    private long lastBeepTime = 0;
    private java.util.Set<String> acknowledgedAlerts = ConcurrentHashMap.newKeySet(); // Set of acknowledged target IDs

    // --- Constructor ---
    public RadarPanel(MainControlDashboard dashboard, JTextArea detectedObjectsListArea, JTextArea systemLogArea) {
        this.dashboard = dashboard;
        this.detectedObjectsListArea = detectedObjectsListArea;
        this.systemLogArea = systemLogArea;

        setBackground(new Color(10, 10, 10));
        detectedObjectsMap = new ConcurrentHashMap<>();
        activeObjectsList = new CopyOnWriteArrayList<>();
        selectedObject = null;
        beamAngleHistory = new LinkedList<>();

        // Initialize Satellite Map Manager (triggers repaint when tiles load)
        this.mapManager = new SatelliteMapManager(this::repaint);

        timer = new Timer(20, this);
        timer.start();

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int centerX = getWidth() / 2;
                int centerY = getHeight() / 2;
                int radius = Math.min(centerX, centerY) - 20;
                double dist = e.getPoint().distance(centerX, centerY);
                if (dist <= radius) {
                    dragStartPoint = e.getPoint();
                    isPanning = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPanning = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (isPanning && dragStartPoint != null) {
                    int dx = e.getX() - dragStartPoint.x;
                    int dy = e.getY() - dragStartPoint.y;
                    
                    if (dx != 0 || dy != 0) {
                        double latRad = Math.toRadians(mapManager.getCenterLat());
                        double zScale = 1 << mapManager.getZoom();
                        double tileX = (mapManager.getCenterLon() + 180.0) / 360.0 * zScale;
                        double tileY = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * zScale;

                        double newTileX = tileX - (dx / 256.0);
                        double newTileY = tileY - (dy / 256.0);

                        double newLon = newTileX / zScale * 360.0 - 180.0;
                        double n = Math.PI - (2.0 * Math.PI * newTileY) / zScale;
                        double newLat = Math.toDegrees(Math.atan(Math.sinh(n)));

                        mapManager.setCenter(newLat, newLon);

                        if (onCoordinatesMoved != null) {
                            onCoordinatesMoved.accept(newLat, newLon);
                        }

                        dragStartPoint = e.getPoint();
                    }
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        appendLog("System initialized. Satellite Radar online. Threat Assessment Core activated. Waiting for detections...");
    }

    public void setOnCoordinatesMoved(java.util.function.BiConsumer<Double, Double> callback) {
        this.onCoordinatesMoved = callback;
    }

    public SatelliteMapManager getMapManager() {
        return mapManager;
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            systemLogArea.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - " + message + "\n");
            systemLogArea.setCaretPosition(systemLogArea.getDocument().getLength());
        });
    }

    public void acknowledgeSelectedTargetAlert() {
        if (selectedObject != null) {
            String id = selectedObject.getObjectID();
            acknowledgedAlerts.add(id);
            appendLog("ALERT ACKNOWLEDGED: Silenced warning alarms for target " + id);
            updateThreatSystemAndUI();
        }
    }

    private void updateDetectedObjectsList() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-12s %-10s %-10s %-8s %-10s %-12s\n", "ID", "EAST (m)", "NORTH (m)", "ALT (m)", "STATUS", "SOURCE"));
            sb.append("------------------------------------------------------------------------\n");

            boolean detectedAny = false;
            long currentTime = System.currentTimeMillis();

            for (AirborneObject obj : activeObjectsList) {
                if (obj.isMergedIntoDrone()) {
                    continue; // Skip rendering merged raw sensor targets in the table
                }
                // Spatial Gating Filter: Only show raw LiDAR targets if they fall within 6 degrees of an active drone target.
                // Only show raw Ultrasonic targets if they are within close range (< 3.0 meters).
                if (obj.getObjectID().startsWith("LIDAR_TGT_")) {
                    if (!isWithinGatedSlice(obj)) {
                        continue;
                    }
                } else if (obj.getObjectID().startsWith("ULTRA_TGT_")) {
                    if (obj.getDistance() >= 3.0) {
                        continue;
                    }
                }
                if (currentTime - obj.getLastDetectionTimestamp() < OBJECT_EXPIRATION_TIME) {
                    detectedAny = true;
                    int relativeE = (int) obj.getRelX();
                    int relativeN = (int) -obj.getRelY(); // Convert South to North

                    String status = "ACTIVE";
                    if (obj == selectedObject) {
                        status = "TARGETED";
                    } else if (obj.isCurrentlyDetectedByBeam()) {
                        status = "DETECTED";
                    } else if (currentTime - obj.getLastDetectionTimestamp() < AirborneObject.DETECTION_FADE_DURATION) {
                         status = "LAST SEEN";
                    }

                    sb.append(String.format("%-12s %-10d %-10d %-8d %-10s %-12s\n",
                                    obj.getObjectID(), relativeE, relativeN, obj.getAltitude(), status, obj.getFusionSource()));
                }
            }

            if (!detectedAny) {
                sb.append("No active detections.\n");
            }

            detectedObjectsListArea.setText(sb.toString());
        });
    }

    private void handleClick(int mouseX, int mouseY) {
        AirborneObject clickedOn = null;
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int radius = Math.min(centerX, centerY) - 20;

        double mpp = mapManager.getMetersPerPixel();
        pixelsPerMeter = 1.0 / mpp;

        for (AirborneObject obj : activeObjectsList) {
            double screenX = centerX + obj.getRelX() * pixelsPerMeter - obj.getSize() / 2.0;
            double screenY = centerY + obj.getRelY() * pixelsPerMeter - obj.getSize() / 2.0;
            double distanceToCenter = Math.sqrt(Math.pow(screenX + obj.getSize()/2.0 - centerX, 2) + 
                                                Math.pow(screenY + obj.getSize()/2.0 - centerY, 2));

            if (distanceToCenter <= radius &&
                mouseX >= screenX && mouseX <= screenX + obj.getSize() &&
                mouseY >= screenY && mouseY <= screenY + obj.getSize()) {
                clickedOn = obj;
                break;
            }
        }

        if (clickedOn != null) {
            if (clickedOn == selectedObject) {
                selectedObject = null;
                appendLog("Object deselected: " + clickedOn.getObjectID());
            } else {
                selectedObject = clickedOn;
                appendLog("Object selected: " + selectedObject.getObjectID() +
                                " at E:" + (int)selectedObject.getRelX() + "m, N:" + (int)-selectedObject.getRelY() + "m | Alt:" + selectedObject.getAltitude() + "m");
            }
        } else {
            if (selectedObject != null) {
                appendLog("Clicked on empty radar space. Deselecting object: " + selectedObject.getObjectID());
                selectedObject = null;
            } else {
                appendLog("Clicked on empty radar space.");
            }
        }
        repaint();
        updateDetectedObjectsList();
    }

    public AirborneObject getSelectedObject() {
        return selectedObject;
    }

    // --- handlePythonDetection Method ---
    // This is the primary entry point for Python-originated detection data

    public void handlePythonDetection(JSONObject droneData) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        double mpp = mapManager.getMetersPerPixel();
        pixelsPerMeter = 1.0 / mpp;

        try {
            String objectID = droneData.getString("id");
            double azimuthDeg = droneData.getDouble("azimuth");
            double elevationDeg = droneData.getDouble("elevation");
            double distanceMeters = droneData.getDouble("distance");

            String fusionSource = "CAMERA";

            // If it is a camera drone, check for active sensor targets to correlate and fuse
            if (objectID.startsWith("DRONE_")) {
                long now = System.currentTimeMillis();
                
                // 1. Look for closest active LiDAR target within ±6° azimuth
                double minLidarDiff = Double.MAX_VALUE;
                AirborneObject closestLidar = null;

                for (AirborneObject other : activeObjectsList) {
                    // Check if it is an active LiDAR target (updated within 1.5s)
                    if (now - other.getLastDetectionTimestamp() < 1500 
                            && other.getObjectID().startsWith("LIDAR_TGT_")) {
                        
                        // Calculate bearing from center
                        double otherBearing = Math.toDegrees(Math.atan2(other.getRelX(), -other.getRelY()));
                        otherBearing = (otherBearing + 360) % 360;
                        
                        double diff = Math.abs(azimuthDeg - otherBearing);
                        if (diff > 180) diff = 360 - diff;
                        
                        if (diff <= 6.0 && diff < minLidarDiff) {
                            minLidarDiff = diff;
                            closestLidar = other;
                        }
                    }
                }

                if (closestLidar != null) {
                    // Overwrite distance with precise LiDAR measurement
                    distanceMeters = closestLidar.getDistance();
                    fusionSource = "LIDAR";
                    closestLidar.setMergedIntoDrone(true);
                } else {
                    // 2. Look for active Ultrasonic target within ±10° azimuth AND close-range (< 3.0 meters)
                    AirborneObject ultraObj = detectedObjectsMap.get("ULTRA_TGT_01");
                    if (ultraObj != null && now - ultraObj.getLastDetectionTimestamp() < 1500 && ultraObj.getDistance() < 3.0) {
                        double ultraBearing = Math.toDegrees(Math.atan2(ultraObj.getRelX(), -ultraObj.getRelY()));
                        ultraBearing = (ultraBearing + 360) % 360;
                        
                        double diff = Math.abs(azimuthDeg - ultraBearing);
                        if (diff > 180) diff = 360 - diff;
                        
                        if (diff <= 10.0) {
                            // Overwrite distance with precise Ultrasonic measurement
                            distanceMeters = ultraObj.getDistance();
                            fusionSource = "ULTRASONIC";
                            ultraObj.setMergedIntoDrone(true);
                        }
                    }
                }
            } else {
                // If it is a sensor target, reset its merged state by default
                // It will be set to merged on the next camera processing frame if it correlates
                AirborneObject existing = detectedObjectsMap.get(objectID);
                if (existing != null) {
                    existing.setMergedIntoDrone(false);
                }
                if (objectID.startsWith("LIDAR_TGT_")) {
                    fusionSource = "LIDAR";
                } else if (objectID.startsWith("ULTRA_TGT_")) {
                    fusionSource = "ULTRASONIC";
                }
            }

            double azimuthRad = Math.toRadians(azimuthDeg);
            double elevationRad = Math.toRadians(elevationDeg);

            // Calculate physical displacement relative to radar center in meters
            // 0 degrees is North (negative Y in Java), clockwise
            double relX = distanceMeters * Math.cos(azimuthRad - Math.PI / 2);
            double relY = distanceMeters * Math.sin(azimuthRad - Math.PI / 2);

            int displayAltitude = (int) (distanceMeters * Math.sin(elevationRad));
            if (displayAltitude < 0) displayAltitude = 0;

            int objectSize = 10;

            AirborneObject obj = detectedObjectsMap.get(objectID);

            if (obj == null) {
                obj = new AirborneObject(objectID, relX, relY, displayAltitude, objectSize);
                obj.setFusionSource(fusionSource);
                detectedObjectsMap.put(objectID, obj);
                activeObjectsList.add(obj);
                appendLog("NEW DETECTED: ID " + objectID + String.format(" at E:%dm, N:%dm, Alt:%dm (Source: %s)",
                                (int)relX, (int)-relY, displayAltitude, fusionSource));
            } else {
                obj.setFusionSource(fusionSource);
                obj.updatePosition(relX, relY, displayAltitude);
            }
            obj.setCurrentlyDetectedByBeam(true);

        } catch (org.json.JSONException e) {
            appendLog("ERROR: Failed to parse drone detection JSON: " + e.getMessage());
        } catch (Exception e) {
            appendLog("ERROR in handlePythonDetection: " + e.getMessage());
            e.printStackTrace();
        }
        repaint();
    }

    // Spatial Gating check: returns true if the raw LiDAR target aligns within 6.0 degrees bearing of any actively tracked drone.
    private boolean isWithinGatedSlice(AirborneObject rawObj) {
        if (!rawObj.getObjectID().startsWith("LIDAR_TGT_")) {
            return true;
        }
        long now = System.currentTimeMillis();
        double rawBearing = Math.toDegrees(Math.atan2(rawObj.getRelX(), -rawObj.getRelY()));
        rawBearing = (rawBearing + 360) % 360;
        
        for (AirborneObject other : activeObjectsList) {
            if (other.getObjectID().startsWith("DRONE_") && (now - other.getLastDetectionTimestamp() < 2000)) {
                double droneBearing = Math.toDegrees(Math.atan2(other.getRelX(), -other.getRelY()));
                droneBearing = (droneBearing + 360) % 360;
                
                double diff = Math.abs(rawBearing - droneBearing);
                if (diff > 180) diff = 360 - diff;
                
                if (diff <= 6.0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int centerX = panelWidth / 2;
        int centerY = panelHeight / 2;
        int radius = Math.min(centerX, centerY) - 20;

        double mpp = mapManager.getMetersPerPixel();
        pixelsPerMeter = 1.0 / mpp;
        double maxRadarRange = radius / pixelsPerMeter;

        // Save original clip shape to restore later
        Shape originalClip = g2d.getClip();

        // 1. Draw Satellite Map restricted inside the circular radar boundary
        g2d.setClip(new java.awt.geom.Ellipse2D.Double(centerX - radius, centerY - radius, 2 * radius, 2 * radius));
        mapManager.drawMap(g2d, panelWidth, panelHeight);
        
        // Restore original clip so we can draw outer elements, labels and grid rings
        g2d.setClip(originalClip);

        // 2. Draw Translucent Radar Circular Scope Grid on top of the satellite map
        g2d.setColor(new Color(0, 160, 0, 100)); // Semi-transparent green
        g2d.setStroke(new BasicStroke(1));
        g2d.drawOval(centerX - radius, centerY - radius, 2 * radius, 2 * radius);
        
        // Draw crosshairs
        g2d.drawLine(centerX, centerY - radius, centerX, centerY + radius);
        g2d.drawLine(centerX - radius, centerY, centerX + radius, centerY);

        // Draw dynamic range rings & scale labels
        g2d.setFont(new Font("Monospaced", Font.BOLD, 11));
        for (int i = 1; i <= 4; i++) {
            int currentRadius = radius / 4 * i;
            g2d.setColor(new Color(0, 160, 0, 80));
            g2d.drawOval(centerX - currentRadius, centerY - currentRadius, 2 * currentRadius, 2 * currentRadius);
            
            // Draw range label on the rings
            double ringDist = maxRadarRange / 4.0 * i;
            String distText = String.format("%.0f m", ringDist);
            if (ringDist >= 1000) {
                distText = String.format("%.1f km", ringDist / 1000.0);
            }
            g2d.setColor(new Color(50, 255, 50, 180));
            g2d.drawString(distText, centerX + 5, centerY - currentRadius + 14);
        }

        // Draw Software-Defined Geofence Zones
        double warningRadius = 2500.0 * pixelsPerMeter;
        double restrictedRadius = 1500.0 * pixelsPerMeter;

        // 1. Warning Zone (Amber Dashed Circle)
        g2d.setColor(new Color(255, 165, 0, 20)); // Translucent amber fill
        g2d.fillOval((int)(centerX - warningRadius), (int)(centerY - warningRadius), (int)(2 * warningRadius), (int)(2 * warningRadius));
        
        g2d.setColor(new Color(255, 165, 0, 90)); // Amber dashed outline
        Stroke dashedAmber = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f, 4.0f}, 0.0f);
        g2d.setStroke(dashedAmber);
        g2d.drawOval((int)(centerX - warningRadius), (int)(centerY - warningRadius), (int)(2 * warningRadius), (int)(2 * warningRadius));

        // 2. Restricted Zone (Red Dashed Circle)
        g2d.setColor(new Color(255, 50, 50, 25)); // Translucent red fill
        g2d.fillOval((int)(centerX - restrictedRadius), (int)(centerY - restrictedRadius), (int)(2 * restrictedRadius), (int)(2 * restrictedRadius));
        
        g2d.setColor(new Color(255, 50, 50, 130)); // Red dashed outline
        Stroke dashedRed = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{8.0f, 4.0f}, 0.0f);
        g2d.setStroke(dashedRed);
        g2d.drawOval((int)(centerX - restrictedRadius), (int)(centerY - restrictedRadius), (int)(2 * restrictedRadius), (int)(2 * restrictedRadius));
        
        // Reset stroke
        g2d.setStroke(new BasicStroke(1));

        // Text Labels for Geofence
        g2d.setFont(new Font("Monospaced", Font.BOLD, 10));
        g2d.setColor(new Color(255, 165, 0, 150));
        g2d.drawString("WARNING ZONE (2500M)", (int)(centerX - warningRadius + 8), (int)(centerY - warningRadius + 14));
        g2d.setColor(new Color(255, 100, 100, 200));
        g2d.drawString("RESTRICTED ZONE (1500M)", (int)(centerX - restrictedRadius + 8), (int)(centerY - restrictedRadius + 14));

        // Draw the Radar Sweep Beam (semi-transparent green overlay)
        float alphaStepBeam = 1.0f / MAX_BEAM_HISTORY_SIZE;
        int currentBeamIndex = 0;
        for (Double historyAngle : beamAngleHistory) {
            float alpha = 1.0f - (currentBeamIndex * alphaStepBeam);
            if (alpha < 0.0f) alpha = 0.0f;

            g2d.setColor(new Color(0, 255, 0, (int)(alpha * 100)));
            g2d.setStroke(new BasicStroke(2.5f));

            int histBeamX = (int) (centerX + radius * Math.cos(historyAngle - Math.PI / 2));
            int histBeamY = (int) (centerY + radius * Math.sin(historyAngle - Math.PI / 2));
            g2d.drawLine(centerX, centerY, histBeamX, histBeamY);

            currentBeamIndex++;
        }

        // Current sweeping beam edge
        g2d.setColor(new Color(50, 255, 50, 200));
        g2d.setStroke(new BasicStroke(3));
        int beamX = (int) (centerX + radius * Math.cos(beamAngle - Math.PI / 2));
        int beamY = (int) (centerY + radius * Math.sin(beamAngle - Math.PI / 2));
        g2d.drawLine(centerX, centerY, beamX, beamY);

        // 4. Draw All Airborne Objects, Trails, and Metadata on top
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 12));
        long currentTime = System.currentTimeMillis();

        for (AirborneObject obj : activeObjectsList) {
            if (obj.isMergedIntoDrone()) {
                continue; // Skip rendering merged raw sensor targets on the screen
            }
            // Spatial Gating Filter: Only show raw LiDAR targets if they fall within 6 degrees of an active drone target.
            // Only show raw Ultrasonic targets if they are within close range (< 3.0 meters).
            if (obj.getObjectID().startsWith("LIDAR_TGT_")) {
                if (!isWithinGatedSlice(obj)) {
                    continue;
                }
            } else if (obj.getObjectID().startsWith("ULTRA_TGT_")) {
                if (obj.getDistance() >= 3.0) {
                    continue;
                }
            }
            // Check if object is within visible radar range or too old
            if (obj.getDistance() > maxRadarRange || (currentTime - obj.getLastDetectionTimestamp() > OBJECT_EXPIRATION_TIME)) {
                continue;
            }

            // Draw Object Trails (computed dynamically in meters)
            LinkedList<Point2D.Double> currentTrail = obj.getTrail();
            if (currentTrail.size() > 1) {
                float alphaStepTrail = 1.0f / (float) AirborneObject.MAX_TRAIL_LENGTH;

                for (int i = 0; i < currentTrail.size() - 1; i++) {
                    Point2D.Double p1 = currentTrail.get(i);
                    Point2D.Double p2 = currentTrail.get(i + 1);

                    float alpha = 1.0f - ((float) i * alphaStepTrail);
                    if (alpha < 0.0f) alpha = 0.0f;

                    g2d.setColor(new Color(255, 0, 0, (int)(alpha * 150)));
                    g2d.setStroke(new BasicStroke(1.5f));

                    int p1x = (int) (centerX + p1.x * pixelsPerMeter);
                    int p1y = (int) (centerY + p1.y * pixelsPerMeter);
                    int p2x = (int) (centerX + p2.x * pixelsPerMeter);
                    int p2y = (int) (centerY + p2.y * pixelsPerMeter);

                    g2d.drawLine(p1x, p1y, p2x, p2y);
                }
            }

            double screenX = centerX + obj.getRelX() * pixelsPerMeter - obj.getSize() / 2.0;
            double screenY = centerY + obj.getRelY() * pixelsPerMeter - obj.getSize() / 2.0;

            // Draw Heading Velocity Vector
            if (currentTrail.size() >= 2) {
                double speed = obj.getSpeed(pixelsPerMeter);
                if (speed > 0.5) { // Only draw vector line if target is moving
                    double headingDeg = obj.getHeading();
                    double headingRad = Math.toRadians(headingDeg);
                    
                    // Vector length scales with speed (e.g. 1.5 pixels per m/s)
                    double vectorLength = Math.max(15.0, Math.min(50.0, speed * 1.5));
                    
                    // Screen-space start and end coords
                    int startX = (int)(centerX + obj.getRelX() * pixelsPerMeter);
                    int startY = (int)(centerY + obj.getRelY() * pixelsPerMeter);
                    int endX = (int)(startX + vectorLength * Math.sin(headingRad));
                    int endY = (int)(startY - vectorLength * Math.cos(headingRad));
                    
                    // Draw vector line
                    g2d.setColor(new Color(50, 255, 50, 180)); // Semi-transparent green
                    g2d.setStroke(new BasicStroke(2.0f));
                    g2d.drawLine(startX, startY, endX, endY);
                    
                    // Draw vector arrowhead
                    double arrowAngle = Math.PI / 6; // 30 degrees arrowhead spread
                    int arrowSize = 6;
                    int xLeft = (int)(endX - arrowSize * Math.sin(headingRad - arrowAngle));
                    int yLeft = (int)(endY + arrowSize * Math.cos(headingRad - arrowAngle));
                    int xRight = (int)(endX - arrowSize * Math.sin(headingRad + arrowAngle));
                    int yRight = (int)(endY + arrowSize * Math.cos(headingRad + arrowAngle));
                    
                    g2d.drawLine(endX, endY, xLeft, yLeft);
                    g2d.drawLine(endX, endY, xRight, yRight);
                    g2d.setStroke(new BasicStroke(1.0f));
                }
            }

            // Orange highlight if selected

            if (obj == selectedObject) {
                g2d.setColor(new Color(255, 165, 0));
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRect((int) screenX - 2, (int) screenY - 2, obj.getSize() + 4, obj.getSize() + 4);
                g2d.setStroke(new BasicStroke(1));
            }

            // Draw object square (Yellow when updated, Red when fading)
            obj.draw(g2d, centerX, centerY, pixelsPerMeter);


            // If swept over, draw detection line and flashing text
            if (obj.isCurrentlyDetectedByBeam() && (currentTime - obj.getLastDetectionTimestamp() < DETECTION_DISPLAY_DURATION)) {
                g2d.setColor(new Color(255, 255, 0));
                g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f, 5.0f}, 0.0f));
                g2d.drawLine(centerX, centerY, (int)(centerX + obj.getRelX() * pixelsPerMeter), (int)(centerY + obj.getRelY() * pixelsPerMeter));
                g2d.drawString("DETECTED!", (int)screenX + obj.getSize() + 5, (int)screenY + obj.getSize()/2 + 5);
            }

            // Text metadata details
            g2d.setColor(new Color(220, 220, 220));
            String idAndCoords = String.format("ID: %s | E:%.0fm, N:%.0fm", obj.getObjectID(), obj.getRelX(), -obj.getRelY());
            String altitudeInfo = String.format("Alt: %d m", obj.getAltitude());

            g2d.drawString(idAndCoords, (int)screenX + obj.getSize() + 5, (int)screenY + obj.getSize()/2 + 20);
            g2d.drawString(altitudeInfo, (int)screenX + obj.getSize() + 5, (int)screenY + obj.getSize()/2 + 35);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        beamAngle += 0.05;
        if (beamAngle > 2 * Math.PI) {
            beamAngle -= 2 * Math.PI;
        }

        beamAngleHistory.addFirst(beamAngle);
        if (beamAngleHistory.size() > MAX_BEAM_HISTORY_SIZE) {
            beamAngleHistory.removeLast();
        }

        long currentTime = System.currentTimeMillis();
        List<String> objectIDsToRemove = new ArrayList<>();

        for (AirborneObject obj : activeObjectsList) {
            if (currentTime - obj.getLastDetectionTimestamp() > OBJECT_EXPIRATION_TIME) {
                objectIDsToRemove.add(obj.getObjectID());
                if (obj == selectedObject) {
                    selectedObject = null;
                    appendLog("Selected object " + obj.getObjectID() + " expired/lost detection. Deselected.");
                } else {
                    appendLog("Object " + obj.getObjectID() + " expired/lost detection.");
                }
            }
        }

        for (String id : objectIDsToRemove) {
            detectedObjectsMap.remove(id);
            activeObjectsList.removeIf(obj -> obj.getObjectID().equals(id));
            acknowledgedAlerts.remove(id); // Clean up ack list
        }

        updateThreatSystemAndUI();
        updateDetectedObjectsList();
        repaint();
    }

    private void updateThreatSystemAndUI() {
        if (dashboard == null) return;

        double warningRadius = 2500.0; // in meters
        double restrictedRadius = 1500.0; // in meters

        boolean hasActiveObjects = false;
        boolean hasIntruder = false;
        boolean hasUnacknowledgedIntruder = false;
        long currentTime = System.currentTimeMillis();

        // Calculate Sectors counts & proximity threats
        int ne = 0, se = 0, sw = 0, nw = 0;
        String newThreatLevel = "GREEN";

        for (AirborneObject obj : activeObjectsList) {
            if (currentTime - obj.getLastDetectionTimestamp() <= OBJECT_EXPIRATION_TIME) {
                hasActiveObjects = true;
                double distanceMeters = obj.getDistance();

                // Sector checks using relative physical coordinates (dx, dy in meters)
                double dx = obj.getRelX();
                double dy = -obj.getRelY(); // Positive up (North)
                if (dx >= 0 && dy >= 0) ne++;
                else if (dx >= 0 && dy < 0) se++;
                else if (dx < 0 && dy < 0) sw++;
                else nw++;

                // Threat assessments
                if (distanceMeters <= restrictedRadius) {
                    hasIntruder = true;
                    if (!acknowledgedAlerts.contains(obj.getObjectID())) {
                        hasUnacknowledgedIntruder = true;
                    }
                } else if (distanceMeters <= warningRadius) {
                    if (!"RED".equals(newThreatLevel)) {
                        newThreatLevel = "AMBER";
                    }
                }
            }
        }

        if (hasIntruder) {
            newThreatLevel = "RED";
        }

        currentThreatLevel = newThreatLevel;
        dashboard.updateThreatLevel(currentThreatLevel);
        dashboard.updateSectorStats(ne, se, sw, nw);

        // Sound alert system
        if (hasUnacknowledgedIntruder) {
            long now = System.currentTimeMillis();
            if (now - lastBeepTime > 2000) {
                java.awt.Toolkit.getDefaultToolkit().beep();
                lastBeepTime = now;
            }
        }

        // Update selected target detailed kinematics card
        AirborneObject selected = selectedObject;
        if (selected != null && (currentTime - selected.getLastDetectionTimestamp() <= OBJECT_EXPIRATION_TIME)) {
            double range = selected.getDistance();
            double bearing = selected.getHeading();
            double speed = selected.getSpeed(pixelsPerMeter);
            int alt = selected.getAltitude();

            // Sector name using physical coordinates
            double dx = selected.getRelX();
            double dy = -selected.getRelY();
            String sector = "N/A";
            if (dx >= 0 && dy >= 0) sector = "NE";
            else if (dx >= 0 && dy < 0) sector = "SE";
            else if (dx < 0 && dy < 0) sector = "SW";
            else sector = "NW";

            // Target threat
            String targetThreat = "GREEN";
            if (range <= restrictedRadius) {
                targetThreat = "RED";
            } else if (range <= warningRadius) {
                targetThreat = "AMBER";
            }

            boolean isAcked = acknowledgedAlerts.contains(selected.getObjectID());
            dashboard.updateSelectedTarget(selected.getObjectID(), range, bearing, speed, alt, sector, isAcked, targetThreat, selected.getFusionSource());
        } else {
            dashboard.updateSelectedTarget(null, 0.0, 0.0, 0.0, 0, "N/A", false, "NONE", "N/A");
        }
    }

}