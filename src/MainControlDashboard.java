// MainControlDashboard.java
import javax.swing.*;
import java.awt.*;
import org.json.JSONObject; // For handling Python detection data

public class MainControlDashboard extends JPanel {
    private RadarPanel radarPanel;
    private JTextArea detectedObjectsListArea;
    private JTextArea systemLogArea;

    public MainControlDashboard() {
        setLayout(new BorderLayout());
        setBackground(Color.DARK_GRAY);

        // --- Top Panel (placeholder for controls) ---
        JPanel topPanel = new JPanel();
        topPanel.setBackground(new Color(30, 30, 30));
        topPanel.setPreferredSize(new Dimension(getWidth(), 50));
        topPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JLabel titleLabel = new JLabel("SKYDEF-X CONTROL INTERFACE");
        titleLabel.setForeground(new Color(50, 255, 50));
        titleLabel.setFont(new Font("Monospaced", Font.BOLD, 20));
        topPanel.add(titleLabel);
        add(topPanel, BorderLayout.NORTH);

        // --- Central Panel for Radar and Information ---
        JPanel centerPanel = new JPanel(new BorderLayout());
        add(centerPanel, BorderLayout.CENTER);

        // Initialize JTextAreas for dashboard output
        detectedObjectsListArea = new JTextArea();
        detectedObjectsListArea.setEditable(false);
        detectedObjectsListArea.setBackground(new Color(20, 20, 20));
        detectedObjectsListArea.setForeground(new Color(50, 255, 50));
        detectedObjectsListArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        detectedObjectsListArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane detectedScrollPane = new JScrollPane(detectedObjectsListArea);
        detectedScrollPane.setPreferredSize(new Dimension(300, 0)); // Fixed width for detection list
        detectedScrollPane.setBorder(BorderFactory.createLineBorder(new Color(0, 60, 0), 1)); // Border for aesthetics

        systemLogArea = new JTextArea();
        systemLogArea.setEditable(false);
        systemLogArea.setBackground(new Color(20, 20, 20));
        systemLogArea.setForeground(new Color(50, 255, 50));
        systemLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        systemLogArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane logScrollPane = new JScrollPane(systemLogArea);
        logScrollPane.setPreferredSize(new Dimension(0, 150)); // Fixed height for log
        logScrollPane.setBorder(BorderFactory.createLineBorder(new Color(0, 60, 0), 1));

        // Create the RadarPanel, passing the JTextArea references
        radarPanel = new RadarPanel(detectedObjectsListArea, systemLogArea);
        radarPanel.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 2)); // Stronger border for radar

        // Add components to center panel
        centerPanel.add(radarPanel, BorderLayout.CENTER);
        centerPanel.add(detectedScrollPane, BorderLayout.EAST);

        // Add system log at the bottom
        add(logScrollPane, BorderLayout.SOUTH);
    }

    /**
     * Public method to receive drone detection data from the client.
     * This method will be called by the DroneDetectionClient.
     * @param droneData JSONObject containing "id", "azimuth", "elevation", "distance"
     */
    public void handlePythonDetection(JSONObject droneData) {
        radarPanel.handlePythonDetection(droneData);
    }
}