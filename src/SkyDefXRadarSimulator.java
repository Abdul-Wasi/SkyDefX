// SkyDefXRadarSimulator.java (Updated for sound synchronization)

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class SkyDefXRadarSimulator extends JFrame implements KeyListener, ActionListener {

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private IdentityScreenPanel identityScreenPanel;
    private PasscodeScreenPanel passcodeScreenPanel;
    private InitializationSequencePanel initializationSequencePanel;
    private MainControlDashboard mainControlDashboard;

    private static final String IDENTITY_SCREEN_CARD = "IdentityScreen";
    private static final String PASSCODE_SCREEN_CARD = "PasscodeScreen";
    private static final String INIT_SEQUENCE_CARD = "InitializationSequence";
    private static final String MAIN_DASHBOARD_CARD = "MainDashboard";

    private String userIdentity = "Guest";
    private String currentActiveCard; // To keep track of the currently active card

    public SkyDefXRadarSimulator() {
        setTitle("SkyDefX Radar Simulator");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // --- Initialize Identity Screen ---
        identityScreenPanel = new IdentityScreenPanel(this);
        cardPanel.add(identityScreenPanel, IDENTITY_SCREEN_CARD);

        // --- Initialize Passcode Screen ---
        passcodeScreenPanel = new PasscodeScreenPanel(this);
        cardPanel.add(passcodeScreenPanel, PASSCODE_SCREEN_CARD);

        // --- Initialize Initialization Sequence Screen ---
        initializationSequencePanel = new InitializationSequencePanel(this);
        cardPanel.add(initializationSequencePanel, INIT_SEQUENCE_CARD);

        // --- Initialize Main Control Dashboard ---
        mainControlDashboard = new MainControlDashboard();
        cardPanel.add(mainControlDashboard, MAIN_DASHBOARD_CARD);

        add(cardPanel);

        // Show the Identity Screen initially and set the current active card
        cardLayout.show(cardPanel, IDENTITY_SCREEN_CARD);
        currentActiveCard = IDENTITY_SCREEN_CARD; // Initialize the tracking variable

        addKeyListener(this);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
    }

    // --- KeyListener Methods ---
    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        System.out.println("Key Pressed: " + KeyEvent.getKeyText(e.getKeyCode()) + " (currentActiveCard: " + currentActiveCard + ")");
        if (currentActiveCard.equals(INIT_SEQUENCE_CARD)) {
            System.out.println("On INIT_SEQUENCE_CARD. Is typing finished? " + initializationSequencePanel.isSequenceFinishedTyping());
            if (initializationSequencePanel.isSequenceFinishedTyping() &&
                e.getKeyCode() == KeyEvent.VK_ENTER) {

                // Play the beep sound immediately before transitioning to dashboard
                SoundPlayer.playSound("system_online_beep.wav");
                System.out.println("SkyDefXRadarSimulator: ENTER pressed. Playing system_online_beep.wav and proceeding to Main Dashboard.");
                
                System.out.println("Proceeding to Main Dashboard...");
                cardLayout.show(cardPanel, MAIN_DASHBOARD_CARD);
                currentActiveCard = MAIN_DASHBOARD_CARD; // Update active card
                mainControlDashboard.requestFocusInWindow();
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
            currentActiveCard = PASSCODE_SCREEN_CARD; // Update active card
        } else if (e.getActionCommand().equals("passcode_granted")) {
            System.out.println("Access granted. Proceeding to initialization.");
            cardLayout.show(cardPanel, INIT_SEQUENCE_CARD);
            currentActiveCard = INIT_SEQUENCE_CARD; // Update active card
            SoundPlayer.playSound("system_startup.wav"); // Starts the background startup sound
            initializationSequencePanel.startSequence();
        } else if (e.getActionCommand().equals("init_sequence_finished")) {
            System.out.println("Initialization sequence finished typing. Waiting for ENTER.");
            // No explicit action here; the KeyListener will handle the ENTER press.
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