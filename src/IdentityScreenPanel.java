// IdentityScreenPanel.java

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class IdentityScreenPanel extends JPanel {

    private JTextField inputField;
    private ActionListener identityConfirmedListener; // To notify when identity is entered

    public IdentityScreenPanel(ActionListener listener) {
        this.identityConfirmedListener = listener; // Store the listener for callback

        setBackground(Color.BLACK); // Black background
        setLayout(new BorderLayout(0, 50)); // Layout with vertical gap

        // --- Top: SkyDefX Title ---
        JLabel titleLabel = new JLabel("SKYDEFX", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Monospaced", Font.BOLD, 48));
        titleLabel.setForeground(new Color(0, 255, 0)); // Bright green
        titleLabel.setBorder(BorderFactory.createEmptyBorder(50, 0, 0, 0)); // Top padding
        add(titleLabel, BorderLayout.NORTH);

        // --- Center: Prompt and Input Field ---
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS)); // Vertical stacking
        centerPanel.setBackground(Color.BLACK);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 100, 0, 100)); // Horizontal padding

        JLabel promptLabel = new JLabel("PLEASE IDENTIFY YOURSELF", SwingConstants.CENTER);
        promptLabel.setAlignmentX(Component.CENTER_ALIGNMENT); // Center align within BoxLayout
        promptLabel.setFont(new Font("Monospaced", Font.PLAIN, 24));
        promptLabel.setForeground(new Color(0, 200, 0)); // Slightly dimmer green
        
        inputField = new JTextField(20); // 20 columns wide
        inputField.setAlignmentX(Component.CENTER_ALIGNMENT);
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 20));
        inputField.setBackground(Color.BLACK); // Black background for input
        inputField.setForeground(new Color(0, 255, 0)); // Green text for input
        inputField.setCaretColor(new Color(0, 255, 0)); // Green caret (cursor)
        inputField.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 150, 0))); // Underline effect

        // Add an action listener to the input field to detect ENTER key press
        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // When Enter is pressed, trigger the listener (e.g., to move to next screen)
                if (identityConfirmedListener != null) {
                    identityConfirmedListener.actionPerformed(new ActionEvent(IdentityScreenPanel.this, ActionEvent.ACTION_PERFORMED, "identity_confirmed"));
                }
            }
        });
        
        // Add components to the center panel
        centerPanel.add(promptLabel);
        centerPanel.add(Box.createVerticalStrut(20)); // Spacing
        centerPanel.add(inputField);

        add(centerPanel, BorderLayout.CENTER);
    }

    // Call this method to give focus to the input field when the panel becomes visible
    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> {
            inputField.requestFocusInWindow();
        });
    }
    
    public String getEnteredIdentity() {
        return inputField.getText().trim();
    }
}