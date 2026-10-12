package com.lanchat.client.ui;

import java.awt.Color;
import java.awt.Font;

/** Colors and fonts used by the whole GUI, so the look can be changed in one place. */
public final class Theme {

    private Theme() { }

    public static final Color PRIMARY = new Color(79, 125, 243);
    public static final Color PRIMARY_HOVER = new Color(61, 104, 219);
    public static final Color PURPLE = new Color(124, 92, 255);

    public static final Color SIDEBAR_BG = new Color(27, 31, 51);
    public static final Color SIDEBAR_CARD = new Color(36, 41, 66);
    public static final Color SIDEBAR_HOVER = new Color(46, 52, 82);
    public static final Color SIDEBAR_TEXT = new Color(222, 226, 242);
    public static final Color SIDEBAR_MUTED = new Color(127, 134, 168);

    public static final Color CHAT_BG = new Color(243, 245, 251);
    public static final Color WHITE = Color.WHITE;
    public static final Color BORDER = new Color(225, 229, 240);
    public static final Color HOVER_LIGHT = new Color(236, 240, 250);
    public static final Color TEXT = new Color(31, 36, 48);
    public static final Color TEXT_MUTED = new Color(107, 115, 144);
    public static final Color PLACEHOLDER = new Color(160, 167, 190);

    public static final Color PRIVATE_BG = new Color(238, 233, 255);
    public static final Color PRIVATE_BORDER = new Color(213, 201, 255);
    public static final Color PRIVATE_ACCENT = new Color(107, 76, 240);

    public static final Color PILL_BG = new Color(231, 235, 246);
    public static final Color PILL_FG = new Color(102, 112, 143);
    public static final Color NOTICE_BG = new Color(226, 236, 255);
    public static final Color NOTICE_FG = new Color(47, 91, 199);
    public static final Color ERROR_BG = new Color(253, 231, 231);
    public static final Color ERROR_FG = new Color(180, 35, 24);

    public static final Color ONLINE = new Color(52, 199, 89);

    private static final Color[] AVATARS = {
            new Color(79, 125, 243), new Color(124, 92, 255), new Color(236, 90, 140), new Color(255, 138, 61),
            new Color(32, 178, 170), new Color(52, 168, 83), new Color(214, 69, 65), new Color(142, 68, 173)
    };

    /** Same name always gets the same color. */
    public static Color avatarColor(String name) {
        return AVATARS[Math.floorMod(name == null ? 0 : name.hashCode(), AVATARS.length)];
    }

    public static Font font(int style, float size) {
        return new Font("Segoe UI", style, 12).deriveFont(size);   // falls back to the default font if missing
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
