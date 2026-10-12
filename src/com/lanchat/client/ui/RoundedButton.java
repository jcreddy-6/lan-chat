package com.lanchat.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A flat button with rounded corners and hover / pressed colors. */
public class RoundedButton extends JButton {

    private final Color base;
    private final Color hover;
    private final Color pressed;
    private final int arc;

    public RoundedButton(String text, Color base, Color hover, Color foreground, int arc) {
        super(text);
        this.base = base;
        this.hover = hover;
        this.pressed = hover.darker();
        this.arc = arc;
        setForeground(foreground);
        setFont(Theme.font(Font.BOLD, 13f));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    }

    public static RoundedButton primary(String text) {
        return new RoundedButton(text, Theme.PRIMARY, Theme.PRIMARY_HOVER, Color.WHITE, 24);
    }

    /** Transparent button for light backgrounds. */
    public static RoundedButton ghost(String text) {
        return new RoundedButton(text, new Color(0, 0, 0, 0), Theme.HOVER_LIGHT, Theme.TEXT_MUTED, 24);
    }

    /** Transparent button for the dark sidebar. */
    public static RoundedButton onDark(String text) {
        return new RoundedButton(text, new Color(0, 0, 0, 0), Theme.SIDEBAR_HOVER, Theme.SIDEBAR_TEXT, 24);
    }

    /** Smaller padding and font; returns this so it can be chained. */
    public RoundedButton small() {
        setFont(Theme.font(Font.BOLD, 12f));
        setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill;
        if (!isEnabled()) {
            fill = base.getAlpha() == 0 ? base : Theme.alpha(base, 110);
        } else if (getModel().isPressed()) {
            fill = pressed;
        } else if (getModel().isRollover()) {
            fill = hover;
        } else {
            fill = base;
        }
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }
}
