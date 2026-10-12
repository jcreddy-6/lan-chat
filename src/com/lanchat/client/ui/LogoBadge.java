package com.lanchat.client.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Small app logo: gradient rounded square with "LC". */
public class LogoBadge extends JComponent {

    private final int size;

    public LogoBadge(int size) {
        this.size = size;
        setOpaque(false);
        setPreferredSize(new Dimension(size, size));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, Theme.PRIMARY, size, size, Theme.PURPLE));
        g2.fillRoundRect(0, 0, size, size, size / 3, size / 3);
        g2.setFont(Theme.font(Font.BOLD, size * 0.4f));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.WHITE);
        g2.drawString("LC", (size - fm.stringWidth("LC")) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
