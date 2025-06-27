// MainControlDashboard.java

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class MainControlDashboard extends JPanel {

    // References to UI components that RadarPanel needs to update
    private JTextArea detectedObjectsListArea;
    private JTextArea systemLogArea;
    private JTextArea threatNotificationArea; // NEW: For specific threat alerts

    // Reference to the RadarPanel
    private RadarPanel radarPanel;

    public MainControlDashboard() {
        // Use BorderLayout for the main dashboard layout
        super(new BorderLayout());
        setBackground(Color.BLACK); // Dark background for the whole dashboard

        // 1. Initialize JTextArea components for logging/display
        detectedObjectsListArea = createTextArea(20, 30, false); // Not editable by user
        systemLogArea = createTextArea(5, 80, false); // Not editable by user
        threatNotificationArea = createTextArea(10, 30, false); // NEW: For threat notifications

        // 2. Initialize RadarPanel, passing the required JTextAreas
        radarPanel = new RadarPanel(detectedObjectsListArea, systemLogArea);

        // 3. Set up the UI components for each section
        setupUI();
    }

    // Helper method to create styled JTextAreas
    private JTextArea createTextArea(int rows, int columns, boolean editable) {
        JTextArea textArea = new JTextArea(rows, columns);
        textArea.setBackground(new Color(20, 20, 20)); // Very dark grey background
        textArea.setForeground(new Color(0, 255, 0)); // Green text
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setEditable(editable);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 1)); // Thin green border
        return textArea;
    }

    // Sets up the main layout and adds sub-panels
    private void setupUI() {
        // Add padding around the main content
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- North: Top Panel (Logo + Status) ---
        add(createTopPanel(), BorderLayout.NORTH);

        // --- West: Left Panel (Navigation) ---
        add(createLeftPanel(), BorderLayout.WEST);

        // --- Center: Radar Panel ---
        // Wrap radarPanel in a JPanel with a border for visual separation
        JPanel radarContainer = new JPanel(new BorderLayout());
        radarContainer.setBackground(Color.BLACK);
        radarContainer.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 2)); // Thicker border
        radarContainer.add(radarPanel, BorderLayout.CENTER);
        add(radarContainer, BorderLayout.CENTER);

        // --- East: Right Panel (Threat Details & Notifications) ---
        add(createRightPanel(), BorderLayout.EAST);

        // --- South: Bottom Panel (System Log) ---
        add(createLogPanel(), BorderLayout.SOUTH);
    }

    // Creates the top panel with logo and status
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(15, 15, 15)); // Slightly lighter dark grey
        panel.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 1));
        panel.setPreferredSize(new Dimension(getWidth(), 60)); // Fixed height

        // SkyDefX Logo
        JLabel logoLabel = new JLabel("SKYDEFX", SwingConstants.LEFT);
        logoLabel.setFont(new Font("Monospaced", Font.BOLD, 28));
        logoLabel.setForeground(new Color(0, 200, 0)); // Bright green
        logoLabel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0)); // Padding

        // System Status
        JLabel statusLabel = new JLabel("Status: ACTIVE | Last Ping: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), SwingConstants.RIGHT);
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 14));
        statusLabel.setForeground(new Color(150, 150, 150)); // Greyish white
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15)); // Padding

        panel.add(logoLabel, BorderLayout.WEST);
        panel.add(statusLabel, BorderLayout.EAST);
        return panel;
    }

    // Creates the left navigation panel
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Vertical stacking of buttons
        panel.setBackground(new Color(15, 15, 15));
        panel.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 1));
        panel.setPreferredSize(new Dimension(180, getHeight())); // Fixed width

        // Add some spacing at the top
        panel.add(Box.createVerticalStrut(15));

        // Navigation Buttons
        String[] navButtons = {"RADAR", "THREAT LOGS", "DRONE FEEDS", "HISTORY", "LAUNCH PROTOCOL"};
        for (String text : navButtons) {
            JButton button = createNavButton(text);
            panel.add(button);
            panel.add(Box.createVerticalStrut(10)); // Spacing between buttons
        }

        // Add flexible space to push buttons to top
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    // Helper method to create styled navigation buttons
    private JButton createNavButton(String text) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.CENTER_ALIGNMENT); // Center buttons horizontally
        button.setMaximumSize(new Dimension(150, 40)); // Max size for consistent width
        button.setBackground(new Color(30, 30, 30)); // Dark button background
        button.setForeground(new Color(0, 255, 0)); // Green text
        button.setFont(new Font("Monospaced", Font.BOLD, 14));
        button.setBorder(BorderFactory.createLineBorder(new Color(0, 100, 0), 1)); // Green border
        button.setFocusPainted(false); // No focus border
        button.addActionListener(e -> appendLog("Navigation clicked: " + text)); // Simple action listener for now
        return button;
    }

    // Creates the right panel for detected objects and threat notifications
    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); // Vertical stacking
        panel.setBackground(new Color(15, 15, 15));
        panel.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 1));
        panel.setPreferredSize(new Dimension(300, getHeight())); // Fixed width

        panel.add(Box.createVerticalStrut(10)); // Top spacing

        // Detected Objects List
        JLabel detectedLabel = new JLabel("DETECTED OBJECTS", SwingConstants.CENTER);
        detectedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        detectedLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        detectedLabel.setForeground(new Color(0, 200, 0));
        panel.add(detectedLabel);
        panel.add(Box.createVerticalStrut(5));
        JScrollPane detectedScrollPane = new JScrollPane(detectedObjectsListArea);
        detectedScrollPane.setBorder(null); // No extra border on scroll pane
        panel.add(detectedScrollPane);
        
        panel.add(Box.createVerticalStrut(20)); // Spacing between sections

        // Threat Notifications
        JLabel threatLabel = new JLabel("SYSTEM NOTIFICATIONS", SwingConstants.CENTER);
        threatLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        threatLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        threatLabel.setForeground(new Color(255, 100, 0)); // Orange color for threats
        panel.add(threatLabel);
        panel.add(Box.createVerticalStrut(5));
        JScrollPane threatScrollPane = new JScrollPane(threatNotificationArea);
        threatScrollPane.setBorder(null);
        panel.add(threatScrollPane);

        panel.add(Box.createVerticalGlue()); // Push content to top
        return panel;
    }

    // Creates the bottom panel for the system log
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(15, 15, 15));
        panel.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 1));
        panel.setPreferredSize(new Dimension(getWidth(), 150)); // Fixed height

        JLabel logLabel = new JLabel("SYSTEM LOG", SwingConstants.CENTER);
        logLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        logLabel.setForeground(new Color(0, 200, 0));
        panel.add(logLabel, BorderLayout.NORTH);

        JScrollPane logScrollPane = new JScrollPane(systemLogArea);
        logScrollPane.setBorder(null); // No extra border on scroll pane
        panel.add(logScrollPane, BorderLayout.CENTER);

        return panel;
    }

    // Public method to append messages to the system log (for external use)
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            systemLogArea.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - " + message + "\n");
            systemLogArea.setCaretPosition(systemLogArea.getDocument().getLength());
        });
    }

    // Public method to append messages to the threat notification area
    public void appendNotification(String message) {
        SwingUtilities.invokeLater(() -> {
            threatNotificationArea.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - " + message + "\n");
            threatNotificationArea.setCaretPosition(threatNotificationArea.getDocument().getLength());
        });
    }

    // Optional: Getter for radar panel if external access is needed
    public RadarPanel getRadarPanel() {
        return radarPanel;
    }
}