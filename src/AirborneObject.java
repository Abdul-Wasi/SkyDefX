// AirborneObject.java
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.LinkedList;

public class AirborneObject {
    private String objectID;
    private double x, y; // Position on the panel
    private int size;
    private int altitude; // In meters, from Python's elevation/distance

    private LinkedList<Point2D.Double> trail;
    public static final int MAX_TRAIL_LENGTH = 30; // Number of past positions to store for trail

    private boolean currentlyDetectedByBeam; // True if recently detected by Python
    private long lastDetectionTimestamp; // Timestamp of the last Python detection update

    public static final long DETECTION_FADE_DURATION = 1500; // Time in milliseconds for detection status to fade

    public AirborneObject(String objectID, double x, double y, int altitude, int size) {
        this.objectID = objectID;
        this.x = x;
        this.y = y;
        this.altitude = altitude;
        this.size = size;
        this.trail = new LinkedList<>();
        // Add initial center point; adjusting for drawing from top-left corner
        this.trail.add(new Point2D.Double(x + size / 2.0, y + size / 2.0)); 
        this.currentlyDetectedByBeam = false;
        this.lastDetectionTimestamp = 0; // Not detected yet
    }

    // Update position and add to trail
    public void updatePosition(double newX, double newY, int newAltitude) {
        this.x = newX;
        this.y = newY;
        this.altitude = newAltitude;
        // Add new center point; adjusting for drawing from top-left corner
        this.trail.add(new Point2D.Double(x + size / 2.0, y + size / 2.0)); 
        if (trail.size() > MAX_TRAIL_LENGTH) {
            trail.removeFirst(); // Keep trail length fixed
        }
        this.lastDetectionTimestamp = System.currentTimeMillis();
        this.currentlyDetectedByBeam = true; // Mark as detected immediately upon update
    }

    public void draw(Graphics2D g2d) {
        // Determine color based on detection status and how recently it was detected
        long currentTime = System.currentTimeMillis();
        if (currentlyDetectedByBeam && (currentTime - lastDetectionTimestamp < DETECTION_FADE_DURATION)) {
            g2d.setColor(new Color(255, 255, 0)); // Bright yellow if recently detected
        } else {
            g2d.setColor(new Color(255, 0, 0)); // Red if not detected or detection faded
            currentlyDetectedByBeam = false; // Reset status if duration passed
        }
        g2d.fillRect((int) x, (int) y, size, size);
    }

    // Getters
    public String getObjectID() { return objectID; }
    public double getX() { return x; }
    public double getY() { return y; }
    public int getAltitude() { return altitude; }
    public int getSize() { return size; }
    public LinkedList<Point2D.Double> getTrail() { return trail; }
    public boolean isCurrentlyDetectedByBeam() {
        // Also check timestamp to ensure it's a recent detection for display
        return currentlyDetectedByBeam && (System.currentTimeMillis() - lastDetectionTimestamp < DETECTION_FADE_DURATION);
    }
    public long getLastDetectionTimestamp() { return lastDetectionTimestamp; }

    // Setters (e.g., for selection highlight or specific status)
    public void setCurrentlyDetectedByBeam(boolean detected) {
        // This setter is primarily used for visual feedback from the radar panel itself,
        // but the main detection status comes from Python data updates.
        // It's crucial for the blinking effect.
        this.currentlyDetectedByBeam = detected;
        if (detected) {
            this.lastDetectionTimestamp = System.currentTimeMillis();
        }
    }
}