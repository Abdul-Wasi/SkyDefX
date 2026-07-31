import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket; // NEW: Import ServerSocket
import java.net.Socket; // NEW: Import Socket
import java.util.concurrent.ExecutorService; // NEW: For server thread pool
import java.util.concurrent.Executors; // NEW: For server thread pool
import org.json.JSONObject; // Assuming you have org.json in your classpath

public class SkyDefXRadarSimulator extends JFrame implements KeyListener, ActionListener {

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private IdentityScreenPanel identityScreenPanel;
    private PasscodeScreenPanel passcodeScreenPanel;
    private InitializationSequencePanel initializationSequencePanel;
    private MainControlDashboard mainControlDashboard;

    // Remove DroneDetectionClient instance:
    // private DroneDetectionClient droneDetectionClient;

    // NEW: Server socket components
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader in;
    
    // NEW: Video socket components
    private ServerSocket videoServerSocket;
    private Socket videoClientSocket;
    private java.io.InputStream videoIn;

    private boolean serverRunning = false; // Flag to control server thread
    private ExecutorService serverExecutor; // Thread pool for server operations

    private static final int SERVER_PORT = 12345; // Port for Python script to connect to
    private static final int VIDEO_PORT = 12346;  // Port for Python video stream

    private static final String IDENTITY_SCREEN_CARD = "IdentityScreen";
    private static final String PASSCODE_SCREEN_CARD = "PasscodeScreen";
    private static final String INIT_SEQUENCE_CARD = "InitializationSequence";
    private static final String MAIN_DASHBOARD_CARD = "MainDashboard";

    private String userIdentity = "Guest";
    private String currentActiveCard;

    public SkyDefXRadarSimulator() {
        setTitle("SkyDefX Radar Simulator");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        identityScreenPanel = new IdentityScreenPanel(this);
        cardPanel.add(identityScreenPanel, IDENTITY_SCREEN_CARD);

        passcodeScreenPanel = new PasscodeScreenPanel(this);
        cardPanel.add(passcodeScreenPanel, PASSCODE_SCREEN_CARD);

        initializationSequencePanel = new InitializationSequencePanel(this);
        cardPanel.add(initializationSequencePanel, INIT_SEQUENCE_CARD);

        mainControlDashboard = new MainControlDashboard();
        cardPanel.add(mainControlDashboard, MAIN_DASHBOARD_CARD);

        add(cardPanel);

        cardLayout.show(cardPanel, IDENTITY_SCREEN_CARD);
        currentActiveCard = IDENTITY_SCREEN_CARD;

        addKeyListener(this);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        // Add WindowListener to stop server and sounds on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer(); // Stop the server when the window closes
                SoundPlayer.stopAllSounds();
                if (mainControlDashboard != null) {
                    mainControlDashboard.shutdown();
                }
                System.out.println("SkyDefXRadarSimulator: Application closing. Server, map threads, and sounds stopped.");
            }
        });

        // Initialize server executor service with a cached thread pool to run telemetry and video streams concurrently
        serverExecutor = Executors.newCachedThreadPool();
    }

    // --- NEW: Server Methods ---
    private void startServer() {
        if (serverRunning) {
            System.out.println("Java Radar Server is already running.");
            return;
        }

        serverRunning = true;
        
        // 1. Submit Telemetry JSON Server Socket Task
        serverExecutor.submit(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                System.out.println("Java Radar Server: Listening on port " + SERVER_PORT + " for Python telemetry...");

                while (serverRunning) {
                    try {
                        final Socket client = serverSocket.accept();
                        System.out.println("Java Radar Server: Python telemetry client connected from " + client.getInetAddress());

                        serverExecutor.submit(() -> {
                            try (BufferedReader clientIn = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                                String line;
                                while (serverRunning && (line = clientIn.readLine()) != null) {
                                    try {
                                        JSONObject json = new JSONObject(line);
                                        if (mainControlDashboard != null) {
                                            SwingUtilities.invokeLater(() -> mainControlDashboard.handlePythonDetection(json));
                                        }
                                    } catch (org.json.JSONException e) {
                                        System.err.println("Java Radar Server: Failed to parse JSON: " + line + " - " + e.getMessage());
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Java Radar Server: Telemetry client read error: " + e.getMessage());
                            } finally {
                                try { client.close(); } catch (IOException ignored) {}
                                System.out.println("Java Radar Server: Python telemetry client disconnected.");
                            }
                        });
                    } catch (IOException e) {
                        if (serverRunning) {
                            System.err.println("Java Radar Server: Error accepting telemetry client: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Java Radar Server: Could not start telemetry server on port " + SERVER_PORT + ": " + e.getMessage());
            } finally {
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                        System.out.println("Java Radar Server: Telemetry server socket closed.");
                    }
                } catch (IOException e) {
                    System.err.println("Java Radar Server: Error closing telemetry server socket: " + e.getMessage());
                }
            }
        });

        // 2. Submit Live Video Stream Server Socket Task
        serverExecutor.submit(() -> {
            try {
                videoServerSocket = new ServerSocket(VIDEO_PORT);
                System.out.println("Java Radar Server: Listening on port " + VIDEO_PORT + " for Python video stream...");

                while (serverRunning) {
                    try {
                        videoClientSocket = videoServerSocket.accept();
                        System.out.println("Java Radar Server: Python video client connected from " + videoClientSocket.getInetAddress());

                        videoIn = videoClientSocket.getInputStream();
                        java.io.DataInputStream dis = new java.io.DataInputStream(videoIn);
                        
                        if (mainControlDashboard != null && mainControlDashboard.getVideoFeedPanel() != null) {
                            mainControlDashboard.getVideoFeedPanel().setStatus("LIVE OPTICAL FEED ONLINE");
                        }

                        while (serverRunning) {
                            // Read JPEG frame byte length
                            int length = dis.readInt();
                            if (length <= 0 || length > 15 * 1024 * 1024) { // Limit to 15MB to prevent OOM
                                System.err.println("Java Radar Server: Invalid video frame length: " + length);
                                break;
                            }

                            byte[] imageBytes = new byte[length];
                            dis.readFully(imageBytes);

                            // Decode JPEG binary payload
                            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes);
                            BufferedImage img = javax.imageio.ImageIO.read(bais);

                            if (img != null && mainControlDashboard != null && mainControlDashboard.getVideoFeedPanel() != null) {
                                BufferedImage finalImg = img;
                                SwingUtilities.invokeLater(() -> mainControlDashboard.getVideoFeedPanel().updateFrame(finalImg));
                            }
                        }
                    } catch (IOException e) {
                        if (serverRunning) {
                            System.err.println("Java Radar Server: Video stream connection lost: " + e.getMessage());
                        }
                    } finally {
                        try {
                            if (videoIn != null) videoIn.close();
                            if (videoClientSocket != null) videoClientSocket.close();
                            System.out.println("Java Radar Server: Python video client disconnected.");
                        } catch (IOException e) {
                            System.err.println("Java Radar Server: Error closing video client resources: " + e.getMessage());
                        }
                        if (mainControlDashboard != null && mainControlDashboard.getVideoFeedPanel() != null) {
                            SwingUtilities.invokeLater(() -> mainControlDashboard.getVideoFeedPanel().setStatus("STANDBY: DISCONNECTED"));
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Java Radar Server: Could not start video server on port " + VIDEO_PORT + ": " + e.getMessage());
            } finally {
                try {
                    if (videoServerSocket != null && !videoServerSocket.isClosed()) {
                        videoServerSocket.close();
                        System.out.println("Java Radar Server: Video server socket closed.");
                    }
                } catch (IOException e) {
                    System.err.println("Java Radar Server: Error closing video server socket: " + e.getMessage());
                }
            }
        });
    }

    private void stopServer() {
        System.out.println("Java Radar Server: Stopping server...");
        serverRunning = false;
        if (serverExecutor != null) {
            serverExecutor.shutdownNow(); // Interrupt server threads
        }
        try {
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
            if (videoClientSocket != null) videoClientSocket.close();
            if (videoServerSocket != null) videoServerSocket.close();
        } catch (IOException e) {
            System.err.println("Java Radar Server: Error stopping server resources: " + e.getMessage());
        }
        System.out.println("Java Radar Server: Server stopped.");
    }

    // --- KeyListener Methods ---
    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        System.out.println("Key Pressed: " + KeyEvent.getKeyText(e.getKeyCode()) + " (currentActiveCard: " + currentActiveCard + ")");
        if (currentActiveCard.equals(INIT_SEQUENCE_CARD)) {
            if (initializationSequencePanel.isSequenceFinishedTyping() &&
                e.getKeyCode() == KeyEvent.VK_ENTER) {

                SoundPlayer.stopStartupSound();
                SoundPlayer.playSound("system_online_beep.wav");
                System.out.println("SkyDefXRadarSimulator: ENTER pressed. Playing system_online_beep.wav and proceeding to Main Dashboard.");

                System.out.println("Proceeding to Main Dashboard...");
                cardLayout.show(cardPanel, MAIN_DASHBOARD_CARD);
                currentActiveCard = MAIN_DASHBOARD_CARD;
                mainControlDashboard.requestFocusInWindow();

                // NEW: Start the server here instead of the client
                startServer();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    // --- ActionListener for callbacks from sub-panels ---
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("identity_confirmed")) {
            userIdentity = identityScreenPanel.getEnteredIdentity();
            if (userIdentity.isEmpty()) {
                userIdentity = "Guest";
            }
            System.out.println("User identified as: " + userIdentity);
            cardLayout.show(cardPanel, PASSCODE_SCREEN_CARD);
            currentActiveCard = PASSCODE_SCREEN_CARD;
        } else if (e.getActionCommand().equals("passcode_granted")) {
            System.out.println("Access granted. Proceeding to initialization.");
            cardLayout.show(cardPanel, INIT_SEQUENCE_CARD);
            currentActiveCard = INIT_SEQUENCE_CARD;
            SoundPlayer.playSound("system_startup.wav");
            initializationSequencePanel.startSequence();
        } else if (e.getActionCommand().equals("init_sequence_finished")) {
            System.out.println("Initialization sequence finished typing. Waiting for ENTER.");
        }
    }

    // --- Main Method ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SkyDefXRadarSimulator simulator = new SkyDefXRadarSimulator();
            simulator.setVisible(true);
        });
    }
}