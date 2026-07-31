// MainControlDashboard.java
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainControlDashboard extends JPanel {
    private RadarPanel radarPanel;
    private VideoFeedPanel videoFeedPanel; // NEW: Live Optical Video feed panel
    private JTextArea detectedObjectsListArea;
    private JTextArea systemLogArea;

    // Tactical Threat Indicators
    private JLabel threatLevelLabel;
    private JLabel sectorStatsLabel;
    private JLabel targetKinematicsLabel;
    private JButton ackButton;

    // Sidebar controls
    private JTextField searchField;
    private JTextField latField;
    private JTextField lonField;
    private JLabel zoomLabel;
    private JLabel resLabel;
    private JComboBox<String> presetBox;
    private JPanel westPanel;

    // Autocomplete fields
    private JPopupMenu suggestPopup;
    private Timer suggestTimer;
    private boolean isSelectingSuggestion = false;

    // Presets database: [Name, Lat, Lon]
    private final String[][] PRESETS = {
        {"Custom Coordinates", "", ""},
        {"Central Park, NY", "40.785091", "-73.968285"},
        {"The Pentagon, Arlington", "38.871856", "-77.056264"},
        {"Area 51, Nevada", "37.234333", "-115.806667"},
        {"London Eye, UK", "51.503300", "-0.119500"},
        {"Eiffel Tower, Paris", "48.858400", "2.294500"},
        {"Sydney Opera House", "-33.856800", "151.215300"},
        {"Taj Mahal, India", "27.175100", "78.042100"}
    };

    public MainControlDashboard() {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);

        // --- Top Panel (Header) ---
        JPanel topPanel = new JPanel();
        topPanel.setBackground(new Color(15, 15, 15));
        topPanel.setPreferredSize(new Dimension(getWidth(), 60));
        topPanel.setLayout(new BorderLayout());
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 100, 0)));

        // Create Config Toggle Button
        JButton configBtn = new JButton("[CONFIG NODE]");
        configBtn.setFont(new Font("Monospaced", Font.BOLD, 11));
        configBtn.setBackground(new Color(30, 30, 30));
        configBtn.setForeground(new Color(50, 255, 50));
        configBtn.setFocusPainted(false);
        configBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 100, 0), 1),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        configBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        configBtn.addActionListener(e -> {
            if (westPanel != null) {
                boolean isVisible = westPanel.isVisible();
                westPanel.setVisible(!isVisible);
                configBtn.setText(isVisible ? "[CONFIG NODE]" : "[HIDE CONFIG]");
                revalidate();
                repaint();
            }
        });

        JLabel titleLabel = new JLabel("SKYDEF-X: TACTICAL RADAR VISUALIZER");
        titleLabel.setForeground(new Color(50, 255, 50));
        titleLabel.setFont(new Font("Monospaced", Font.BOLD, 18));

        JPanel leftHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        leftHeaderPanel.setOpaque(false);
        leftHeaderPanel.add(configBtn);
        leftHeaderPanel.add(titleLabel);
        topPanel.add(leftHeaderPanel, BorderLayout.WEST);
        
        JLabel subtitleLabel = new JLabel("STATUS: SYSTEM ACTIVE  ");
        subtitleLabel.setForeground(new Color(0, 200, 0));
        subtitleLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        topPanel.add(subtitleLabel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // --- Bottom Panel (System Log) ---
        systemLogArea = new JTextArea();
        systemLogArea.setEditable(false);
        systemLogArea.setBackground(new Color(10, 15, 10));
        systemLogArea.setForeground(new Color(50, 255, 50));
        systemLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        systemLogArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JScrollPane logScrollPane = new JScrollPane(systemLogArea);
        logScrollPane.setPreferredSize(new Dimension(0, 160));
        
        // Stylish titled border for logs
        TitledBorder logBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(0, 80, 0), 1),
            " SYSTEM CONSOLE LOGS "
        );
        logBorder.setTitleColor(new Color(0, 200, 0));
        logBorder.setTitleFont(new Font("Monospaced", Font.BOLD, 12));
        logScrollPane.setBorder(logBorder);

        add(logScrollPane, BorderLayout.SOUTH);

        // --- Central Panel (Split Radar & Target List) ---
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(Color.BLACK);
        add(centerPanel, BorderLayout.CENTER);

        // Target List Area on the East
        detectedObjectsListArea = new JTextArea();
        detectedObjectsListArea.setEditable(false);
        detectedObjectsListArea.setBackground(new Color(10, 12, 10));
        detectedObjectsListArea.setForeground(new Color(50, 255, 50));
        detectedObjectsListArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        detectedObjectsListArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JScrollPane detectedScrollPane = new JScrollPane(detectedObjectsListArea);
        detectedScrollPane.setPreferredSize(new Dimension(400, 0));
        
        TitledBorder targetBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(0, 80, 0), 1),
            " LIVE TARGET TRACKING "
        );
        targetBorder.setTitleColor(new Color(0, 200, 0));
        targetBorder.setTitleFont(new Font("Monospaced", Font.BOLD, 12));
        detectedScrollPane.setBorder(targetBorder);

        // Create the Radar Panel, passing the dashboard reference, detected area, and log area
        radarPanel = new RadarPanel(this, detectedObjectsListArea, systemLogArea);
        radarPanel.setBorder(BorderFactory.createLineBorder(new Color(0, 80, 0), 2)); // Stronger border for radar

        // Listen to coordinates moved via click & drag panning
        radarPanel.setOnCoordinatesMoved((lat, lon) -> {
            latField.setText(String.format("%.6f", lat));
            lonField.setText(String.format("%.6f", lon));
            presetBox.setSelectedIndex(0); // Set to "Custom"
        });

        // --- Left Sidebar (Software-Defined Threat Panel) ---
        JPanel sidebarPanel = new JPanel();
        sidebarPanel.setPreferredSize(new Dimension(220, 0));
        sidebarPanel.setBackground(new Color(15, 15, 15));
        sidebarPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0, 80, 0), 1),
                "THREAT ASSESS CORE",
                javax.swing.border.TitledBorder.CENTER,
                javax.swing.border.TitledBorder.TOP,
                new Font("Monospaced", Font.BOLD, 11),
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

        targetKinematicsLabel = new JLabel("<html><font color='#00ff00'>SELECTED TARGET:</font><br>ID: NONE<br>Range: N/A<br>Bearing: N/A<br>Speed: N/A<br>Altitude: N/A<br>Source: N/A</html>");
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

        JPanel eastPanel = new JPanel(new BorderLayout());
        eastPanel.setPreferredSize(new Dimension(320, 0));
        eastPanel.setBackground(Color.BLACK);
        eastPanel.add(detectedScrollPane, BorderLayout.CENTER);
        
        videoFeedPanel = new VideoFeedPanel();
        eastPanel.add(videoFeedPanel, BorderLayout.SOUTH);
        
        centerPanel.add(radarPanel, BorderLayout.CENTER);
        centerPanel.add(eastPanel, BorderLayout.EAST);
        centerPanel.add(sidebarPanel, BorderLayout.WEST);

        // --- Left Panel (West - Radar Node & Map Controls) ---
        westPanel = new JPanel();
        westPanel.setVisible(false); // Collapsed by default for a clean user experience!
        westPanel.setLayout(new BoxLayout(westPanel, BoxLayout.Y_AXIS));
        westPanel.setPreferredSize(new Dimension(280, 0));
        westPanel.setBackground(new Color(20, 22, 20));
        
        TitledBorder sidebarBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(0, 80, 0), 1),
            " RADAR NODE DEPLOYMENT "
        );
        sidebarBorder.setTitleColor(new Color(0, 200, 0));
        sidebarBorder.setTitleFont(new Font("Monospaced", Font.BOLD, 12));
        westPanel.setBorder(sidebarBorder);

        // Add padding around components
        westPanel.add(Box.createVerticalStrut(15));

        // 0. Search Location Field
        JLabel searchLabel = new JLabel("SEARCH ADDRESS / BUILDING:");
        searchLabel.setForeground(new Color(0, 200, 0));
        searchLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        JPanel searchLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchLabelPanel.setBackground(new Color(20, 22, 20));
        searchLabelPanel.add(searchLabel);
        westPanel.add(searchLabelPanel);

        searchField = new JTextField(14);
        searchField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        searchField.setBackground(Color.BLACK);
        searchField.setForeground(new Color(50, 255, 50));
        searchField.setCaretColor(new Color(50, 255, 50));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 80, 0), 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        searchField.setMaximumSize(new Dimension(250, 30));

        JButton searchBtn = new JButton("GO");
        searchBtn.setFont(new Font("Monospaced", Font.BOLD, 12));
        searchBtn.setBackground(new Color(0, 60, 0));
        searchBtn.setForeground(new Color(50, 255, 50));
        searchBtn.setFocusPainted(false);
        searchBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 150, 0), 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        searchBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel searchControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        searchControlPanel.setBackground(new Color(20, 22, 20));
        searchControlPanel.add(searchField);
        searchControlPanel.add(searchBtn);
        westPanel.add(searchControlPanel);

        // Initialize Autocomplete Suggestion Popup & Debounce Timer
        suggestPopup = new JPopupMenu();
        suggestPopup.setBackground(new Color(15, 20, 15));
        suggestPopup.setBorder(BorderFactory.createLineBorder(new Color(0, 120, 0), 1));

        suggestTimer = new Timer(400, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                suggestTimer.stop();
                String text = searchField.getText().trim();
                if (text.length() >= 3) {
                    fetchSuggestions(text);
                } else {
                    suggestPopup.setVisible(false);
                }
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { restartTimer(); }
            @Override
            public void removeUpdate(DocumentEvent e) { restartTimer(); }
            @Override
            public void changedUpdate(DocumentEvent e) { restartTimer(); }

            private void restartTimer() {
                if (isSelectingSuggestion) return;
                suggestTimer.restart();
            }
        });

        westPanel.add(Box.createVerticalStrut(15));

        // 1. Presets Selector
        JLabel presetLabel = new JLabel("SELECT DEPLOYMENT SITE:");
        presetLabel.setForeground(new Color(0, 200, 0));
        presetLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        presetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel presetLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presetLabelPanel.setBackground(new Color(20, 22, 20));
        presetLabelPanel.add(presetLabel);
        westPanel.add(presetLabelPanel);

        String[] presetNames = new String[PRESETS.length];
        for (int i = 0; i < PRESETS.length; i++) {
            presetNames[i] = PRESETS[i][0];
        }
        presetBox = new JComboBox<>(presetNames);
        presetBox.setFont(new Font("Monospaced", Font.PLAIN, 12));
        presetBox.setBackground(Color.BLACK);
        presetBox.setForeground(new Color(50, 255, 50));
        presetBox.setBorder(BorderFactory.createLineBorder(new Color(0, 100, 0), 1));
        presetBox.setMaximumSize(new Dimension(250, 30));
        presetBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPanel comboPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        comboPanel.setBackground(new Color(20, 22, 20));
        comboPanel.add(presetBox);
        westPanel.add(comboPanel);

        westPanel.add(Box.createVerticalStrut(15));

        // 2. Latitude Field
        JLabel latLabel = new JLabel("LATITUDE (Decimal):");
        latLabel.setForeground(new Color(0, 200, 0));
        latLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        JPanel latLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        latLabelPanel.setBackground(new Color(20, 22, 20));
        latLabelPanel.add(latLabel);
        westPanel.add(latLabelPanel);

        latField = new JTextField("40.785091", 15);
        latField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        latField.setBackground(Color.BLACK);
        latField.setForeground(new Color(50, 255, 50));
        latField.setCaretColor(new Color(50, 255, 50));
        latField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 80, 0), 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        latField.setMaximumSize(new Dimension(250, 30));
        JPanel latTextPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        latTextPanel.setBackground(new Color(20, 22, 20));
        latTextPanel.add(latField);
        westPanel.add(latTextPanel);

        westPanel.add(Box.createVerticalStrut(10));

        // 3. Longitude Field
        JLabel lonLabel = new JLabel("LONGITUDE (Decimal):");
        lonLabel.setForeground(new Color(0, 200, 0));
        lonLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        JPanel lonLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lonLabelPanel.setBackground(new Color(20, 22, 20));
        lonLabelPanel.add(lonLabel);
        westPanel.add(lonLabelPanel);

        lonField = new JTextField("-73.968285", 15);
        lonField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        lonField.setBackground(Color.BLACK);
        lonField.setForeground(new Color(50, 255, 50));
        lonField.setCaretColor(new Color(50, 255, 50));
        lonField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 80, 0), 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        lonField.setMaximumSize(new Dimension(250, 30));
        JPanel lonTextPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lonTextPanel.setBackground(new Color(20, 22, 20));
        lonTextPanel.add(lonField);
        westPanel.add(lonTextPanel);

        westPanel.add(Box.createVerticalStrut(10));

        // GPS Sync Button
        JButton getLocBtn = new JButton("SYNC LIVE LOCATION [GPS]");
        getLocBtn.setFont(new Font("Monospaced", Font.BOLD, 12));
        getLocBtn.setBackground(new Color(25, 25, 25));
        getLocBtn.setForeground(new Color(255, 140, 0)); // Orange for GPS sync
        getLocBtn.setFocusPainted(false);
        getLocBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(150, 80, 0), 1),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        getLocBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        JPanel getLocPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        getLocPanel.setBackground(new Color(20, 22, 20));
        getLocPanel.add(getLocBtn);
        westPanel.add(getLocPanel);

        westPanel.add(Box.createVerticalStrut(15));

        // 4. Deploy Button
        JButton deployBtn = new JButton("DEPLOY RADAR NODE");
        deployBtn.setFont(new Font("Monospaced", Font.BOLD, 12));
        deployBtn.setBackground(new Color(0, 60, 0));
        deployBtn.setForeground(new Color(50, 255, 50));
        deployBtn.setFocusPainted(false);
        deployBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 150, 0), 1),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        deployBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.setBackground(new Color(20, 22, 20));
        btnPanel.add(deployBtn);
        westPanel.add(btnPanel);

        westPanel.add(Box.createVerticalStrut(25));
        westPanel.add(new JSeparator(JSeparator.HORIZONTAL));
        westPanel.add(Box.createVerticalStrut(20));

        // 5. Map Settings (Zoom)
        JLabel zoomTitle = new JLabel("TACTICAL RANGE & ZOOM:");
        zoomTitle.setForeground(new Color(0, 200, 0));
        zoomTitle.setFont(new Font("Monospaced", Font.BOLD, 12));
        JPanel zoomTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        zoomTitlePanel.setBackground(new Color(20, 22, 20));
        zoomTitlePanel.add(zoomTitle);
        westPanel.add(zoomTitlePanel);

        SatelliteMapManager mapManager = radarPanel.getMapManager();

        zoomLabel = new JLabel("MAP ZOOM: " + mapManager.getZoom());
        zoomLabel.setForeground(Color.WHITE);
        zoomLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JPanel zoomLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        zoomLabelPanel.setBackground(new Color(20, 22, 20));
        zoomLabelPanel.add(zoomLabel);
        westPanel.add(zoomLabelPanel);

        resLabel = new JLabel(String.format("SCALE: %.2f m/px", mapManager.getMetersPerPixel()));
        resLabel.setForeground(new Color(150, 150, 150));
        resLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JPanel resLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resLabelPanel.setBackground(new Color(20, 22, 20));
        resLabelPanel.add(resLabel);
        westPanel.add(resLabelPanel);

        westPanel.add(Box.createVerticalStrut(10));

        // Zoom Buttons panel
        JPanel zoomButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        zoomButtonsPanel.setBackground(new Color(20, 22, 20));

        JButton zoomInBtn = new JButton(" ZOOM + ");
        zoomInBtn.setFont(new Font("Monospaced", Font.BOLD, 12));
        zoomInBtn.setBackground(new Color(20, 20, 20));
        zoomInBtn.setForeground(new Color(50, 255, 50));
        zoomInBtn.setFocusPainted(false);
        zoomInBtn.setBorder(BorderFactory.createLineBorder(new Color(0, 100, 0), 1));
        zoomInBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton zoomOutBtn = new JButton(" ZOOM - ");
        zoomOutBtn.setFont(new Font("Monospaced", Font.BOLD, 12));
        zoomOutBtn.setBackground(new Color(20, 20, 20));
        zoomOutBtn.setForeground(new Color(50, 255, 50));
        zoomOutBtn.setFocusPainted(false);
        zoomOutBtn.setBorder(BorderFactory.createLineBorder(new Color(0, 100, 0), 1));
        zoomOutBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        zoomButtonsPanel.add(zoomInBtn);
        zoomButtonsPanel.add(zoomOutBtn);
        westPanel.add(zoomButtonsPanel);

        add(westPanel, BorderLayout.WEST);

        // --- Action Listeners ---

        // Preset combobox change listener
        presetBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int index = presetBox.getSelectedIndex();
                if (index > 0) { // If not custom
                    latField.setText(PRESETS[index][1]);
                    lonField.setText(PRESETS[index][2]);
                    
                    // Trigger deployment
                    applyDeployment();
                }
            }
        });

        // Search execution listener
        ActionListener searchAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String query = searchField.getText().trim();
                if (!query.isEmpty()) {
                    searchBtn.setEnabled(false);
                    searchField.setEnabled(false);
                    radarPanel.appendLog("Searching address: '" + query + "'...");
                    new Thread(() -> {
                        double[] coords = searchLocation(query);
                        SwingUtilities.invokeLater(() -> {
                            searchBtn.setEnabled(true);
                            searchField.setEnabled(true);
                            if (coords != null) {
                                latField.setText(String.format("%.6f", coords[0]));
                                lonField.setText(String.format("%.6f", coords[1]));
                                presetBox.setSelectedIndex(0);
                                applyDeployment();
                            } else {
                                radarPanel.appendLog("SEARCH FAILED: Location not found.");
                            }
                        });
                    }).start();
                }
            }
        };
        searchBtn.addActionListener(searchAction);
        searchField.addActionListener(searchAction);

        // Live location button listener
        getLocBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getLocBtn.setEnabled(false);
                getLocBtn.setText("SYNCING GPS...");
                radarPanel.appendLog("Initiating native OS GPS lookup...");

                new Thread(() -> {
                    double[] coords = getNativeOSLocation();
                    
                    if (coords == null) {
                        radarPanel.appendLog("GPS: Native OS Location unavailable/blocked. Trying IP Geolocation fallback...");
                        coords = getLiveCoordinates(); // Fallback to IP
                    }

                    final double[] finalCoords = coords;
                    SwingUtilities.invokeLater(() -> {
                        getLocBtn.setEnabled(true);
                        getLocBtn.setText("SYNC LIVE LOCATION [GPS]");
                        if (finalCoords != null) {
                            latField.setText(String.format("%.6f", finalCoords[0]));
                            lonField.setText(String.format("%.6f", finalCoords[1]));
                            presetBox.setSelectedIndex(0); // Custom Coordinates
                            applyDeployment();
                        } else {
                            radarPanel.appendLog("GPS SYNC ERROR: Geolocation lookup failed. Check network connection.");
                        }
                    });
                }).start();
            }
        });

        // Deploy button listener
        deployBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                presetBox.setSelectedIndex(0); // Reset to "Custom" when manual button clicked
                applyDeployment();
            }
        });

        // Zoom In button listener
        zoomInBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int currentZoom = mapManager.getZoom();
                if (currentZoom < 19) {
                    mapManager.setZoom(currentZoom + 1);
                    updateZoomReadout();
                    radarPanel.appendLog("Tactical Zoom In. Level: " + mapManager.getZoom());
                }
            }
        });

        // Zoom Out button listener
        zoomOutBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int currentZoom = mapManager.getZoom();
                if (currentZoom > 1) {
                    mapManager.setZoom(currentZoom - 1);
                    updateZoomReadout();
                    radarPanel.appendLog("Tactical Zoom Out. Level: " + mapManager.getZoom());
                }
            }
        });

        // Set default map coordinates on init
        SwingUtilities.invokeLater(() -> {
            applyDeployment();
        });
    }

    private void applyDeployment() {
        try {
            double lat = Double.parseDouble(latField.getText().trim());
            double lon = Double.parseDouble(lonField.getText().trim());

            if (lat < -85.0 || lat > 85.0 || lon < -180.0 || lon > 180.0) {
                radarPanel.appendLog("DEPLOYMENT FAILED: Coordinates out of bounds (Lat: -85 to 85, Lon: -180 to 180).");
                return;
            }

            radarPanel.getMapManager().setCenter(lat, lon);
            updateZoomReadout();
            radarPanel.appendLog(String.format("Tactical deployment locked to coordinates: Lat %.6f, Lon %.6f", lat, lon));

        } catch (NumberFormatException ex) {
            radarPanel.appendLog("DEPLOYMENT FAILED: Invalid decimal coordinates format.");
        }
    }

    private void updateZoomReadout() {
        SatelliteMapManager mapManager = radarPanel.getMapManager();
        zoomLabel.setText("MAP ZOOM: " + mapManager.getZoom());
        resLabel.setText(String.format("SCALE: %.2f m/px", mapManager.getMetersPerPixel()));
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

    public void updateSelectedTarget(String id, double range, double bearing, double speed, int alt, String sector, boolean isAcked, String threat, String fusionSource) {
        SwingUtilities.invokeLater(() -> {
            if (id == null) {
                targetKinematicsLabel.setText("<html><font color='#00ff00'>SELECTED TARGET:</font><br>ID: NONE<br>Range: N/A<br>Bearing: N/A<br>Speed: N/A<br>Altitude: N/A<br>Source: N/A</html>");
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
                    "Source: <font color='%s'>%s</font><br>" +
                    "Threat: <font color='%s'>%s</font></html>",
                    id, range, bearing, sector, speed, alt,
                    "LIDAR".equals(fusionSource) ? "#00ffff" : ("ULTRASONIC".equals(fusionSource) ? "#ff00ff" : "#ffffff"),
                    fusionSource,
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

    public void shutdown() {
        if (radarPanel != null && radarPanel.getMapManager() != null) {
            radarPanel.getMapManager().shutdown();
        }
    }

    // --- Geocoding lookup via OpenStreetMap Nominatim ---
    private double[] searchLocation(String query) {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            URL url = new URL("https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "SkyDefX-Radar-Simulator");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder content = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    org.json.JSONArray jsonArray = new org.json.JSONArray(content.toString());
                    if (jsonArray.length() > 0) {
                        JSONObject first = jsonArray.getJSONObject(0);
                        double lat = Double.parseDouble(first.getString("lat"));
                        double lon = Double.parseDouble(first.getString("lon"));
                        String displayName = first.optString("display_name", "");
                        radarPanel.appendLog("Geocoded location: " + displayName);
                        return new double[]{lat, lon};
                    }
                }
            } else {
                System.err.println("Nominatim search returned code " + status);
            }
        } catch (Exception e) {
            System.err.println("Geocoding failed: " + e.getMessage());
        }
        return null;
    }

    // --- Native GeoCoordinateWatcher (PowerShell interface) ---
    private double[] getNativeOSLocation() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-ExecutionPolicy", "Bypass", "-File", ".cache/get_location.ps1"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                String lastLine = null;
                while ((line = reader.readLine()) != null) {
                    lastLine = line.trim();
                }
                
                if (lastLine != null && !lastLine.startsWith("ERROR") && lastLine.contains(",")) {
                    String[] parts = lastLine.split(",");
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    radarPanel.appendLog("GPS: Native OS GPS lock acquired.");
                    return new double[]{lat, lon};
                } else if (lastLine != null) {
                    System.out.println("OS Geolocation Watcher output: " + lastLine);
                }
            }
            p.waitFor();
        } catch (Exception e) {
            System.err.println("PowerShell location execution error: " + e.getMessage());
        }
        return null;
    }

    // --- Geolocation IP APIs ---
    private double[] getLiveCoordinates() {
        try {
            return fetchLocationFromIpapiCo();
        } catch (Exception e1) {
            System.out.println("ipapi.co failed: " + e1.getMessage() + ". Trying ip-api.com...");
            try {
                return fetchLocationFromIpApi();
            } catch (Exception e2) {
                System.out.println("ip-api.com failed: " + e2.getMessage() + ". Trying freeipapi.com...");
                try {
                    return fetchLocationFromFreeIpApi();
                } catch (Exception e3) {
                    System.err.println("All location services failed.");
                    return null;
                }
            }
        }
    }

    private double[] fetchLocationFromIpapiCo() throws Exception {
        URL url = new URL("https://ipapi.co/json/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int status = conn.getResponseCode();
        if (status == 200) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                JSONObject json = new JSONObject(content.toString());
                if (json.has("latitude") && json.has("longitude")) {
                    return new double[]{json.getDouble("latitude"), json.getDouble("longitude")};
                }
            }
        }
        throw new IOException("ipapi.co returned code " + status);
    }

    private double[] fetchLocationFromIpApi() throws Exception {
        URL url = new URL("http://ip-api.com/json/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int status = conn.getResponseCode();
        if (status == 200) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                JSONObject json = new JSONObject(content.toString());
                if ("success".equals(json.optString("status"))) {
                    return new double[]{json.getDouble("lat"), json.getDouble("lon")};
                }
            }
        }
        throw new IOException("ip-api.com returned code " + status);
    }

    private double[] fetchLocationFromFreeIpApi() throws Exception {
        URL url = new URL("https://freeipapi.com/api/json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int status = conn.getResponseCode();
        if (status == 200) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                JSONObject json = new JSONObject(content.toString());
                return new double[]{json.getDouble("latitude"), json.getDouble("longitude")};
            }
        }
        throw new IOException("freeipapi.com returned code " + status);
    }

    private void fetchSuggestions(String query) {
        new Thread(() -> {
            try {
                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                URL url = new URL("https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=5");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "SkyDefX-Radar-Simulator");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int status = conn.getResponseCode();
                if (status == 200) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        StringBuilder content = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            content.append(inputLine);
                        }
                        org.json.JSONArray jsonArray = new org.json.JSONArray(content.toString());
                        
                        SwingUtilities.invokeLater(() -> {
                            suggestPopup.removeAll();
                            if (jsonArray.length() > 0) {
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    JSONObject item = jsonArray.getJSONObject(i);
                                    String name = item.getString("display_name");
                                    double lat = Double.parseDouble(item.getString("lat"));
                                    double lon = Double.parseDouble(item.getString("lon"));
                                    
                                    String shortName = name;
                                    if (shortName.length() > 45) {
                                        shortName = shortName.substring(0, 42) + "...";
                                    }
                                    
                                    JMenuItem menuItem = new JMenuItem(shortName);
                                    menuItem.setFont(new Font("Monospaced", Font.PLAIN, 11));
                                    menuItem.setBackground(new Color(15, 20, 15));
                                    menuItem.setForeground(new Color(50, 255, 50));
                                    menuItem.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                                    
                                    menuItem.addActionListener(e -> {
                                        isSelectingSuggestion = true;
                                        searchField.setText(name);
                                        latField.setText(String.format("%.6f", lat));
                                        lonField.setText(String.format("%.6f", lon));
                                        presetBox.setSelectedIndex(0);
                                        applyDeployment();
                                        suggestPopup.setVisible(false);
                                        isSelectingSuggestion = false;
                                    });
                                    suggestPopup.add(menuItem);
                                }
                                if (searchField.isShowing() && searchField.hasFocus()) {
                                    suggestPopup.show(searchField, 0, searchField.getHeight());
                                    searchField.requestFocusInWindow();
                                }
                            } else {
                                suggestPopup.setVisible(false);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("Suggestion fetch failed: " + e.getMessage());
            }
        }).start();
    }

    public VideoFeedPanel getVideoFeedPanel() {
        return videoFeedPanel;
    }
}