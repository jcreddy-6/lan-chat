package com.lanchat.client.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/** Shared painting for the rounded text and password fields. */
final class FieldPainter {

    private static final int ARC = 16;

    private FieldPainter() { }

    static void background(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, ARC, ARC);
        g2.dispose();
    }

    /** Draws the outline (blue when focused) and the grey hint text when the field is empty. */
    static void decorate(Graphics g, JComponent c, String placeholder, boolean empty) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean focused = c.hasFocus();
        g2.setColor(focused ? Theme.PRIMARY : Theme.BORDER);
        g2.setStroke(new BasicStroke(focused ? 2f : 1f));
        g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, ARC, ARC);
        if (empty && placeholder != null && !placeholder.isEmpty()) {
            Insets in = c.getInsets();
            FontMetrics fm = g2.getFontMetrics(c.getFont());
            g2.setFont(c.getFont());
            g2.setColor(Theme.PLACEHOLDER);
            g2.drawString(placeholder, in.left, (c.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }
}
