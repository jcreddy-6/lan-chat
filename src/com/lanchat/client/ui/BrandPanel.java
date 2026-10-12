package com.lanchat.client.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Gradient panel with the app name and a few decorative chat bubbles, shown on the login window. */
public class BrandPanel extends JPanel {

    public BrandPanel() {
        setPreferredSize(new Dimension(340, 520));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        g.setPaint(new GradientPaint(0, 0, Theme.PRIMARY, w, h, Theme.PURPLE));
        g.fillRect(0, 0, w, h);

        g.setColor(new Color(255, 255, 255, 26));
        g.fillOval(-70, h - 210, 280, 280);
        g.fillOval(w - 140, -90, 240, 240);
        g.fillOval(w - 90, h / 2, 120, 120);

        // logo
        g.setColor(Color.WHITE);
        g.fillRoundRect(40, 48, 54, 54, 18, 18);
        g.setFont(Theme.font(Font.BOLD, 21f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.PRIMARY);
        g.drawString("LC", 40 + (54 - fm.stringWidth("LC")) / 2, 48 + (54 - fm.getHeight()) / 2 + fm.getAscent());

        // title and tagline
        g.setColor(Color.WHITE);
        g.setFont(Theme.font(Font.BOLD, 34f));
        g.drawString("LAN Chat", 40, 156);
        g.setFont(Theme.font(Font.PLAIN, 15f));
        g.setColor(new Color(255, 255, 255, 225));
        g.drawString("Fast, private messaging for", 40, 190);
        g.drawString("everyone on your local network.", 40, 212);

        // decorative bubbles
        bubble(g, 40, h - 215, 214, 42, new Color(255, 255, 255, 238), Theme.TEXT, "Is the demo ready?");
        bubble(g, w - 40 - 210, h - 160, 210, 42, new Color(0, 0, 0, 55), Color.WHITE, "Almost, pushing the last fix");
        bubble(g, 40, h - 105, 190, 42, new Color(255, 255, 255, 238), Theme.TEXT, "Great, see you at 4!");
        g.dispose();
    }

    private static void bubble(Graphics2D g, int x, int y, int w, int h, Color bg, Color fg, String text) {
        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 22, 22);
        g.setFont(Theme.font(Font.PLAIN, 13f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(fg);
        g.drawString(text, x + 16, y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }
}
