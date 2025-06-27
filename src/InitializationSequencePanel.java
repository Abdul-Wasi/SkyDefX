// InitializationSequencePanel.java (Updated for sound synchronization)

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;

public class InitializationSequencePanel extends JPanel implements ActionListener {

    private Timer typewriterTimer;
    private Queue<String> messagesToType;
    private String currentDisplayedText = "";
    private String currentFullMessage = "";
    private int currentCharIndex = 0;

    private List<String> fullyTypedMessages = new ArrayList<>();

    private boolean sequenceFinishedTyping = false;
    private ActionListener sequenceCompletedListener;

    private final String[] INIT_MESSAGES_ARRAY = {
        "> Initializing SkyDefX...",
        "Connecting to secure network...", // Changed "Establishing command link..."
        "Running diagnostics and integrity checks...", // Changed "Securing protocols..."
        "> SYSTEM ONLINE \u2705",
        "Please Proceed..." // This is the last message
    };


    public InitializationSequencePanel(ActionListener listener) {
        this.sequenceCompletedListener = listener;

        setBackground(Color.BLACK);
        setForeground(new Color(0, 255, 0));
        setFont(new Font("Monospaced", Font.PLAIN, 20));

        messagesToType = new LinkedList<>();
    }

    public void startSequence() {
        messagesToType.clear();
        for (String msg : INIT_MESSAGES_ARRAY) {
            messagesToType.add(msg);
        }
        fullyTypedMessages.clear();
        currentDisplayedText = ""; // Ensure it's clear at start
        currentFullMessage = ""; // Ensure it's clear at start
        currentCharIndex = 0;
        sequenceFinishedTyping = false;
        
        startNextMessage();

        if (typewriterTimer == null) {
            typewriterTimer = new Timer(70, this);
        }
        if (!typewriterTimer.isRunning()) {
             typewriterTimer.start();
        }
        repaint();
    }

    private void startNextMessage() {
        if (!messagesToType.isEmpty()) {
            currentFullMessage = messagesToType.poll();
            currentDisplayedText = "";
            currentCharIndex = 0;

            // NEW: Stop the system_startup.wav when "Please Proceed..." begins
            if (currentFullMessage.equals("Please Proceed...")) {
                SoundPlayer.stopStartupSound();
                System.out.println("InitializationSequencePanel: 'Please Proceed...' message started. Stopping background startup sound.");
            }

        } else {
            typewriterTimer.stop(); // Stop the typewriter timer
            sequenceFinishedTyping = true;

            // REMOVED: SoundPlayer.playSound("system_online_beep.wav"); from here.
            // This sound will now be played by SkyDefXRadarSimulator when ENTER is pressed.

            if (sequenceCompletedListener != null) {
                // This informs SkyDefXRadarSimulator that typing is finished
                sequenceCompletedListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "init_sequence_finished"));
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        FontMetrics fm = g2d.getFontMetrics();
        int yOffset = 50;
        int lineHeight = fm.getHeight() + 5;

        // Draw fully typed messages
        for (String msg : fullyTypedMessages) {
            g2d.drawString(msg, 50, yOffset);
            yOffset += lineHeight;
        }

        // Draw the currently typing message (if not empty)
        if (!currentDisplayedText.isEmpty()) {
            g2d.drawString(currentDisplayedText, 50, yOffset);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!sequenceFinishedTyping) {
            if (currentCharIndex < currentFullMessage.length()) {
                currentDisplayedText = currentFullMessage.substring(0, currentCharIndex + 1);
                currentCharIndex++;
            } else {
                // Current message has finished typing
                fullyTypedMessages.add(currentFullMessage); // Add it to the list of fully typed messages
                currentDisplayedText = ""; // Clear current displayed text immediately
                typewriterTimer.stop(); // Pause typewriter timer

                // Start a short pause timer before starting the next message
                Timer pauseTimer = new Timer(500, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e2) {
                        ((Timer) e2.getSource()).stop(); // Stop the pause timer
                        startNextMessage(); // Move to the next message in the queue
                        if (!sequenceFinishedTyping) { // Only restart main timer if there are more messages to type
                            typewriterTimer.start();
                        }
                    }
                });
                pauseTimer.setRepeats(false); // Only run once
                pauseTimer.start();
            }
        }
        repaint(); // Request a repaint to update the displayed text
    }

    public boolean isSequenceFinishedTyping() {
        return sequenceFinishedTyping;
    }
}