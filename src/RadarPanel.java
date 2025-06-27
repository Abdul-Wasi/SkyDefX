// RadarPanel.java (Updated for Object Trails)

import javax.swing.*;                   // Provides JPanel, Timer, JTextArea (for outputting to dashboard)
import java.awt.*;                      // Provides Graphics, Graphics2D, Color, BasicStroke, RenderingHints
import java.awt.event.ActionEvent;      // Class representing an action event
import java.awt.event.ActionListener;   // Interface for receiving action events
import java.awt.event.MouseAdapter;     // For handling mouse events easily
import java.awt.event.MouseEvent;       // Class representing a mouse event
import java.awt.geom.Point2D;           // NEW: For drawing object trails
import java.util.ArrayList;             // Original list type (not used directly, but part of context)
import java.util.List;                  // Interface for list
import java.util.Iterator;              // For safely removing elements
import java.util.concurrent.CopyOnWriteArrayList; // Thread-safe list for concurrent access
import java.time.LocalTime;             // For logging timestamps
import java.time.format.DateTimeFormatter; // For formatting time in logs
import java.util.LinkedList;            // For storing beam angle history


class RadarPanel extends JPanel implements ActionListener {

    // --- Private Fields ---
    private double beamAngle = 0;
    private Timer timer;
    private List<AirborneObject> airborneObjects; // List to manage all airborne objects
    private JTextArea detectedObjectsListArea; // Reference to the detected objects list in the dashboard
    private JTextArea systemLogArea;         // Reference to the system log area in the dashboard

    private AirborneObject selectedObject; // Holds the object currently selected by clicking
    private List<String> currentlyDetectedIDsInBeam; // Tracks IDs of objects hit by the current beam sweep

    private static final long DETECTION_DISPLAY_DURATION = 1000; // milliseconds to show "DETECTED!" status
    private long lastDetectionVisualTime = 0; // Timestamp of the last detection (for blinking effect)

    private boolean initialObjectsSpawned = false; // Flag to control initial object creation

    // Fields for Radar Afterglow Effect
    private LinkedList<Double> beamAngleHistory; // Stores past beam angles for the trail
    private static final int MAX_BEAM_HISTORY_SIZE = 50; // Number of past beam positions to store (20ms/frame * 50 = 1 sec trail)


    // --- Constructor ---
    // Modified constructor to accept JTextArea references from the main frame.
    public RadarPanel(JTextArea detectedObjectsListArea, JTextArea systemLogArea) {
        this.detectedObjectsListArea = detectedObjectsListArea;
        this.systemLogArea = systemLogArea;
        
        setBackground(new Color(10, 10, 10)); // Very dark background for the radar scope
        airborneObjects = new CopyOnWriteArrayList<>(); // Using thread-safe list
        currentlyDetectedIDsInBeam = new ArrayList<>(); // Initialize tracking list for beam hits
        selectedObject = null; // No object selected initially

        // Initialize beam angle history
        beamAngleHistory = new LinkedList<>(); 

        timer = new Timer(20, this); // Animation timer (20ms interval)
        timer.start(); // Start the animation loop

        // Add MouseListener for interactivity
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY()); // Call a helper method to handle the click
            }
        });

        // Log initial message to system log
        appendLog("System initialized. Radar online. Scanning airspace...");
    }

    // --- Helper Method to Append to System Log ---
    private void appendLog(String message) {
        // Use SwingUtilities.invokeLater to ensure updates to JTextArea happen on the EDT.
        SwingUtilities.invokeLater(() -> {
            systemLogArea.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - " + message + "\n");
            // Auto-scroll to the bottom of the log area
            systemLogArea.setCaretPosition(systemLogArea.getDocument().getLength());
        });
    }

    // --- Helper Method to Update Detected Objects List ---
    private void updateDetectedObjectsList() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-10s %-8s %-6s %-6s %-8s\n", "ID", "X", "Y", "ALT", "STATUS")); // Header with STATUS
            sb.append("--------------------------------------------------\n"); // Separator

            boolean detectedAny = false;
            for (AirborneObject obj : airborneObjects) {
                // We only list objects that are currently within the radar's sweep OR are selected.
                if (obj.isCurrentlyDetectedByBeam() || obj == selectedObject) { // Check for beam hit OR selection
                    detectedAny = true;
                    // Coordinates relative to radar center
                    int centerX = getWidth() / 2;
                    int centerY = getHeight() / 2;
                    int relativeX = (int) (obj.getX() - centerX);
                    int relativeY = (int) (obj.getY() - centerY);

                    String status = "ACTIVE"; // Default status
                    if (obj == selectedObject) {
                        status = "TARGETED"; // Highlight in list if selected
                    } else if (obj.isCurrentlyDetectedByBeam()) {
                        status = "DETECTED";
                    }

                    sb.append(String.format("%-10s %-8d %-6d %-6d %-8s\n",
                            obj.getObjectID(), relativeX, relativeY, obj.getAltitude(), status));
                }
            }

            if (!detectedAny) {
                sb.append("No active detections.\n");
            }

            detectedObjectsListArea.setText(sb.toString()); // Set the updated text
        });
    }

    // Handle mouse clicks on the radar panel
    private void handleClick(int mouseX, int mouseY) {
        AirborneObject clickedOn = null;
        for (AirborneObject obj : airborneObjects) {
            // Check if the click is within the object's bounding box
            // AND if the object is currently within the visible radar area (maxRadarRadius)
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            int maxRadarRadius = Math.min(centerX, centerY) - 20;
            double distance = Math.sqrt(Math.pow(obj.getX() - centerX, 2) + Math.pow(obj.getY() - centerY, 2));

            if (distance <= maxRadarRadius && // Object must be within the radar's visual range
                mouseX >= obj.getX() && mouseX <= obj.getX() + obj.getSize() &&
                mouseY >= obj.getY() && mouseY <= obj.getY() + obj.getSize()) {
                clickedOn = obj;
                break; // Found an object, stop checking
            }
        }

        if (clickedOn != null) {
            if (clickedOn == selectedObject) {
                // If the same object is clicked again, deselect it
                selectedObject = null;
                appendLog("Object deselected: " + clickedOn.getObjectID());
            } else {
                // Select the new object
                selectedObject = clickedOn;
                appendLog("Object selected: " + selectedObject.getObjectID() +
                          " at X:" + (int)(selectedObject.getX() - getWidth()/2) +
                          " Y:" + (int)(selectedObject.getY() - getHeight()/2) +
                          " Alt:" + selectedObject.getAltitude() + "m");
            }
        } else {
            // Clicked on empty space, deselect any currently selected object
            if (selectedObject != null) {
                appendLog("Clicked on empty radar space. Deselecting object: " + selectedObject.getObjectID());
                selectedObject = null;
            } else {
                appendLog("Clicked on empty radar space.");
            }
        }
        repaint(); // Repaint to show/hide selection highlight
        updateDetectedObjectsList(); // Update the list to reflect selection status
    }

    // --- addNotify Method ---
    @Override
    public void addNotify() {
        super.addNotify();
        // Initial object creation is now handled in actionPerformed once panel dimensions are stable.
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

        // Draw the Radar Beam Afterglow
        float alphaStepBeam = 1.0f / MAX_BEAM_HISTORY_SIZE;
        int currentBeamIndex = 0;
        for (Double historyAngle : beamAngleHistory) {
            float alpha = 1.0f - (currentBeamIndex * alphaStepBeam);
            if (alpha < 0.0f) alpha = 0.0f; // Ensure alpha doesn't go negative

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
        for (AirborneObject obj : airborneObjects) {
            // NEW: Draw Object Trail
            LinkedList<Point2D.Double> currentTrail = obj.getTrail();
            if (currentTrail.size() > 1) { // Need at least two points to draw a line
                // The MAX_TRAIL_LENGTH is defined in AirborneObject, so calculate alpha step based on that
                float alphaStepTrail = 1.0f / (float) AirborneObject.MAX_TRAIL_LENGTH;
                
                for (int i = 0; i < currentTrail.size() - 1; i++) {
                    Point2D.Double p1 = currentTrail.get(i);
                    Point2D.Double p2 = currentTrail.get(i + 1);

                    // Calculate transparency for trail segments (older segments are more transparent)
                    float alpha = 1.0f - ((float) i * alphaStepTrail);
                    if (alpha < 0.0f) alpha = 0.0f; 
                    
                    // Fading red trail, slightly translucent (max alpha 150 for trail)
                    g2d.setColor(new Color(255, 0, 0, (int)(alpha * 150))); 
                    g2d.setStroke(new BasicStroke(1)); // Thin line for trail

                    g2d.drawLine((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);
                }
            }

            // Draw highlight if this object is selected, regardless of detection status
            if (obj == selectedObject) {
                g2d.setColor(new Color(255, 165, 0)); // Orange highlight
                g2d.setStroke(new BasicStroke(2)); // Thicker border
                // Draw a slightly larger square outline around the object
                g2d.drawRect((int) obj.getX() - 2, (int) obj.getY() - 2, obj.getSize() + 4, obj.getSize() + 4);
                g2d.setStroke(new BasicStroke(1)); // Reset stroke for subsequent drawing
            }

            // Only draw "DETECTED!" text and line for a short duration after beam hit
            if (obj.isCurrentlyDetectedByBeam() && (System.currentTimeMillis() - lastDetectionVisualTime < DETECTION_DISPLAY_DURATION)) {
                 obj.draw(g2d); // Draw in yellow (from AirborneObject)
                 g2d.setColor(new Color(255, 255, 0)); // Bright yellow for DETECTED text and lines
                 g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f, 5.0f}, 0.0f)); // Dashed line
                 g2d.drawLine(centerX, centerY, (int)obj.getX(), (int)obj.getY());
                 g2d.drawString("DETECTED!", (int)obj.getX() + 15, (int)obj.getY() + 5);
            } else {
                 // If not currently detected by beam, or if detection visual time passed, draw in default red
                 obj.draw(g2d); // Draw in red (from AirborneObject)
            }
           
            g2d.setColor(new Color(200, 200, 200)); // Lighter grey for all other info

            String idAndCoords = String.format("ID: %s | X: %d, Y: %d",
                                               obj.getObjectID(),
                                               ((int)obj.getX() - centerX),
                                               ((int)obj.getY() - centerY));
            String altitudeInfo = String.format("Alt: %d m", obj.getAltitude());

            g2d.drawString(idAndCoords, (int)obj.getX() + 15, (int)obj.getY() + 20);
            g2d.drawString(altitudeInfo, (int)obj.getX() + 15, (int)obj.getY() + 35);
        }
    }

    // --- actionPerformed Method (Simulation Logic) ---
    @Override
    public void actionPerformed(ActionEvent e) {
        // --- DEBUG PRINT: Check if this method is being called ---
        System.out.println("Action Performed! Panel Size: " + getWidth() + "x" + getHeight()); 

        // --- Delayed Initial Object Spawning ---
        if (!initialObjectsSpawned && getWidth() > 0 && getHeight() > 0) {
            for (int i = 0; i < 5; i++) {
                airborneObjects.add(new AirborneObject(getWidth(), getHeight()));
            }
            appendLog("Initial airborne objects generated.");
            initialObjectsSpawned = true; // Set flag to true after initial spawn
        }

        // Update radar beam angle
        beamAngle += 0.05;
        if (beamAngle > 2 * Math.PI) {
            beamAngle -= 2 * Math.PI;
        }

        // Add current beam angle to history
        beamAngleHistory.addFirst(beamAngle); // Add to the front (newest)
        if (beamAngleHistory.size() > MAX_BEAM_HISTORY_SIZE) {
            beamAngleHistory.removeLast(); // Remove the oldest if history is too long
        }


        // --- Object Movement and Lifecycle Management ---
        List<AirborneObject> objectsToRemove = new ArrayList<>(); 

        for (AirborneObject obj : airborneObjects) { 
            obj.move(getWidth(), getHeight()); // This also updates the object's internal trail history

            // If object moves off-screen, mark it for removal and add a new one
            if (obj.getX() < -obj.getSize() || obj.getX() > getWidth() + obj.getSize() ||
                obj.getY() < -obj.getSize() || obj.getY() > getHeight() + obj.getSize()) {
                
                if (obj == selectedObject) {
                    selectedObject = null;
                    appendLog("Selected object " + obj.getObjectID() + " exited radar range. Deselected.");
                } else {
                    appendLog("Object " + obj.getObjectID() + " exited radar range.");
                }
                
                currentlyDetectedIDsInBeam.remove(obj.getObjectID());
                objectsToRemove.add(obj); // Mark for removal
            }
        }

        // Now, remove the marked objects and add new ones AFTER iteration
        for (AirborneObject obj : objectsToRemove) {
            airborneObjects.remove(obj); 
            AirborneObject newObj = new AirborneObject(getWidth(), getHeight()); 
            airborneObjects.add(newObj); 
            appendLog("New object " + newObj.getObjectID() + " entered airspace.");
            updateDetectedObjectsList(); 
        }


        // --- Radar Detection Logic ---
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int maxRadarRadius = Math.min(centerX, centerY) - 20;

        // Reset beam detection status for all objects at the start of each sweep
        for (AirborneObject obj : airborneObjects) {
            obj.setCurrentlyDetectedByBeam(false);
        }

        for (AirborneObject obj : airborneObjects) {
            double objectX = obj.getX();
            double objectY = obj.getY();
            double distance = Math.sqrt(Math.pow(objectX - centerX, 2) + Math.pow(objectY - centerY, 2));

            // Check if object is within radar's maximum range
            if (distance <= maxRadarRadius) {
                double objectAngle = Math.atan2(objectY - centerY, objectX - centerX);
                if (objectAngle < 0) {
                    objectAngle += 2 * Math.PI; 
                }
                double normalizedBeamAngle = beamAngle - Math.PI / 2; 
                if (normalizedBeamAngle < 0) {
                    normalizedBeamAngle += 2 * Math.PI;
                }
                double angleTolerance = Math.toRadians(5); // 5-degree wide beam

                // Check if the object is within the radar beam's current sweep angle
                boolean hitByBeam = false;
                if (Math.abs(objectAngle - normalizedBeamAngle) < angleTolerance) {
                    hitByBeam = true;
                } else if (normalizedBeamAngle < angleTolerance && objectAngle > (2 * Math.PI - angleTolerance)) {
                    hitByBeam = true; 
                } else if (objectAngle < angleTolerance && normalizedBeamAngle > (2 * Math.PI - angleTolerance)) {
                    hitByBeam = true; 
                }
                
                // Manage detection status and logging
                if (hitByBeam) {
                    obj.setCurrentlyDetectedByBeam(true); 
                    if (!currentlyDetectedIDsInBeam.contains(obj.getObjectID())) {
                        lastDetectionVisualTime = System.currentTimeMillis(); 
                        currentlyDetectedIDsInBeam.add(obj.getObjectID());
                        appendLog("Object DETECTED: ID " + obj.getObjectID() +
                                  " at X:" + (objectX - centerX) +
                                  " Y:" + (objectY - centerY) +
                                  " Alt:" + obj.getAltitude() + "m");
                    }
                } else {
                    currentlyDetectedIDsInBeam.remove(obj.getObjectID()); 
                }
            } else {
                if (currentlyDetectedIDsInBeam.contains(obj.getObjectID())) {
                    appendLog("Object " + obj.getObjectID() + " out of radar range.");
                    currentlyDetectedIDsInBeam.remove(obj.getObjectID());
                }
                obj.setCurrentlyDetectedByBeam(false); 
            }
        }
        
        updateDetectedObjectsList(); 
        repaint(); 
    }
}