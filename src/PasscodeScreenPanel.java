// PasscodeScreenPanel.java

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays; // For comparing char arrays

public class PasscodeScreenPanel extends JPanel {

    private JPasswordField passcodeField; // Use JPasswordField for masked input
    private JLabel feedbackLabel; // To display ACCESS GRANTED/DENIED
    private ActionListener passcodeGrantedListener; // To notify when passcode is correct

    private final char[] CORRECT_PASSCODE = {'1', '2', '3', '4'}; // Your predefined passcode

    public PasscodeScreenPanel(ActionListener listener) {
        this.passcodeGrantedListener = listener; // Store the listener for callback

        setBackground(Color.BLACK);
        setLayout(new BorderLayout(0, 50)); // Layout with vertical gap

        // --- Top: System Locked Title ---
        JLabel titleLabel = new JLabel("[ SYSTEM LOCKED ]", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Monospaced", Font.BOLD, 48));
        titleLabel.setForeground(new Color(255, 50, 50)); // Red color for locked status
        titleLabel.setBorder(BorderFactory.createEmptyBorder(50, 0, 0, 0)); // Top padding
        add(titleLabel, BorderLayout.NORTH);

        // --- Center: Prompt and Passcode Field ---
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(Color.BLACK);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 100, 0, 100));

        JLabel promptLabel = new JLabel("ENTER AUTHORIZATION CODE:", SwingConstants.CENTER);
        promptLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        promptLabel.setFont(new Font("Monospaced", Font.PLAIN, 24));
        promptLabel.setForeground(new Color(0, 200, 0));
        
        passcodeField = new JPasswordField(15); // 15 columns wide
        passcodeField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passcodeField.setFont(new Font("Monospaced", Font.PLAIN, 20));
        passcodeField.setBackground(Color.BLACK);
        passcodeField.setForeground(new Color(0, 255, 0));
        passcodeField.setCaretColor(new Color(0, 255, 0));
        passcodeField.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 150, 0))); // Underline effect

        feedbackLabel = new JLabel("", SwingConstants.CENTER);
        feedbackLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        feedbackLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        feedbackLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0)); // Top padding for feedback

        // Action listener for Enter key press on the passcode field
        passcodeField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                char[] enteredPasscode = passcodeField.getPassword(); // Get password as char array
                
                if (Arrays.equals(enteredPasscode, CORRECT_PASSCODE)) {
                    // Correct Passcode
                    feedbackLabel.setForeground(new Color(0, 255, 0));
                    feedbackLabel.setText(">> ACCESS GRANTED \u2705"); // Green checkmark
                    passcodeField.setEditable(false); // Disable further input
                    
                    // Trigger the listener after a short delay to allow user to see "ACCESS GRANTED"
                    Timer timer = new Timer(1500, new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e2) {
                            ((Timer) e2.getSource()).stop();
                            if (passcodeGrantedListener != null) {
                                passcodeGrantedListener.actionPerformed(new ActionEvent(PasscodeScreenPanel.this, ActionEvent.ACTION_PERFORMED, "passcode_granted"));
                            }
                        }
                    });
                    timer.setRepeats(false); // Only run once
                    timer.start();

                } else {
                    // Incorrect Passcode
                    feedbackLabel.setForeground(new Color(255, 50, 50));
                    feedbackLabel.setText(">> ACCESS DENIED \u274C"); // Red X mark
                    passcodeField.setText(""); // Clear the field
                    // Optional: Play a "denied" sound here
                }
                // Securely clear the password array
                Arrays.fill(enteredPasscode, ' ');
            }
        });
        
        centerPanel.add(promptLabel);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(passcodeField);
        centerPanel.add(feedbackLabel); // Add feedback label

        add(centerPanel, BorderLayout.CENTER);
    }

    // Call this method to give focus to the input field when the panel becomes visible
    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> {
            passcodeField.requestFocusInWindow();
            feedbackLabel.setText(""); // Clear feedback when shown
            passcodeField.setText(""); // Clear field when shown
            passcodeField.setEditable(true); // Ensure it's editable for retry
        });
    }
}