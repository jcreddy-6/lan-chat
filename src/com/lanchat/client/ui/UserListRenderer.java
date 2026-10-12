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

/** Draws one online user in the sidebar: avatar, green dot, name and the room they are in. */
public class UserListRenderer extends JComponent implements ListCellRenderer<String> {

    private final String me;
    private String name = "";
    private String room = "";
    private boolean selected;

    public UserListRenderer(String me) {
        this.me = me;
        setOpaque(false);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        int space = value.indexOf(' ');
        name = space > 0 ? value.substring(0, space) : value;
        int open = value.indexOf("[#");
        int close = value.indexOf(']');
        room = (open >= 0 && close > open) ? value.substring(open + 2, close) : "";
        selected = isSelected;
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 46);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        g.setColor(Theme.SIDEBAR_BG);
        g.fillRect(0, 0, w, h);
        if (selected) {
            g.setColor(Theme.SIDEBAR_HOVER);
            g.fillRoundRect(10, 3, w - 20, h - 6, 14, 14);
        }

        int d = 30;
        int ay = (h - d) / 2;
        AvatarBadge.paint(g, 20, ay, d, name);
        g.setColor(Theme.SIDEBAR_BG);                       // ring that separates the dot from the avatar
        g.fillOval(20 + d - 12, ay + d - 12, 13, 13);
        g.setColor(Theme.ONLINE);
        g.fillOval(20 + d - 10, ay + d - 10, 9, 9);

        g.setFont(Theme.font(Font.BOLD, 14f));
        FontMetrics fm = g.getFontMetrics();
        int baseline = (h - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(Theme.SIDEBAR_TEXT);
        String label = name.equals(me) ? name + " (you)" : name;
        g.drawString(label, 62, baseline);

        if (!room.isEmpty()) {
            g.setFont(Theme.font(Font.PLAIN, 11f));
            FontMetrics rf = g.getFontMetrics();
            String text = "#" + room;
            g.setColor(Theme.SIDEBAR_MUTED);
            g.drawString(text, w - 20 - rf.stringWidth(text), (h - rf.getHeight()) / 2 + rf.getAscent());
        }
        g.dispose();
    }
}
