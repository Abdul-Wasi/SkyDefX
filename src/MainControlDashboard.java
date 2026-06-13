// MainControlDashboard.java
import javax.swing.*;
import java.awt.*;
import org.json.JSONObject; // For handling Python detection data

public class MainControlDashboard extends JPanel {
    private RadarPanel radarPanel;
    private JTextArea detectedObjectsListArea;
    private JTextArea systemLogArea;

    // Tactical Threat Indicators
    private JLabel threatLevelLabel;
    private JLabel sectorStatsLabel;
    private JLabel targetKinematicsLabel;
    private JButton ackButton;

    public MainControlDashboard() {
        setLayout(new BorderLayout());
        setBackground(Color.DARK_GRAY);

        // --- Top Panel (Title) ---
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
        radarPanel = new RadarPanel(this, detectedObjectsListArea, systemLogArea);
        radarPanel.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 2)); // Stronger border for radar

        // --- Left Sidebar (Software-Defined Threat Panel) ---
        JPanel sidebarPanel = new JPanel();
        sidebarPanel.setPreferredSize(new Dimension(280, 0));
        sidebarPanel.setBackground(new Color(15, 15, 15));
        sidebarPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0, 80, 0), 1),
                "THREAT ASSESSMENT CORE",
                javax.swing.border.TitledBorder.CENTER,
                javax.swing.border.TitledBorder.TOP,
                new Font("Monospaced", Font.BOLD, 12),
                new Color(50, 255, 50)
        ));
        sidebarPanel.setLayout(new GridBagLayout());

        threatLevelLabel = new JLabel("THREAT LEVEL: GREEN", SwingConstants.CENTER);
        threatLevelLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        threatLevelLabel.setForeground(new Color(50, 255, 50));
        threatLevelLabel.setOpaque(true);
        threatLevelLabel.setBackground(new Color(0, 30, 0));
        threatLevelLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 100, 0), 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        sectorStatsLabel = new JLabel("<html><font color='#00ff00'>AIRSPACE SECTORS:</font><br>NE: 0 | SE: 0<br>NW: 0 | SW: 0</html>");
        sectorStatsLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        sectorStatsLabel.setForeground(Color.LIGHT_GRAY);
        sectorStatsLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0, 60, 0)), "SECTOR COUNT", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, new Font("Monospaced", Font.BOLD, 10), new Color(0, 200, 0)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        targetKinematicsLabel = new JLabel("<html><font color='#00ff00'>SELECTED TARGET:</font><br>ID: NONE<br>Range: N/A<br>Bearing: N/A<br>Speed: N/A<br>Altitude: N/A</html>");
        targetKinematicsLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        targetKinematicsLabel.setForeground(Color.LIGHT_GRAY);
        targetKinematicsLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0, 60, 0)), "KINEMATIC DATA", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, new Font("Monospaced", Font.BOLD, 10), new Color(0, 200, 0)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        ackButton = new JButton("ACKNOWLEDGE ALERT");
        ackButton.setFont(new Font("Monospaced", Font.BOLD, 12));
        ackButton.setBackground(new Color(40, 40, 40));
        ackButton.setForeground(Color.WHITE);
        ackButton.setFocusPainted(false);
        ackButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80), 2),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        ackButton.setEnabled(false);
        ackButton.addActionListener(e -> {
            radarPanel.acknowledgeSelectedTargetAlert();
        });

        JLabel legendLabel = new JLabel("<html><font color='#50ff50'>THREAT STATUS CODES:</font><br>"
            + "<font color='#00ff00'>&bull; GREEN</font> - Safe / Clear<br>"
            + "<font color='#ffaa00'>&bull; AMBER</font> - Warning (2500m)<br>"
            + "<font color='#ff3333'>&bull; RED</font> - Intrusion (1500m)<br>"
            + "<font color='#50ff50'>&bull; Vector Arrow</font> - Course & Velocity</html>");
        legendLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        legendLabel.setForeground(Color.GRAY);
        legendLabel.setBorder(BorderFactory.createEmptyBorder(15, 5, 5, 5));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
        sidebarPanel.add(threatLevelLabel, gbc);

        gbc.gridy = 1;
        sidebarPanel.add(sectorStatsLabel, gbc);

        gbc.gridy = 2;
        sidebarPanel.add(targetKinematicsLabel, gbc);

        gbc.gridy = 3;
        sidebarPanel.add(ackButton, gbc);

        gbc.gridy = 4;
        sidebarPanel.add(legendLabel, gbc);

        gbc.gridy = 5;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        sidebarPanel.add(new JPanel() {{ setOpaque(false); }}, gbc);

        // Add components to center panel
        centerPanel.add(radarPanel, BorderLayout.CENTER);
        centerPanel.add(detectedScrollPane, BorderLayout.EAST);
        centerPanel.add(sidebarPanel, BorderLayout.WEST);

        // Add system log at the bottom
        add(logScrollPane, BorderLayout.SOUTH);
    }

    public void updateThreatLevel(String level) {
        SwingUtilities.invokeLater(() -> {
            if ("RED".equals(level)) {
                threatLevelLabel.setText("THREAT: CRITICAL INTRUSION");
                boolean flash = (System.currentTimeMillis() / 500) % 2 == 0;
                threatLevelLabel.setForeground(flash ? Color.RED : Color.BLACK);
                threatLevelLabel.setBackground(flash ? new Color(255, 50, 50, 100) : Color.DARK_GRAY);
            } else if ("AMBER".equals(level)) {
                threatLevelLabel.setText("THREAT: WARNING (PROXIMITY)");
                threatLevelLabel.setForeground(new Color(255, 165, 0));
                threatLevelLabel.setBackground(new Color(40, 20, 0));
            } else {
                threatLevelLabel.setText("THREAT: GREEN (NORMAL)");
                threatLevelLabel.setForeground(new Color(50, 255, 50));
                threatLevelLabel.setBackground(new Color(0, 30, 0));
            }
        });
    }

    public void updateSectorStats(int ne, int se, int sw, int nw) {
        SwingUtilities.invokeLater(() -> {
            sectorStatsLabel.setText(String.format(
                "<html><font color='#00ff00'>AIRSPACE SECTORS:</font><br>" +
                "NE: %d | SE: %d<br>" +
                "NW: %d | SW: %d</html>",
                ne, se, nw, sw
            ));
        });
    }

    public void updateSelectedTarget(String id, double range, double bearing, double speed, int alt, String sector, boolean isAcked, String threat) {
        SwingUtilities.invokeLater(() -> {
            if (id == null) {
                targetKinematicsLabel.setText("<html><font color='#00ff00'>SELECTED TARGET:</font><br>ID: NONE<br>Range: N/A<br>Bearing: N/A<br>Speed: N/A<br>Altitude: N/A</html>");
                ackButton.setEnabled(false);
                ackButton.setText("ACKNOWLEDGE ALERT");
                ackButton.setBackground(new Color(40, 40, 40));
            } else {
                targetKinematicsLabel.setText(String.format(
                    "<html><font color='#00ff00'>SELECTED TARGET:</font><br>" +
                    "ID: %s<br>" +
                    "Range: %.1f m<br>" +
                    "Bearing: %.1f° (%s)<br>" +
                    "Speed: %.1f m/s<br>" +
                    "Altitude: %d m<br>" +
                    "Threat: <font color='%s'>%s</font></html>",
                    id, range, bearing, sector, speed, alt,
                    "RED".equals(threat) ? "#ff3333" : ("AMBER".equals(threat) ? "#ffaa00" : "#00ff00"),
                    isAcked ? "ACKNOWLEDGED" : threat
                ));
                if ("RED".equals(threat) && !isAcked) {
                    ackButton.setEnabled(true);
                    ackButton.setBackground(new Color(0, 120, 0));
                    ackButton.setText("ACKNOWLEDGE ALERT");
                } else {
                    ackButton.setEnabled(false);
                    ackButton.setText(isAcked ? "ALERT ACKNOWLEDGED" : "ACKNOWLEDGE ALERT");
                    ackButton.setBackground(new Color(40, 40, 40));
                }
            }
        });
    }

    public void handlePythonDetection(JSONObject droneData) {
        radarPanel.handlePythonDetection(droneData);
    }
}