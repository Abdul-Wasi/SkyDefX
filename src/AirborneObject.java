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

    public void updatePosition(double newRelX, double newRelY, int newAltitude) {
        this.relX = newRelX;
        this.relY = newRelY;
        this.altitude = newAltitude;
        this.trail.add(new Point2D.Double(relX, relY));
        if (trail.size() > MAX_TRAIL_LENGTH) {
            trail.removeFirst();
        }
        this.lastDetectionTimestamp = System.currentTimeMillis();
        this.currentlyDetectedByBeam = true;
    }

    public void draw(Graphics2D g2d, int centerX, int centerY, double pixelsPerMeter) {
        // Compute screen coordinates dynamically based on scale
        double screenX = centerX + (relX * pixelsPerMeter) - size / 2.0;
        double screenY = centerY + (relY * pixelsPerMeter) - size / 2.0;

        long currentTime = System.currentTimeMillis();
        if (currentlyDetectedByBeam && (currentTime - lastDetectionTimestamp < DETECTION_FADE_DURATION)) {
            g2d.setColor(new Color(255, 255, 0)); // Bright yellow when active
        } else {
            g2d.setColor(new Color(255, 0, 0)); // Red when fading
            currentlyDetectedByBeam = false;
        }
        g2d.fillRect((int) screenX, (int) screenY, size, size);
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
}