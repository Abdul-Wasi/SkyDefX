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
import java.util.concurrent.ConcurrentHashMap; // For thread-safe map access
import java.util.concurrent.CopyOnWriteArrayList; // For thread-safe list access during iteration
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import org.json.JSONObject; // Import for JSON processing

class RadarPanel extends JPanel implements ActionListener {

    // --- Private Fields ---
    private double beamAngle = 0;
    private Timer timer;
    private Map<String, AirborneObject> detectedObjectsMap;
    private List<AirborneObject> activeObjectsList; // To maintain drawing order, will be derived from map values
    private JTextArea detectedObjectsListArea;
    private JTextArea systemLogArea;

    private AirborneObject selectedObject; // Holds the object currently selected by clicking

    private static final long DETECTION_DISPLAY_DURATION = 1000; // milliseconds to show "DETECTED!" status
    private static final long OBJECT_EXPIRATION_TIME = 5000; // Milliseconds after last detection to remove object

    // Fields for Radar Afterglow Effect
    private LinkedList<Double> beamAngleHistory;
    private static final int MAX_BEAM_HISTORY_SIZE = 50; // Number of past beam positions to store (20ms/frame * 50 = 1 sec trail)

    // --- Constants for Radar Scaling ---
    private static final double MAX_RADAR_RANGE_METERS = 5000; // Max range of our simulated radar in meters
    private double pixelsPerMeter; // Calculated based on panel size

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

        setBackground(new Color(10, 10, 10)); // Very dark background for the radar scope
        detectedObjectsMap = new ConcurrentHashMap<>(); // Thread-safe map for detections
        activeObjectsList = new CopyOnWriteArrayList<>(); // Thread-safe list for drawing iteration
        selectedObject = null; // No object selected initially

        beamAngleHistory = new LinkedList<>();

        timer = new Timer(20, this); // Animation timer (20ms interval)
        timer.start(); // Start the animation loop

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });

        appendLog("System initialized. Radar online. Threat Assessment Core activated.");
    }

    // --- Helper Method to Append to System Log ---
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

    // --- Helper Method to Update Detected Objects List ---
    private void updateDetectedObjectsList() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-10s %-8s %-6s %-6s %-8s\n", "ID", "X", "Y", "ALT", "STATUS"));
            sb.append("--------------------------------------------------\n");

            boolean detectedAny = false;
            long currentTime = System.currentTimeMillis();

            // Iterate over values of the map for display
            for (AirborneObject obj : activeObjectsList) {
                // Only list objects that are still active (not expired)
                if (currentTime - obj.getLastDetectionTimestamp() < OBJECT_EXPIRATION_TIME) {
                    detectedAny = true;
                    int centerX = getWidth() / 2;
                    int centerY = getHeight() / 2;
                    int relativeX = (int) (obj.getX() + obj.getSize() / 2 - centerX); // Center of object relative to radar center
                    int relativeY = (int) (obj.getY() + obj.getSize() / 2 - centerY); // Center of object relative to radar center

                    String status = "ACTIVE";
                    if (obj == selectedObject) {
                        status = "TARGETED";
                    } else if (obj.isCurrentlyDetectedByBeam()) { // Check current active detection status
                        status = "DETECTED";
                    } else if (currentTime - obj.getLastDetectionTimestamp() < AirborneObject.DETECTION_FADE_DURATION) {
                         status = "LAST SEEN"; // Indicate it was seen recently but not current beam
                    }

                    sb.append(String.format("%-10s %-8d %-6d %-6d %-8s\n",
                                    obj.getObjectID(), relativeX, relativeY, obj.getAltitude(), status));
                }
            }

            if (!detectedAny) {
                sb.append("No active detections.\n");
            }

            detectedObjectsListArea.setText(sb.toString());
        });
    }

    // Handle mouse clicks on the radar panel
    private void handleClick(int mouseX, int mouseY) {
        AirborneObject clickedOn = null;
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int maxRadarRadius = Math.min(centerX, centerY) - 20;

        for (AirborneObject obj : activeObjectsList) {
            double distanceToCenter = Math.sqrt(Math.pow(obj.getX() + obj.getSize()/2 - centerX, 2) + Math.pow(obj.getY() + obj.getSize()/2 - centerY, 2));

            if (distanceToCenter <= maxRadarRadius && // Object must be within radar's visual range
                mouseX >= obj.getX() && mouseX <= obj.getX() + obj.getSize() &&
                mouseY >= obj.getY() && mouseY <= obj.getY() + obj.getSize()) {
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
                                " at X:" + (int)(selectedObject.getX() + selectedObject.getSize()/2 - getWidth()/2) +
                                " Y:" + (int)(selectedObject.getY() + selectedObject.getSize()/2 - getHeight()/2) +
                                " Alt:" + selectedObject.getAltitude() + "m");
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
            // Panel not yet fully laid out, ignore detections for now
            return;
        }
        // Calculate pixelsPerMeter once panel dimensions are stable
        if (pixelsPerMeter == 0) {
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            int maxRadarRadius = Math.min(centerX, centerY) - 20;
            pixelsPerMeter = (double) maxRadarRadius / MAX_RADAR_RANGE_METERS;
            appendLog("Radar panel dimensions established. Max range: " + MAX_RADAR_RANGE_METERS + "m.");
        }

        try {
            String objectID = droneData.getString("id");
            double azimuthDeg = droneData.getDouble("azimuth"); // Azimuth in degrees (0-360)
            double elevationDeg = droneData.getDouble("elevation"); // Elevation in degrees
            double distanceMeters = droneData.getDouble("distance");

            // Convert degrees to radians for trigonometric functions
            double azimuthRad = Math.toRadians(azimuthDeg);
            double elevationRad = Math.toRadians(elevationDeg);

            double scaledDistance = distanceMeters * pixelsPerMeter;
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;

            double targetX = centerX + (scaledDistance * Math.cos(azimuthRad - Math.PI / 2));
            double targetY = centerY + (scaledDistance * Math.sin(azimuthRad - Math.PI / 2));

            // Calculate altitude based on elevation and distance
            int displayAltitude = (int) (distanceMeters * Math.sin(elevationRad));
            if (displayAltitude < 0) displayAltitude = 0; // Altitude can't be negative

            int objectSize = 10; // Standard size for a detected object

            AirborneObject obj = detectedObjectsMap.get(objectID);

            if (obj == null) {
                // New object detected: create and add to map and drawing list
                obj = new AirborneObject(objectID, targetX - objectSize / 2.0, targetY - objectSize / 2.0, displayAltitude, objectSize);
                detectedObjectsMap.put(objectID, obj);
                activeObjectsList.add(obj); // Add to list for drawing
                appendLog("NEW DETECTED: ID " + objectID + String.format(" at X:%d, Y:%d, Alt:%dm (from Python)",
                                (int)(targetX - centerX), (int)(targetY - centerY), displayAltitude));
            } else {
                // Existing object, update its position and status
                obj.updatePosition(targetX - objectSize / 2.0, targetY - objectSize / 2.0, displayAltitude);
            }
            // Ensure visual detection state for blinking.
            obj.setCurrentlyDetectedByBeam(true); // This sets the timestamp internally

        } catch (org.json.JSONException e) {
            appendLog("ERROR: Failed to parse drone detection JSON: " + e.getMessage());
        } catch (Exception e) {
            appendLog("ERROR in handlePythonDetection: " + e.getMessage());
            e.printStackTrace();
        }
        repaint(); // Request a repaint to show new/updated objects
    }

    // --- paintComponent Method ---
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

        // Draw static radar elements (grid)
        g2d.setColor(new Color(0, 60, 0)); // Darker green for grid lines
        g2d.setStroke(new BasicStroke(1));
        g2d.drawLine(centerX, centerY - radius, centerX, centerY + radius);
        g2d.drawLine(centerX - radius, centerY, centerX + radius, centerY);
        for (int i = 1; i <= 4; i++) {
            int currentRadius = radius / 4 * i;
            g2d.drawOval(centerX - currentRadius, centerY - currentRadius,
                    2 * currentRadius, 2 * currentRadius);
        }

        // Draw Software-Defined Geofence Zones
        double mppScale = (double) radius / MAX_RADAR_RANGE_METERS;
        double warningRadius = 2500.0 * mppScale;
        double restrictedRadius = 1500.0 * mppScale;

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

        // Draw the Radar Beam Afterglow
        float alphaStepBeam = 1.0f / MAX_BEAM_HISTORY_SIZE;
        int currentBeamIndex = 0;
        for (Double historyAngle : beamAngleHistory) {
            float alpha = 1.0f - (currentBeamIndex * alphaStepBeam);
            if (alpha < 0.0f) alpha = 0.0f;

            g2d.setColor(new Color(50, 255, 50, (int)(alpha * 150)));
            g2d.setStroke(new BasicStroke(3));

            int histBeamX = (int) (centerX + radius * Math.cos(historyAngle - Math.PI / 2));
            int histBeamY = (int) (centerY + radius * Math.sin(historyAngle - Math.PI / 2));
            g2d.drawLine(centerX, centerY, histBeamX, histBeamY);

            currentBeamIndex++;
        }

        // Draw the current, brightest Radar Beam on top (fully opaque)
        g2d.setColor(new Color(50, 255, 50, 255)); // Bright green, fully opaque
        g2d.setStroke(new BasicStroke(3));
        int beamX = (int) (centerX + radius * Math.cos(beamAngle - Math.PI / 2));
        int beamY = (int) (centerY + radius * Math.sin(beamAngle - Math.PI / 2));
        g2d.drawLine(centerX, centerY, beamX, beamY);


        // --- Draw All Airborne Objects, Their Trails, and Information ---
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 12));
        long currentTime = System.currentTimeMillis();

        // Use activeObjectsList for drawing, which gets pruned in actionPerformed
        for (AirborneObject obj : activeObjectsList) {
            // Only draw objects that are within radar range and not too old
            double distanceToCenter = Math.sqrt(Math.pow(obj.getX() + obj.getSize()/2.0 - centerX, 2) + Math.pow(obj.getY() + obj.getSize()/2.0 - centerY, 2));

            if (distanceToCenter > radius || (currentTime - obj.getLastDetectionTimestamp() > OBJECT_EXPIRATION_TIME)) {
                continue;
            }

            // Draw Object Trail
            LinkedList<Point2D.Double> currentTrail = obj.getTrail();
            if (currentTrail.size() > 1) {
                float alphaStepTrail = 1.0f / (float) AirborneObject.MAX_TRAIL_LENGTH;

                for (int i = 0; i < currentTrail.size() - 1; i++) {
                    Point2D.Double p1 = currentTrail.get(i);
                    Point2D.Double p2 = currentTrail.get(i + 1);

                    float alpha = 1.0f - ((float) i * alphaStepTrail);
                    if (alpha < 0.0f) alpha = 0.0f;

                    g2d.setColor(new Color(255, 0, 0, (int)(alpha * 150)));
                    g2d.setStroke(new BasicStroke(1));

                    g2d.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);
                }
            }

            // Draw Heading Velocity Vector
            if (currentTrail.size() >= 2) {
                double speed = obj.getSpeed(mppScale);
                if (speed > 0.5) { // Only draw vector line if target is moving
                    double headingDeg = obj.getHeading();
                    double headingRad = Math.toRadians(headingDeg);
                    
                    // Vector length scales with speed (e.g. 1.5 pixels per m/s)
                    double vectorLength = Math.max(15.0, Math.min(50.0, speed * 1.5));
                    
                    // Screen-space dx and dy
                    int startX = (int)(obj.getX() + obj.getSize() / 2.0);
                    int startY = (int)(obj.getY() + obj.getSize() / 2.0);
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

            // Draw highlight if this object is selected
            if (obj == selectedObject) {
                g2d.setColor(new Color(255, 165, 0)); // Orange highlight
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRect((int) obj.getX() - 2, (int) obj.getY() - 2, obj.getSize() + 4, obj.getSize() + 4);
                g2d.setStroke(new BasicStroke(1));
            }

            // Draw object and "DETECTED!" text
            obj.draw(g2d);

            if (obj.isCurrentlyDetectedByBeam() && (currentTime - obj.getLastDetectionTimestamp() < DETECTION_DISPLAY_DURATION)) {
                g2d.setColor(new Color(255, 255, 0)); // Bright yellow for DETECTED text and lines
                g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f, 5.0f}, 0.0f)); // Dashed line
                g2d.drawLine(centerX, centerY, (int)(obj.getX() + obj.getSize()/2), (int)(obj.getY() + obj.getSize()/2));
                g2d.drawString("DETECTED!", (int)obj.getX() + obj.getSize() + 5, (int)obj.getY() + obj.getSize()/2 + 5);
            }

            g2d.setColor(new Color(200, 200, 200)); // Lighter grey for all other info

            String idAndCoords = String.format("ID: %s | X: %d, Y: %d",
                                               obj.getObjectID(),
                                               ((int)obj.getX() + obj.getSize()/2 - centerX),
                                               ((int)obj.getY() + obj.getSize()/2 - centerY));
            String altitudeInfo = String.format("Alt: %d m", obj.getAltitude());

            g2d.drawString(idAndCoords, (int)obj.getX() + obj.getSize() + 5, (int)obj.getY() + obj.getSize()/2 + 20);
            g2d.drawString(altitudeInfo, (int)obj.getX() + obj.getSize() + 5, (int)obj.getY() + obj.getSize()/2 + 35);
        }
    }

    // --- actionPerformed Method (Object Lifecycle) ---
    @Override
    public void actionPerformed(ActionEvent e) {
        // Update radar beam angle for visual sweep
        beamAngle += 0.05;
        if (beamAngle > 2 * Math.PI) {
            beamAngle -= 2 * Math.PI;
        }

        // Add current beam angle to history
        beamAngleHistory.addFirst(beamAngle);
        if (beamAngleHistory.size() > MAX_BEAM_HISTORY_SIZE) {
            beamAngleHistory.removeLast();
        }

        // --- Object Lifecycle Management (Removal of expired objects) ---
        long currentTime = System.currentTimeMillis();
        List<String> objectIDsToRemove = new ArrayList<>();

        for (AirborneObject obj : activeObjectsList) {
            // If an object hasn't been updated by Python for OBJECT_EXPIRATION_TIME, remove it
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

        // Remove from map and list
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

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int radius = Math.min(centerX, centerY) - 20;
        double mppScale = (double) radius / MAX_RADAR_RANGE_METERS;
        double warningRadius = 2500.0 * mppScale;
        double restrictedRadius = 1500.0 * mppScale;

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
                double distanceToCenter = Math.sqrt(
                    Math.pow(obj.getX() + obj.getSize()/2.0 - centerX, 2) +
                    Math.pow(obj.getY() + obj.getSize()/2.0 - centerY, 2)
                );

                // Sector checks
                double dx = obj.getX() + obj.getSize()/2.0 - centerX;
                double dy = centerY - (obj.getY() + obj.getSize()/2.0); // Positive up
                if (dx >= 0 && dy >= 0) ne++;
                else if (dx >= 0 && dy < 0) se++;
                else if (dx < 0 && dy < 0) sw++;
                else nw++;

                // Threat assessments
                if (distanceToCenter <= restrictedRadius) {
                    hasIntruder = true;
                    if (!acknowledgedAlerts.contains(obj.getObjectID())) {
                        hasUnacknowledgedIntruder = true;
                    }
                } else if (distanceToCenter <= warningRadius) {
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
            double distanceToCenter = Math.sqrt(
                Math.pow(selected.getX() + selected.getSize()/2.0 - centerX, 2) +
                Math.pow(selected.getY() + selected.getSize()/2.0 - centerY, 2)
            );
            double range = distanceToCenter / mppScale;
            double bearing = selected.getHeading();
            double speed = selected.getSpeed(mppScale);
            int alt = selected.getAltitude();

            // Sector name
            double dx = selected.getX() + selected.getSize()/2.0 - centerX;
            double dy = centerY - (selected.getY() + selected.getSize()/2.0);
            String sector = "N/A";
            if (dx >= 0 && dy >= 0) sector = "NE";
            else if (dx >= 0 && dy < 0) sector = "SE";
            else if (dx < 0 && dy < 0) sector = "SW";
            else sector = "NW";

            // Target threat
            String targetThreat = "GREEN";
            if (distanceToCenter <= restrictedRadius) {
                targetThreat = "RED";
            } else if (distanceToCenter <= warningRadius) {
                targetThreat = "AMBER";
            }

            boolean isAcked = acknowledgedAlerts.contains(selected.getObjectID());
            dashboard.updateSelectedTarget(selected.getObjectID(), range, bearing, speed, alt, sector, isAcked, targetThreat);
        } else {
            dashboard.updateSelectedTarget(null, 0.0, 0.0, 0.0, 0, "N/A", false, "NONE");
        }
    }
}