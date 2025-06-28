// SkyDefXRadarSimulator.java

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter; // For closing resources
import java.awt.event.WindowEvent;   // For closing resources

public class SkyDefXRadarSimulator extends JFrame implements KeyListener, ActionListener {

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private IdentityScreenPanel identityScreenPanel;
    private PasscodeScreenPanel passcodeScreenPanel;
    private InitializationSequencePanel initializationSequencePanel;
    private MainControlDashboard mainControlDashboard;

    private DroneDetectionClient droneDetectionClient; // NEW: Drone Detection Client

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

        // NEW: Add WindowListener to stop client and sounds on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (droneDetectionClient != null && droneDetectionClient.isRunning()) {
                    droneDetectionClient.stopClient();
                }
                SoundPlayer.stopAllSounds(); // Ensure all sounds are stopped
                System.out.println("SkyDefXRadarSimulator: Application closing. Client and sounds stopped.");
            }
        });
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

                // Play the beep sound immediately before transitioning to dashboard
                SoundPlayer.stopStartupSound(); // Stop background startup sound
                SoundPlayer.playSound("system_online_beep.wav");
                System.out.println("SkyDefXRadarSimulator: ENTER pressed. Playing system_online_beep.wav and proceeding to Main Dashboard.");

                System.out.println("Proceeding to Main Dashboard...");
                cardLayout.show(cardPanel, MAIN_DASHBOARD_CARD);
                currentActiveCard = MAIN_DASHBOARD_CARD; // Update active card
                mainControlDashboard.requestFocusInWindow();

                // NEW: Start the drone detection client when dashboard is shown
                if (droneDetectionClient == null) {
                    // Pass a method reference to the dashboard's handler
                    droneDetectionClient = new DroneDetectionClient(mainControlDashboard::handlePythonDetection);
                }
                droneDetectionClient.startClient();
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
            SoundPlayer.playSound("system_startup.wav"); // Starts the background startup sound
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