package com.lanchat.client.ui;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Supplier;

/** Draws one room row in the sidebar: "# name" and a count badge. The room you are in is highlighted. */
public class RoomListRenderer extends JComponent implements ListCellRenderer<String> {

    private final Supplier<String> currentRoom;
    private String name = "";
    private String count = "";
    private boolean selected;

    public RoomListRenderer(Supplier<String> currentRoom) {
        this.currentRoom = currentRoom;
        setOpaque(false);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        int space = value.indexOf(' ');
        name = (space > 0 ? value.substring(0, space) : value).replaceFirst("^#", "");
        int open = value.indexOf('(');
        int close = value.indexOf(')');
        count = (open >= 0 && close > open) ? value.substring(open + 1, close) : "";
        selected = isSelected;
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 40);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        boolean current = name.equals(currentRoom.get());

        g.setColor(Theme.SIDEBAR_BG);
        g.fillRect(0, 0, w, h);
        if (current) {
            g.setColor(Theme.PRIMARY);
            g.fillRoundRect(10, 3, w - 20, h - 6, 14, 14);
        } else if (selected) {
            g.setColor(Theme.SIDEBAR_HOVER);
            g.fillRoundRect(10, 3, w - 20, h - 6, 14, 14);
        }

        g.setFont(Theme.font(Font.BOLD, 15f));
        FontMetrics fm = g.getFontMetrics();
        int baseline = (h - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(current ? Theme.WHITE : Theme.SIDEBAR_MUTED);
        g.drawString("#", 24, baseline);
        g.setFont(Theme.font(current ? Font.BOLD : Font.PLAIN, 14f));
        g.setColor(current ? Theme.WHITE : Theme.SIDEBAR_TEXT);
        g.drawString(name, 42, baseline);

        if (!count.isEmpty()) {
            g.setFont(Theme.font(Font.BOLD, 11f));
            FontMetrics cf = g.getFontMetrics();
            int bw = Math.max(22, cf.stringWidth(count) + 14);
            int bx = w - 22 - bw;
            g.setColor(current ? Theme.alpha(Theme.WHITE, 60) : Theme.SIDEBAR_CARD);
            g.fillRoundRect(bx, (h - 20) / 2, bw, 20, 20, 20);
            g.setColor(current ? Theme.WHITE : Theme.SIDEBAR_TEXT);
            g.drawString(count, bx + (bw - cf.stringWidth(count)) / 2, (h - cf.getHeight()) / 2 + cf.getAscent());
        }
        g.dispose();
    }
}
