package com.lanchat.client;

import com.lanchat.client.ui.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;

/** Starts the GUI client. */
public class ClientApp {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");   // smoother text
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());   // native look
        } catch (Exception ignored) {
            // keep the default look
        }
        UIManager.put("OptionPane.messageFont", Theme.font(Font.PLAIN, 14f));
        UIManager.put("OptionPane.buttonFont", Theme.font(Font.PLAIN, 13f));
        UIManager.put("ToolTip.font", Theme.font(Font.PLAIN, 12f));
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
