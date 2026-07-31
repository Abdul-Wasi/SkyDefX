// AirborneObject.java
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.LinkedList;

public class AirborneObject {
    private String objectID;
    private double relX, relY; // Physical position in meters relative to radar station (East, South)
    private int size;          // Size in pixels for drawing
    private int altitude;      // Altitude in meters

    private LinkedList<Point2D.Double> trail; // Stores past relative positions (meters)
    public static final int MAX_TRAIL_LENGTH = 30;
    private boolean currentlyDetectedByBeam;
    private long lastDetectionTimestamp;
    private long prevDetectionTimestamp = 0;
    
    // Sensor Fusion fields
    private String fusionSource = "CAMERA"; // "CAMERA", "LIDAR", "ULTRASONIC"
    private boolean mergedIntoDrone = false;

    public static final long DETECTION_FADE_DURATION = 1500; // Milliseconds

    public AirborneObject(String objectID, double relX, double relY, int altitude, int size) {
        this.objectID = objectID;
        this.relX = relX;
        this.relY = relY;
        this.altitude = altitude;
        this.size = size;
        this.trail = new LinkedList<>();
        // Add initial position in meters
        this.trail.add(new Point2D.Double(relX, relY));
        this.currentlyDetectedByBeam = false;
        this.lastDetectionTimestamp = 0;
    }
    // Update position and add to trail
    public void updatePosition(double newRelX, double newRelY, int newAltitude) {
        this.prevDetectionTimestamp = this.lastDetectionTimestamp;
        this.lastDetectionTimestamp = System.currentTimeMillis();
        this.relX = newRelX;
        this.relY = newRelY;
        this.altitude = newAltitude;
        this.trail.add(new Point2D.Double(relX, relY));
        if (trail.size() > MAX_TRAIL_LENGTH) {
            trail.removeFirst();
        }
        this.currentlyDetectedByBeam = true;
    }

    public void draw(Graphics2D g2d, int centerX, int centerY, double pixelsPerMeter) {
        // Compute screen coordinates dynamically based on scale
        double screenX = centerX + (relX * pixelsPerMeter) - size / 2.0;
        double screenY = centerY + (relY * pixelsPerMeter) - size / 2.0;

        long currentTime = System.currentTimeMillis();
        boolean isActive = currentlyDetectedByBeam && (currentTime - lastDetectionTimestamp < DETECTION_FADE_DURATION);
        
        if (isActive) {
            g2d.setColor(new Color(255, 255, 0)); // Bright yellow when active
        } else {
            g2d.setColor(new Color(255, 0, 0)); // Red when fading
            currentlyDetectedByBeam = false;
        }
        g2d.fillRect((int) screenX, (int) screenY, size, size);

        // Draw custom military-grade fusion indicator rings
        if (isActive && objectID.startsWith("DRONE_")) {
            if ("LIDAR".equals(fusionSource)) {
                g2d.setColor(new Color(0, 255, 255, 200)); // Bright Cyan for LiDAR Fusion
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawOval((int) screenX - 5, (int) screenY - 5, size + 10, size + 10);
                g2d.drawString("FSD:LIDAR", (int) screenX - 15, (int) screenY - 8);
            } else if ("ULTRASONIC".equals(fusionSource)) {
                g2d.setColor(new Color(255, 0, 255, 200)); // Bright Magenta for Ultrasonic Fusion
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawOval((int) screenX - 5, (int) screenY - 5, size + 10, size + 10);
                g2d.drawString("FSD:ULTRA", (int) screenX - 15, (int) screenY - 8);
            }
            g2d.setStroke(new BasicStroke(1.0f)); // reset stroke
        }
    }

    // Getters
    public String getObjectID() { return objectID; }
    public double getRelX() { return relX; }
    public double getRelY() { return relY; }
    public int getAltitude() { return altitude; }
    public int getSize() { return size; }
    public LinkedList<Point2D.Double> getTrail() { return trail; }
    
    public double getDistance() {
        return Math.sqrt(relX * relX + relY * relY);
    }

    public boolean isCurrentlyDetectedByBeam() {
        return currentlyDetectedByBeam && (System.currentTimeMillis() - lastDetectionTimestamp < DETECTION_FADE_DURATION);
    }

    public long getLastDetectionTimestamp() { return lastDetectionTimestamp; }

    public void setCurrentlyDetectedByBeam(boolean detected) {
        this.currentlyDetectedByBeam = detected;
        if (detected) {
            this.lastDetectionTimestamp = System.currentTimeMillis();
        }
    }

    public double getSpeed(double pixelsPerMeter) {
        if (trail.size() < 2 || prevDetectionTimestamp == 0) return 0.0;
        long timeDeltaMs = lastDetectionTimestamp - prevDetectionTimestamp;
        if (timeDeltaMs <= 0) return 0.0;
        
        Point2D.Double p1 = trail.get(trail.size() - 2);
        Point2D.Double p2 = trail.getLast();
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double distMeters = Math.sqrt(dx * dx + dy * dy);
        
        double speedMps = distMeters / (timeDeltaMs / 1000.0);
        if (speedMps > 100.0) speedMps = 100.0; // clamp speed
        return speedMps;
    }

    public double getHeading() {
        if (trail.size() < 2) return 0.0;
        Point2D.Double p1 = trail.get(trail.size() - 2);
        Point2D.Double p2 = trail.getLast();
        
        double dx = p2.x - p1.x;
        double dy = p1.y - p2.y; // screen coordinates are inverted vertically
        
        double angleRad = Math.atan2(dx, dy);
        double angleDeg = Math.toDegrees(angleRad);
        return (angleDeg + 360) % 360;
    }

    public String getFusionSource() { return fusionSource; }
    public void setFusionSource(String source) { this.fusionSource = source; }

    public boolean isMergedIntoDrone() { return mergedIntoDrone; }
    public void setMergedIntoDrone(boolean merged) { this.mergedIntoDrone = merged; }
}