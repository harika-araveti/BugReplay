package com.bugreplay;

import com.bugreplay.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Main is the primary entry point for the BugReplay desktop application.
 *
 * It configures the native system Look and Feel and schedules the creation
 * of the MainFrame on the Swing Event Dispatch Thread (EDT).
 */
public class Main {

    public static void main(String[] args) {
        // Configure native system look and feel for a clean OS-integrated appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fallback to default Swing Look & Feel if system theme is unavailable
        }

        // Swing components MUST be created and updated on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            }
        });
    }
}
