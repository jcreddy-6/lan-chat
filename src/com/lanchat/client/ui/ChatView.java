package com.lanchat.client.ui;

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The message area. Instead of one component per message, everything is painted by hand:
 * chat bubbles with avatars, centered notices and date dividers. Right-click a message to copy it.
 * All methods must be called on the Swing thread.
 */
public class ChatView extends JComponent implements Scrollable {

    private enum Kind { OTHER, OWN, SYSTEM, NOTICE, ERROR, DIVIDER, FILE }

    private static final class Row {
        final Kind kind;
        final String sender;
        final String text;
        final String time;
        final boolean priv;
        final String peer;
        List<String> lines = new ArrayList<>();
        int y;
        int height;
        int bubbleW;
        int bubbleH;
        boolean own;       // FILE rows only: sent by me
        byte[] data;       // FILE rows only: the received bytes

        Row(Kind kind, String sender, String text, String time, boolean priv, String peer) {
            this.kind = kind;
            this.sender = sender;
            this.text = text;
            this.time = time;
            this.priv = priv;
            this.peer = peer;
        }
    }

    private static final int PAD_X = 20;
    private static final int TOP_PAD = 14;
    private static final int AVATAR = 36;
    private static final int GAP = 10;
    private static final int BUBBLE_PAD_X = 14;
    private static final int BUBBLE_PAD_Y = 9;
    private static final int ROW_GAP = 10;
    private static final int HEADER_H = 18;
    private static final int TIME_H = 14;
    private static final int MAX_ROWS = 2000;

    private final Font bodyFont = Theme.font(Font.PLAIN, 14f);
    private final Font nameFont = Theme.font(Font.BOLD, 12f);
    private final Font timeFont = Theme.font(Font.PLAIN, 11f);
    private final Font sysFont = Theme.font(Font.PLAIN, 12f);

    private final List<Row> rows = new ArrayList<>();
    private int laidOutWidth = -1;
    private int nextY = TOP_PAD;
    private BiConsumer<String, byte[]> fileListener;

    public ChatView() {
        setOpaque(true);
        setToolTipText("Right-click a message to copy it");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && fileListener != null) {
                    Row file = fileRowAt(e);
                    if (file != null) {
                        fileListener.accept(file.text, file.data);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    copyRowAt(e.getY());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(fileRowAt(e) != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // ---------------------------------------------------------------- public API

    /** A chat bubble. own = sent by me (right side, blue). peer is only used for private messages. */
    public void addMessage(boolean own, String sender, String text, String time, boolean priv, String peer) {
        addRow(new Row(own ? Kind.OWN : Kind.OTHER, sender, text, time, priv, peer));
    }

    /** Grey pill, e.g. "reddy joined #general". */
    public void addSystem(String text) {
        addRow(new Row(Kind.SYSTEM, null, text, "", false, null));
    }

    /** Blue pill for replies from the server. */
    public void addNotice(String text) {
        addRow(new Row(Kind.NOTICE, null, text, "", false, null));
    }

    /** Red pill for errors. */
    public void addError(String text) {
        addRow(new Row(Kind.ERROR, null, text, "", false, null));
    }

    /** Line with a small centered title, used around history blocks. */
    public void addDivider(String text) {
        addRow(new Row(Kind.DIVIDER, null, text, "", false, null));
    }

    /** A file card. Clicking it calls the file listener with the file name and bytes. */
    public void addFile(boolean own, String sender, String fileName, byte[] data, String time,
                        boolean priv, String peer) {
        Row row = new Row(Kind.FILE, sender, fileName, time, priv, peer);
        row.own = own;
        row.data = data;
        addRow(row);
    }

    public void setFileListener(BiConsumer<String, byte[]> listener) {
        this.fileListener = listener;
    }

    /** Plain-text transcript of everything shown in the view. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Row r : rows) {
            if (r.kind == Kind.DIVIDER) {
                sb.append("--- ").append(r.text).append(" ---\n");
            } else if (r.kind == Kind.SYSTEM || r.kind == Kind.NOTICE || r.kind == Kind.ERROR) {
                sb.append("* ").append(r.text.replace('\n', ' ')).append('\n');
            } else if (r.kind == Kind.FILE) {
                sb.append('[').append(r.time).append("] ").append(r.sender)
                        .append(" sent a file: ").append(r.text).append('\n');
            } else {
                sb.append('[').append(r.time).append("] ").append(r.sender)
                        .append(r.priv ? " (private)" : "").append(": ").append(r.text).append('\n');
            }
        }
        return sb.toString();
    }

    /** CSV transcript with the columns time, sender, type, text. */
    public String toCsv() {
        StringBuilder sb = new StringBuilder("time,sender,type,text\n");
        for (Row r : rows) {
            if (r.kind == Kind.DIVIDER) {
                continue;
            }
            String type;
            if (r.kind == Kind.FILE) {
                type = "file";
            } else if (r.kind == Kind.ERROR) {
                type = "error";
            } else if (r.kind == Kind.SYSTEM || r.kind == Kind.NOTICE) {
                type = "notice";
            } else {
                type = r.priv ? "private" : "message";
            }
            sb.append(csv(r.time)).append(',')
                    .append(csv(r.sender == null ? "SERVER" : r.sender)).append(',')
                    .append(csv(type)).append(',')
                    .append(csv(r.text)).append('\n');
        }
        return sb.toString();
    }

    /** Quotes a CSV cell and neutralizes spreadsheet formulas (a cell starting with = + - @ would run in Excel). */
    private static String csv(String value) {
        String s = value == null ? "" : value;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---------------------------------------------------------------- row management

    private void addRow(Row row) {
        rows.add(row);
        if (rows.size() > MAX_ROWS) {
            rows.subList(0, 500).clear();
            laidOutWidth = -1;              // everything moved up: lay out again
        }
        if (laidOutWidth > 0) {
            layoutRow(row, laidOutWidth);
            row.y = nextY;
            nextY += row.height;
        }
        revalidate();
        repaint();
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (pane != null) {
                JScrollBar bar = pane.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    private void ensureLayout(int width) {
        if (width <= 0 || width == laidOutWidth) {
            return;
        }
        laidOutWidth = width;
        nextY = TOP_PAD;
        for (Row r : rows) {
            layoutRow(r, width);
            r.y = nextY;
            nextY += r.height;
        }
    }

    /** Works out line breaks, bubble size and row height for a given component width. */
    private void layoutRow(Row r, int width) {
        switch (r.kind) {
            case DIVIDER:
                r.height = 34;
                break;
            case FILE: {
                int avatarSpace = r.own ? 0 : AVATAR + GAP;
                r.bubbleW = Math.max(180, Math.min(300, width - 2 * PAD_X - avatarSpace));
                r.bubbleH = 64;
                int header = (!r.own || r.priv) ? HEADER_H : 0;
                r.height = header + r.bubbleH + ROW_GAP;
                break;
            }
            case SYSTEM:
            case NOTICE:
            case ERROR: {
                FontMetrics fm = getFontMetrics(sysFont);
                int max = Math.max(160, (int) (width * 0.7)) - 28;
                r.lines = wrap(r.text, fm, max);
                r.bubbleW = maxWidth(r.lines, fm) + 28;
                r.bubbleH = r.lines.size() * fm.getHeight() + 12;
                r.height = r.bubbleH + ROW_GAP;
                break;
            }
            default: {
                FontMetrics fm = getFontMetrics(bodyFont);
                FontMetrics tf = getFontMetrics(timeFont);
                int avatarSpace = r.kind == Kind.OTHER ? AVATAR + GAP : 0;
                int maxBubble = Math.min((int) (width * 0.66), width - 2 * PAD_X - avatarSpace);
                int maxText = Math.max(120, maxBubble) - 2 * BUBBLE_PAD_X;
                r.lines = wrap(r.text, fm, maxText);
                int textW = Math.max(maxWidth(r.lines, fm), tf.stringWidth(r.time));
                r.bubbleW = textW + 2 * BUBBLE_PAD_X;
                r.bubbleH = 2 * BUBBLE_PAD_Y + r.lines.size() * fm.getHeight() + TIME_H;
                int header = (r.kind == Kind.OTHER || r.priv) ? HEADER_H : 0;
                r.height = header + r.bubbleH + ROW_GAP;
            }
        }
    }

    private static int maxWidth(List<String> lines, FontMetrics fm) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, fm.stringWidth(line));
        }
        return max;
    }

    /** Word wrap that also handles explicit new lines and words longer than a line. */
    private static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String safe = text == null ? "" : text;
        for (String paragraph : safe.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                while (fm.stringWidth(word) > maxWidth) {
                    if (line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    int cut = 1;
                    while (cut < word.length() && fm.stringWidth(word.substring(0, cut + 1)) <= maxWidth) {
                        cut++;
                    }
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private void copyRowAt(int y) {
        for (Row r : rows) {
            if (y >= r.y && y < r.y + r.height) {
                if (r.kind == Kind.OWN || r.kind == Kind.OTHER) {
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(r.text), null);
                    } catch (IllegalStateException ignored) {
                        // clipboard busy: ignore
                    }
                }
                return;
            }
        }
    }

    // ---------------------------------------------------------------- size

    @Override
    public Dimension getPreferredSize() {
        int w = getParent() instanceof JViewport ? ((JViewport) getParent()).getWidth() : 400;
        if (w <= 0) {
            w = 400;
        }
        ensureLayout(w);
        return new Dimension(w, nextY + 8);
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.CHAT_BG);
        g.fillRect(0, 0, getWidth(), getHeight());

        ensureLayout(getWidth());
        Rectangle clip = g.getClipBounds() != null ? g.getClipBounds() : new Rectangle(0, 0, getWidth(), getHeight());
        for (Row r : rows) {
            if (r.y + r.height < clip.y) {
                continue;
            }
            if (r.y > clip.y + clip.height) {
                break;
            }
            paintRow(g, r, getWidth());
        }
        g.dispose();
    }

    private void paintRow(Graphics2D g, Row r, int width) {
        switch (r.kind) {
            case DIVIDER:
                paintDivider(g, r, width);
                break;
            case FILE:
                paintFile(g, r, width);
                break;
            case SYSTEM:
            case NOTICE:
            case ERROR:
                paintPill(g, r, width);
                break;
            default:
                paintBubble(g, r, width);
        }
    }

    private void paintDivider(Graphics2D g, Row r, int width) {
        g.setFont(sysFont);
        FontMetrics fm = g.getFontMetrics();
        String text = r.text.toUpperCase();
        int tw = fm.stringWidth(text);
        int cy = r.y + r.height / 2;
        int tx = (width - tw) / 2;
        g.setColor(Theme.BORDER);
        if (tx - 12 > PAD_X) {
            g.drawLine(PAD_X, cy, tx - 12, cy);
            g.drawLine(tx + tw + 12, cy, width - PAD_X, cy);
        }
        g.setColor(Theme.TEXT_MUTED);
        g.drawString(text, tx, cy - fm.getHeight() / 2 + fm.getAscent());
    }

    private void paintPill(Graphics2D g, Row r, int width) {
        Color bg;
        Color fg;
        if (r.kind == Kind.ERROR) {
            bg = Theme.ERROR_BG;
            fg = Theme.ERROR_FG;
        } else if (r.kind == Kind.NOTICE) {
            bg = Theme.NOTICE_BG;
            fg = Theme.NOTICE_FG;
        } else {
            bg = Theme.PILL_BG;
            fg = Theme.PILL_FG;
        }
        int x = (width - r.bubbleW) / 2;
        int arc = r.lines.size() == 1 ? r.bubbleH : 18;
        g.setColor(bg);
        g.fillRoundRect(x, r.y, r.bubbleW, r.bubbleH, arc, arc);

        g.setFont(sysFont);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(fg);
        int ty = r.y + 6 + fm.getAscent();
        for (String line : r.lines) {
            g.drawString(line, (width - fm.stringWidth(line)) / 2, ty);
            ty += fm.getHeight();
        }
    }

    private void paintBubble(Graphics2D g, Row r, int width) {
        boolean own = r.kind == Kind.OWN;
        int header = (!own || r.priv) ? HEADER_H : 0;
        int bubbleX = own ? width - PAD_X - r.bubbleW : PAD_X + AVATAR + GAP;
        int bubbleY = r.y + header;

        paintHeader(g, r, own, bubbleX, r.bubbleW);

        Color bg;
        Color border;
        Color fg;
        Color timeFg;
        if (own) {
            bg = r.priv ? Theme.PRIVATE_ACCENT : Theme.PRIMARY;
            border = null;
            fg = Color.WHITE;
            timeFg = Theme.alpha(Color.WHITE, 190);
        } else {
            bg = r.priv ? Theme.PRIVATE_BG : Color.WHITE;
            border = r.priv ? Theme.PRIVATE_BORDER : Theme.BORDER;
            fg = Theme.TEXT;
            timeFg = Theme.TEXT_MUTED;
        }

        if (!own) {
            g.setColor(new Color(0, 0, 0, 12));   // soft shadow
            g.fill(new RoundRectangle2D.Float(bubbleX, bubbleY + 1, r.bubbleW, r.bubbleH, 18, 18));
        }
        RoundRectangle2D shape = new RoundRectangle2D.Float(bubbleX + 0.5f, bubbleY + 0.5f, r.bubbleW - 1, r.bubbleH - 1, 18, 18);
        g.setColor(bg);
        g.fill(shape);
        if (border != null) {
            g.setColor(border);
            g.draw(shape);
        }

        g.setFont(bodyFont);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(fg);
        int ty = bubbleY + BUBBLE_PAD_Y + fm.getAscent();
        for (String line : r.lines) {
            g.drawString(line, bubbleX + BUBBLE_PAD_X, ty);
            ty += fm.getHeight();
        }

        g.setFont(timeFont);
        FontMetrics tf = g.getFontMetrics();
        g.setColor(timeFg);
        int timeY = bubbleY + BUBBLE_PAD_Y + r.lines.size() * fm.getHeight() + tf.getAscent() - 1;
        g.drawString(r.time, bubbleX + r.bubbleW - BUBBLE_PAD_X - tf.stringWidth(r.time), timeY);
    }

    /** Sender name and avatar (others) or "private to X" (my private messages) above a bubble or file card. */
    private void paintHeader(Graphics2D g, Row r, boolean own, int x, int w) {
        g.setFont(nameFont);
        FontMetrics nf = g.getFontMetrics();
        if (!own) {
            g.setColor(Theme.avatarColor(r.sender));
            g.drawString(r.sender, x, r.y + 13);
            if (r.priv) {
                g.setColor(Theme.PRIVATE_ACCENT);
                g.drawString("  \u00B7  private", x + nf.stringWidth(r.sender), r.y + 13);
            }
            AvatarBadge.paint(g, PAD_X, r.y, AVATAR, r.sender);
        } else if (r.priv) {
            String label = "private to " + r.peer;
            g.setColor(Theme.PRIVATE_ACCENT);
            g.drawString(label, x + w - nf.stringWidth(label), r.y + 13);
        }
    }

    private void paintFile(Graphics2D g, Row r, int width) {
        boolean own = r.own;
        int header = (!own || r.priv) ? HEADER_H : 0;
        int x = own ? width - PAD_X - r.bubbleW : PAD_X + AVATAR + GAP;
        int y = r.y + header;
        paintHeader(g, r, own, x, r.bubbleW);

        Color bg;
        Color border;
        Color fg;
        Color sub;
        Color iconBg;
        Color iconFg;
        if (own) {
            bg = r.priv ? Theme.PRIVATE_ACCENT : Theme.PRIMARY;
            border = null;
            fg = Color.WHITE;
            sub = Theme.alpha(Color.WHITE, 205);
            iconBg = Theme.alpha(Color.WHITE, 45);
            iconFg = Color.WHITE;
        } else {
            bg = r.priv ? Theme.PRIVATE_BG : Color.WHITE;
            border = r.priv ? Theme.PRIVATE_BORDER : Theme.BORDER;
            fg = Theme.TEXT;
            sub = Theme.TEXT_MUTED;
            iconBg = r.priv ? Theme.PRIVATE_BORDER : Theme.NOTICE_BG;
            iconFg = r.priv ? Theme.PRIVATE_ACCENT : Theme.NOTICE_FG;
        }

        if (!own) {
            g.setColor(new Color(0, 0, 0, 12));
            g.fill(new RoundRectangle2D.Float(x, y + 1, r.bubbleW, r.bubbleH, 18, 18));
        }
        RoundRectangle2D shape = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, r.bubbleW - 1, r.bubbleH - 1, 18, 18);
        g.setColor(bg);
        g.fill(shape);
        if (border != null) {
            g.setColor(border);
            g.draw(shape);
        }

        // icon with the file extension
        g.setColor(iconBg);
        g.fillRoundRect(x + 12, y + 12, 40, 40, 12, 12);
        String ext = extensionOf(r.text);
        g.setFont(Theme.font(Font.BOLD, 11f));
        FontMetrics ef = g.getFontMetrics();
        g.setColor(iconFg);
        g.drawString(ext, x + 12 + (40 - ef.stringWidth(ext)) / 2, y + 12 + (40 - ef.getHeight()) / 2 + ef.getAscent());

        // name and details
        g.setFont(Theme.font(Font.BOLD, 13f));
        FontMetrics nameMetrics = g.getFontMetrics();
        g.setColor(fg);
        g.drawString(ellipsize(r.text, nameMetrics, r.bubbleW - 62 - 14), x + 62, y + 27);
        g.setFont(timeFont);
        g.setColor(sub);
        long size = r.data == null ? 0 : r.data.length;
        g.drawString(formatSize(size) + "  \u00B7  " + r.time + "  \u00B7  click to save", x + 62, y + 46);
    }

    /** The file card under the mouse, or null. */
    private Row fileRowAt(MouseEvent e) {
        for (Row r : rows) {
            if (e.getY() >= r.y && e.getY() < r.y + r.height) {
                if (r.kind != Kind.FILE || r.data == null) {
                    return null;
                }
                int header = (!r.own || r.priv) ? HEADER_H : 0;
                int x = r.own ? getWidth() - PAD_X - r.bubbleW : PAD_X + AVATAR + GAP;
                boolean inside = e.getX() >= x && e.getX() <= x + r.bubbleW
                        && e.getY() >= r.y + header && e.getY() <= r.y + header + r.bubbleH;
                return inside ? r : null;
            }
        }
        return null;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "FILE";
        }
        String ext = fileName.substring(dot + 1).toUpperCase();
        return ext.length() > 4 ? ext.substring(0, 4) : ext;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String ellipsize(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && fm.stringWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    // ---------------------------------------------------------------- Scrollable

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(400, 300);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 28;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? Math.max(60, visibleRect.height - 40) : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        Container parent = getParent();
        return parent instanceof JViewport && parent.getHeight() > getPreferredSize().height;
    }
}
