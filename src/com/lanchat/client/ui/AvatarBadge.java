package com.lanchat.client.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Colored circle with the first letter of a name. Also has a static paint method for custom-drawn lists. */
public class AvatarBadge extends JComponent {

    private final String name;
    private final int size;

    public AvatarBadge(String name, int size) {
        this.name = name;
        this.size = size;
        setOpaque(false);
        setPreferredSize(new Dimension(size, size));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paint(g2, 0, 0, size, name);
        g2.dispose();
    }

    public static void paint(Graphics2D g, int x, int y, int diameter, String name) {
        g.setColor(Theme.avatarColor(name));
        g.fillOval(x, y, diameter, diameter);
        String initial = (name == null || name.isEmpty()) ? "?" : name.substring(0, 1).toUpperCase();
        g.setFont(Theme.font(Font.BOLD, diameter * 0.42f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString(initial, x + (diameter - fm.stringWidth(initial)) / 2,
                y + (diameter - fm.getHeight()) / 2 + fm.getAscent());
    }
}
