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

    private LinkedList<Double> beamAngleHistory;
    private static final int MAX_BEAM_HISTORY_SIZE = 50;

    private double pixelsPerMeter; 
    private SatelliteMapManager mapManager;

    private Point dragStartPoint;
    private boolean isPanning = false;
    private java.util.function.BiConsumer<Double, Double> onCoordinatesMoved;

    public RadarPanel(JTextArea detectedObjectsListArea, JTextArea systemLogArea) {
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

        appendLog("System initialized. Satellite Radar online. Waiting for detections...");
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

    private void updateDetectedObjectsList() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-10s %-10s %-10s %-8s %-8s\n", "ID", "EAST (m)", "NORTH (m)", "ALT (m)", "STATUS"));
            sb.append("------------------------------------------------------------\n");

            boolean detectedAny = false;
            long currentTime = System.currentTimeMillis();

            for (AirborneObject obj : activeObjectsList) {
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

                    sb.append(String.format("%-10s %-10d %-10d %-8d %-8s\n",
                                    obj.getObjectID(), relativeE, relativeN, obj.getAltitude(), status));
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
                detectedObjectsMap.put(objectID, obj);
                activeObjectsList.add(obj);
                appendLog("NEW DETECTED: ID " + objectID + String.format(" at E:%dm, N:%dm, Alt:%dm (Webcam)",
                                (int)relX, (int)-relY, displayAltitude));
            } else {
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

        // 3. Draw the Radar Sweep Beam (semi-transparent green overlay)
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
            // Check if object is within visible radar range
            if (obj.getDistance() > maxRadarRange) {
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
        }

        updateDetectedObjectsList();
        repaint();
    }
}