package com.lanchat.client.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** Thin scroll bar with a rounded thumb, no arrows and a transparent track. */
public class ModernScrollBarUI extends BasicScrollBarUI {

    private final Color thumbColor;

    public ModernScrollBarUI(Color thumbColor) {
        this.thumbColor = thumbColor;
    }

    /** Applies the modern scroll bar to a scroll pane and removes its border. */
    public static void apply(JScrollPane pane, Color thumbColor) {
        pane.setBorder(null);
        pane.getVerticalScrollBar().setUI(new ModernScrollBarUI(thumbColor));
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        pane.getVerticalScrollBar().setOpaque(false);
        pane.getVerticalScrollBar().setUnitIncrement(16);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return emptyButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return emptyButton();
    }

    private static JButton emptyButton() {
        JButton b = new JButton();
        Dimension zero = new Dimension(0, 0);
        b.setPreferredSize(zero);
        b.setMinimumSize(zero);
        b.setMaximumSize(zero);
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        // transparent
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isThumbRollover() ? thumbColor.darker() : thumbColor);
        g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
        g2.dispose();
    }
}
