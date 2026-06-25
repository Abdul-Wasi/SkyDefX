import javax.swing.*;
import java.awt.*;
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
    private boolean serverRunning = false; // Flag to control server thread
    private ExecutorService serverExecutor; // Thread pool for server operations

    private static final int SERVER_PORT = 12345; // Port for Python script to connect to

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

        // Initialize server executor service
        serverExecutor = Executors.newSingleThreadExecutor();
    }

    // --- NEW: Server Methods ---
    private void startServer() {
        if (serverRunning) {
            System.out.println("Java Radar Server is already running.");
            return;
        }

        serverRunning = true;
        serverExecutor.submit(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                System.out.println("Java Radar Server: Listening on port " + SERVER_PORT + " for Python connections...");

                while (serverRunning) {
                    try {
                        clientSocket = serverSocket.accept(); // This waits for Python to connect
                        System.out.println("Java Radar Server: Python client connected from " + clientSocket.getInetAddress());

                        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        String line;
                        while (serverRunning && (line = in.readLine()) != null) {
                            try {
                                JSONObject json = new JSONObject(line);
                                // System.out.println("Java Radar Server: Received JSON: " + json.toString()); // Uncomment for verbose debug
                                if (mainControlDashboard != null) {
                                    // Make sure to update UI on the Event Dispatch Thread
                                    SwingUtilities.invokeLater(() -> mainControlDashboard.handlePythonDetection(json));
                                }
                            } catch (org.json.JSONException e) {
                                System.err.println("Java Radar Server: Failed to parse JSON: " + line + " - " + e.getMessage());
                            }
                        }
                    } catch (IOException e) {
                        if (serverRunning) { // Only print error if server was supposed to be running
                            System.err.println("Java Radar Server: Error in client connection or reading: " + e.getMessage());
                        }
                    } finally {
                        // Close client resources if disconnected or error
                        try {
                            if (in != null) in.close();
                            if (clientSocket != null) clientSocket.close();
                            System.out.println("Java Radar Server: Python client disconnected.");
                        } catch (IOException e) {
                            System.err.println("Java Radar Server: Error closing client resources: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Java Radar Server: Could not start server on port " + SERVER_PORT + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Ensure server socket is closed when server stops
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                        System.out.println("Java Radar Server: Server socket closed.");
                    }
                } catch (IOException e) {
                    System.err.println("Java Radar Server: Error closing server socket: " + e.getMessage());
                }
                serverRunning = false;
            }
        });
    }

    private void stopServer() {
        System.out.println("Java Radar Server: Stopping server...");
        serverRunning = false;
        if (serverExecutor != null) {
            serverExecutor.shutdownNow(); // Interrupt server thread
        }
        try {
            if (clientSocket != null) clientSocket.close(); // Close any active client connection.
            if (serverSocket != null) serverSocket.close(); // Close the server socket.
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