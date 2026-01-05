package jdiskmark;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Password check for test portal uploads.
 */
public class PortalEnableDialog extends JDialog {
    private final JPasswordField passwordField;
    private boolean authenticated = false;
    private final String REQUIRED_PASSWORD = "goHampsters!";

    public PortalEnableDialog(Frame parent) {
        super(parent, "Security Check", true);

        // UI Setup
        setLayout(new BorderLayout(10, 10));
        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(new JLabel("Enter Password:"));
        passwordField = new JPasswordField(15);
        
        // trigger checkPassword when Enter is pressed inside the field
        passwordField.addActionListener((ActionEvent e) -> {
            checkPassword();
        });
        
        panel.add(passwordField);

        JButton submitButton = new JButton("Enable");
        submitButton.addActionListener((ActionEvent e) -> {
            checkPassword();
        });

        add(panel, BorderLayout.CENTER);
        add(submitButton, BorderLayout.SOUTH);

        // Set the 'Enable' button as the default button for the Enter key
        getRootPane().setDefaultButton(submitButton);

        pack();
        setLocationRelativeTo(parent);
    }

    private void checkPassword() {
        String input = new String(passwordField.getPassword());
        if (input.equals(REQUIRED_PASSWORD)) {
            authenticated = true;
            dispose(); // Close dialog on success
        } else {
            JOptionPane.showMessageDialog(this, 
                "Incorrect password. Operation will terminate.", 
                "Upload enable failed", 
                JOptionPane.ERROR_MESSAGE);
            authenticated = false;
            dispose(); // Close dialog to trigger termination
        }
    }

    public boolean isAuthorized() {
        return authenticated;
    }
}