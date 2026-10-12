package com.lanchat.client;

import com.lanchat.client.ui.AvatarBadge;
import com.lanchat.client.ui.ChatView;
import com.lanchat.client.ui.LogoBadge;
import com.lanchat.client.ui.ModernScrollBarUI;
import com.lanchat.client.ui.RoomListRenderer;
import com.lanchat.client.ui.RoundedButton;
import com.lanchat.client.ui.RoundedTextField;
import com.lanchat.client.ui.Theme;
import com.lanchat.client.ui.UserListRenderer;
import com.lanchat.common.ChatRecord;
import com.lanchat.common.FileMessage;
import com.lanchat.common.HistoryMessage;
import com.lanchat.common.ListMessage;
import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.TextMessage;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Main chat window: dark sidebar (rooms, online users, you) and the message area with the input bar. */
public class ChatFrame extends JFrame implements ChatConnection.Listener {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_HM = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;   // must match the server limit

    private final ChatConnection connection;
    private final String username;

    private final ChatView chatView = new ChatView();
    private final RoundedTextField inputField = new RoundedTextField(30);
    private final RoundedButton sendButton = RoundedButton.primary("Send");
    private final DefaultListModel<String> roomModel = new DefaultListModel<>();
    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final JList<String> roomList = new JList<>(roomModel);
    private final JList<String> userList = new JList<>(userModel);
    private final JLabel roomLabel = new JLabel("# general");
    private final JLabel onlineLabel = new JLabel("ONLINE");
    private final JLabel statusLabel = new JLabel("Online");
    private final Timer refreshTimer;
    private String currentRoom = "general";   // only touched on the Swing thread
    private boolean admin;                    // learned from the server's user list
    private boolean connected = true;

    public ChatFrame(ChatConnection connection, String username) {
        super("LAN Chat - " + username);
        this.connection = connection;
        this.username = username;
        this.refreshTimer = new Timer(5000, e -> requestLists());   // refresh room and user lists

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1120, 700);
        setMinimumSize(new Dimension(900, 520));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMain(), BorderLayout.CENTER);

        chatView.setFileListener(this::saveReceivedFile);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
                try {
                    connection.send(new TextMessage(MessageType.LOGOUT, username, null, ""));
                } catch (IOException ignored) {
                    // already disconnected
                }
                connection.close();
            }
        });

        refreshTimer.start();
        connection.startListening(this);
        SwingUtilities.invokeLater(inputField::requestFocusInWindow);
    }

    // ---------------------------------------------------------------- building the window

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(270, 0));

        // brand
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 18));
        brand.setOpaque(false);
        brand.add(new LogoBadge(38));
        JLabel appName = new JLabel("LAN Chat");
        appName.setFont(Theme.font(Font.BOLD, 19f));
        appName.setForeground(Color.WHITE);
        brand.add(appName);

        // rooms
        JLabel roomsTitle = new JLabel("ROOMS");
        styleSectionLabel(roomsTitle);
        RoundedButton newRoomButton = RoundedButton.onDark("+ New").small();
        newRoomButton.addActionListener(e -> askForRoom());
        JPanel roomsSection = new JPanel(new BorderLayout());
        roomsSection.setOpaque(false);
        roomsSection.add(sectionHeader(roomsTitle, newRoomButton), BorderLayout.NORTH);
        roomsSection.add(styledList(roomList, new RoomListRenderer(() -> currentRoom), 40), BorderLayout.CENTER);

        // online users
        styleSectionLabel(onlineLabel);
        JPanel usersSection = new JPanel(new BorderLayout());
        usersSection.setOpaque(false);
        usersSection.add(sectionHeader(onlineLabel, null), BorderLayout.NORTH);
        usersSection.add(styledList(userList, new UserListRenderer(username), 46), BorderLayout.CENTER);

        JPanel middle = new JPanel(new GridLayout(2, 1));
        middle.setOpaque(false);
        middle.add(roomsSection);
        middle.add(usersSection);

        roomList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String selected = roomList.getSelectedValue();
                if (e.getClickCount() == 2 && selected != null) {
                    joinRoom(nameOf(selected));
                }
            }
        });
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String selected = userList.getSelectedValue();
                if (e.getClickCount() == 2 && selected != null) {
                    startPrivateMessage(nameOf(selected));
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showUserMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showUserMenu(e);
                }
            }
        });

        sidebar.add(brand, BorderLayout.NORTH);
        sidebar.add(middle, BorderLayout.CENTER);
        sidebar.add(buildUserCard(), BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel buildUserCard() {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(Theme.SIDEBAR_CARD);
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 12));

        JLabel name = new JLabel(username);
        name.setFont(Theme.font(Font.BOLD, 14f));
        name.setForeground(Color.WHITE);
        statusLabel.setFont(Theme.font(Font.PLAIN, 12f));
        statusLabel.setForeground(Theme.ONLINE);
        JPanel who = new JPanel(new GridLayout(2, 1));
        who.setOpaque(false);
        who.add(name);
        who.add(statusLabel);

        RoundedButton leave = RoundedButton.onDark("Leave").small();
        leave.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));

        card.add(new AvatarBadge(username, 38), BorderLayout.WEST);
        card.add(who, BorderLayout.CENTER);
        card.add(leave, BorderLayout.EAST);
        return card;
    }

    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.CHAT_BG);

        // header
        roomLabel.setFont(Theme.font(Font.BOLD, 20f));
        roomLabel.setForeground(Theme.TEXT);
        JLabel hint = new JLabel("Double-click an online user to message them privately");
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setForeground(Theme.TEXT_MUTED);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(roomLabel);
        titles.add(hint);

        RoundedButton historyButton = RoundedButton.ghost("History").small();
        RoundedButton searchButton = RoundedButton.ghost("Search").small();
        RoundedButton exportButton = RoundedButton.ghost("Export").small();
        RoundedButton helpButton = RoundedButton.ghost("Help").small();
        historyButton.addActionListener(e -> sendPacket(new TextMessage(MessageType.HISTORY, username, null, "")));
        searchButton.addActionListener(e -> askForSearch());
        exportButton.addActionListener(e -> exportChat());
        helpButton.addActionListener(e -> showHelp());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 12));
        actions.setOpaque(false);
        actions.add(historyButton);
        actions.add(searchButton);
        actions.add(exportButton);
        actions.add(helpButton);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(12, 24, 12, 14)));
        header.add(titles, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        // messages
        JScrollPane scroll = new JScrollPane(chatView);
        scroll.getViewport().setBackground(Theme.CHAT_BG);
        ModernScrollBarUI.apply(scroll, new Color(160, 168, 196, 170));

        // input bar
        inputField.setPlaceholder("Message #general");
        inputField.addActionListener(e -> sendFromInput());
        sendButton.addActionListener(e -> sendFromInput());
        RoundedButton attachButton = RoundedButton.ghost("Attach");
        attachButton.addActionListener(e -> chooseAndSendFile());
        JPanel inputBar = new JPanel(new BorderLayout(10, 0));
        inputBar.setBackground(Theme.WHITE);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 16, 16, 20)));
        inputBar.add(attachButton, BorderLayout.WEST);
        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(sendButton, BorderLayout.EAST);

        main.add(header, BorderLayout.NORTH);
        main.add(scroll, BorderLayout.CENTER);
        main.add(inputBar, BorderLayout.SOUTH);
        return main;
    }

    private static void styleSectionLabel(JLabel label) {
        label.setFont(Theme.font(Font.BOLD, 11f));
        label.setForeground(Theme.SIDEBAR_MUTED);
    }

    private static JPanel sectionHeader(JLabel title, JComponent right) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(8, 20, 6, 12));
        p.add(title, BorderLayout.WEST);
        if (right != null) {
            p.add(right, BorderLayout.EAST);
        }
        return p;
    }

    private static JScrollPane styledList(JList<String> list, ListCellRenderer<String> renderer, int cellHeight) {
        list.setCellRenderer(renderer);
        list.setBackground(Theme.SIDEBAR_BG);
        list.setFixedCellHeight(cellHeight);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane pane = new JScrollPane(list);
        pane.getViewport().setBackground(Theme.SIDEBAR_BG);
        ModernScrollBarUI.apply(pane, new Color(255, 255, 255, 60));
        return pane;
    }

    // ---------------------------------------------------------------- user menu (right-click)

    private void showUserMenu(MouseEvent e) {
        int index = userList.locationToIndex(e.getPoint());
        Rectangle bounds = index < 0 ? null : userList.getCellBounds(index, index);
        if (bounds == null || !bounds.contains(e.getPoint())) {
            return;
        }
        userList.setSelectedIndex(index);
        String target = nameOf(userModel.get(index));
        if (target.equals(username)) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        JMenuItem message = new JMenuItem("Send private message");
        message.addActionListener(a -> startPrivateMessage(target));
        menu.add(message);
        if (admin) {
            menu.addSeparator();
            JMenuItem kick = new JMenuItem("Kick " + target);
            kick.addActionListener(a -> moderate(MessageType.KICK, target, "Kick"));
            JMenuItem ban = new JMenuItem("Ban " + target);
            ban.addActionListener(a -> moderate(MessageType.BAN, target, "Ban"));
            menu.add(kick);
            menu.add(ban);
        }
        menu.show(userList, e.getX(), e.getY());
    }

    private void moderate(MessageType type, String target, String verb) {
        int answer = JOptionPane.showConfirmDialog(this, verb + " " + target + "?", "Confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            sendPacket(new TextMessage(type, username, target, ""));
        }
    }

    private void startPrivateMessage(String target) {
        inputField.setText("/msg " + target + " ");
        inputField.requestFocusInWindow();
    }

    // ---------------------------------------------------------------- sending

    private void sendFromInput() {
        String text = inputField.getText().trim();
        inputField.setText("");
        if (text.isEmpty() || !connected) {
            return;
        }
        if (text.equalsIgnoreCase("/quit")) {
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
            return;
        }
        Message message = CommandParser.parse(username, text, this::showLocal);
        if (message != null) {
            sendPacket(message);
        }
    }

    private void sendPacket(Message message) {
        if (!connected) {
            return;
        }
        try {
            connection.send(message);
        } catch (IOException e) {
            chatView.addError("Could not send: " + e.getMessage());
        }
    }

    private void joinRoom(String room) {
        sendPacket(new TextMessage(MessageType.JOIN, username, room, ""));
    }

    private void askForRoom() {
        String room = JOptionPane.showInputDialog(this, "Room name (letters, digits, - or _):",
                "Join or create a room", JOptionPane.PLAIN_MESSAGE);
        if (room != null && !room.isBlank()) {
            joinRoom(room.trim());
        }
    }

    private void askForSearch() {
        String keyword = JOptionPane.showInputDialog(this, "Search messages for:", "Search", JOptionPane.PLAIN_MESSAGE);
        if (keyword != null && !keyword.isBlank()) {
            sendPacket(new TextMessage(MessageType.SEARCH, username, null, keyword.trim()));
        }
    }

    private void requestLists() {
        if (!connected) {
            return;
        }
        try {
            connection.send(new TextMessage(MessageType.USER_LIST, username, null, ""));
            connection.send(new TextMessage(MessageType.ROOMS, username, null, ""));
        } catch (IOException ignored) {
            // the reader thread will report the disconnect
        }
    }

    // ---------------------------------------------------------------- files

    /** Attach button: pick a file and send it to the room, or to the selected online user. */
    private void chooseAndSendFile() {
        if (!connected) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a file to send (max 5 MB)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            long size = Files.size(path);
            if (size == 0) {
                chatView.addError("That file is empty.");
                return;
            }
            if (size > MAX_FILE_BYTES) {
                chatView.addError("That file is too large. The limit is 5 MB.");
                return;
            }
            String target = askFileTarget();
            if (target == null) {
                return;   // cancelled
            }
            byte[] data = Files.readAllBytes(path);
            String receiver = target.isEmpty() ? null : target;
            sendPacket(new FileMessage(username, receiver, path.getFileName().toString(), data, receiver != null));
        } catch (IOException e) {
            chatView.addError("Could not read the file: " + e.getMessage());
        }
    }

    /** @return "" for the current room, a username for a private file, or null if the user cancelled */
    private String askFileTarget() {
        String selected = userList.getSelectedValue();
        String other = selected == null ? null : nameOf(selected);
        if (other == null || other.equals(username)) {
            return "";
        }
        Object[] options = {"#" + currentRoom, other, "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Send the file to:", "Send file",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            return "";
        }
        return choice == 1 ? other : null;
    }

    /** Called when the user clicks a file card. */
    private void saveReceivedFile(String fileName, byte[] data) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save file");
        chooser.setSelectedFile(new File(fileName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (Files.exists(target) && !confirmOverwrite(target)) {
            return;
        }
        try {
            Files.write(target, data);
            chatView.addNotice("Saved " + target.getFileName() + " to " + target.getParent());
        } catch (IOException e) {
            chatView.addError("Could not save the file: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- export

    /** Export button: saves everything shown in the chat as a .txt or .csv file. */
    private void exportChat() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export this conversation");
        chooser.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter txt = new FileNameExtensionFilter("Text file (*.txt)", "txt");
        FileNameExtensionFilter csv = new FileNameExtensionFilter("CSV file (*.csv)", "csv");
        chooser.addChoosableFileFilter(txt);
        chooser.addChoosableFileFilter(csv);
        chooser.setFileFilter(txt);
        chooser.setSelectedFile(new File("chat-" + currentRoom + "-" + LocalDateTime.now().format(STAMP) + ".txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        String lower = file.getName().toLowerCase();
        boolean asCsv;
        if (lower.endsWith(".csv")) {
            asCsv = true;
        } else if (lower.endsWith(".txt")) {
            asCsv = false;
        } else {
            asCsv = chooser.getFileFilter() == csv;
            file = new File(file.getParentFile(), file.getName() + (asCsv ? ".csv" : ".txt"));
        }
        if (file.exists() && !confirmOverwrite(file.toPath())) {
            return;
        }
        try {
            // The BOM (\uFEFF) makes Excel read the CSV as UTF-8.
            String content = asCsv ? "\uFEFF" + chatView.toCsv() : chatView.toText();
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            chatView.addNotice("Conversation exported to " + file.getName());
        } catch (IOException e) {
            chatView.addError("Could not export: " + e.getMessage());
        }
    }

    private boolean confirmOverwrite(Path path) {
        int answer = JOptionPane.showConfirmDialog(this, path.getFileName() + " already exists. Replace it?",
                "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    // ---------------------------------------------------------------- help

    /** Output of local commands (/help and usage hints). */
    private void showLocal(String text) {
        if (CommandParser.HELP.equals(text)) {
            showHelp();
        } else {
            chatView.addNotice(text);
        }
    }

    private void showHelp() {
        StringBuilder html = new StringBuilder("<html><body style='width:400px'>");
        html.append("<h3 style='margin-top:0'>Commands</h3><table cellpadding='3'>");
        html.append(helpRow("/msg user text", "send a private message"));
        html.append(helpRow("/join room", "switch to (or create) a room"));
        html.append(helpRow("/rooms", "list rooms"));
        html.append(helpRow("/who", "see who is online"));
        html.append(helpRow("/history", "last messages of this room"));
        html.append(helpRow("/history user", "your private chat with that user"));
        html.append(helpRow("/search text", "search messages"));
        html.append(helpRow("/quit", "leave the chat"));
        html.append("</table>");
        if (admin) {
            html.append("<h3>Admin commands</h3><table cellpadding='3'>");
            html.append(helpRow("/users", "list every account"));
            html.append(helpRow("/kick user", "disconnect a user"));
            html.append(helpRow("/ban user", "ban a user"));
            html.append(helpRow("/unban user", "lift a ban"));
            html.append("</table>");
        }
        html.append("<br>Tips: use <b>Attach</b> to send a file (select an online user first to send it privately), ")
                .append("click a file card to save it, double-click a user to message them, ")
                .append("and right-click a message to copy it.</body></html>");
        JOptionPane.showMessageDialog(this, new JLabel(html.toString()), "Help", JOptionPane.PLAIN_MESSAGE);
    }

    private static String helpRow(String command, String description) {
        return "<tr><td><b>" + command + "</b></td><td>" + description + "</td></tr>";
    }

    // ---------------------------------------------------------------- receiving (Listener)

    /** Reader thread -> hop onto the Swing thread before touching any component. */
    @Override
    public void onMessage(Message message) {
        SwingUtilities.invokeLater(() -> handle(message));
    }

    @Override
    public void onDisconnect() {
        SwingUtilities.invokeLater(this::handleDisconnect);
    }

    private void handle(Message message) {
        if (message instanceof ListMessage) {
            updateLists((ListMessage) message);
        } else if (message instanceof HistoryMessage) {
            showHistory((HistoryMessage) message);
        } else if (message instanceof FileMessage) {
            showFile((FileMessage) message);
        } else if (message instanceof TextMessage) {
            showText((TextMessage) message);
        } else {
            chatView.addSystem(message.display());
        }
    }

    private void showText(TextMessage t) {
        String time = t.getTimestamp().format(HM);
        switch (t.getType()) {
            case TEXT:
                if ("SERVER".equals(t.getSender())) {
                    chatView.addSystem(t.getContent());
                } else {
                    chatView.addMessage(isMe(t.getSender()), t.getSender(), t.getContent(), time, false, null);
                }
                break;
            case PRIVATE: {
                boolean mine = isMe(t.getSender());
                String peer = mine ? t.getReceiver() : t.getSender();
                chatView.addMessage(mine, t.getSender(), t.getContent(), time, true, peer);
                break;
            }
            case ERROR:
                chatView.addError(t.getContent());
                break;
            default:
                chatView.addNotice(t.getContent());
        }
    }

    private void showFile(FileMessage f) {
        boolean mine = isMe(f.getSender());
        String peer = mine ? f.getReceiver() : f.getSender();
        chatView.addFile(mine, f.getSender(), f.getFileName(), f.getData(),
                f.getTimestamp().format(HM), f.isPrivate(), peer);
    }

    private void showHistory(HistoryMessage history) {
        chatView.addDivider(history.getTitle());
        if (history.getRecords().isEmpty()) {
            chatView.addSystem("Nothing found.");
        }
        for (ChatRecord r : history.getRecords()) {
            String time = timeOf(r.sentAt());
            boolean mine = isMe(r.sender());
            if (r.type() == MessageType.PRIVATE) {
                chatView.addMessage(mine, r.sender(), r.content(), time, true, mine ? r.receiver() : r.sender());
            } else {
                String body = r.type() == MessageType.FILE ? "Sent a file: " + r.content() : r.content();
                chatView.addMessage(mine, r.sender(), body, time, false, null);
            }
        }
        chatView.addDivider("end");
    }

    private void updateLists(ListMessage list) {
        if (list.getType() == MessageType.ROOMS) {
            String room = list.getHeader();
            if (room != null && !room.equals(currentRoom)) {
                currentRoom = room;
                roomLabel.setText("# " + room);
                inputField.setPlaceholder("Message #" + room);
            }
            refill(roomModel, list.getItems());
            roomList.repaint();   // moves the highlight to the current room
        } else {
            admin = "ADMIN".equals(list.getHeader());
            statusLabel.setText(admin ? "Admin \u00B7 Online" : "Online");
            refill(userModel, list.getItems());
            onlineLabel.setText("ONLINE \u00B7 " + userModel.size());
        }
    }

    private void handleDisconnect() {
        if (!connected) {
            return;
        }
        connected = false;
        refreshTimer.stop();
        chatView.addError("Disconnected from the server. Close this window and log in again.");
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
    }

    // ---------------------------------------------------------------- helpers

    private boolean isMe(String name) {
        return username.equals(name);
    }

    private static String timeOf(LocalDateTime t) {
        return t.toLocalDate().equals(LocalDate.now()) ? t.format(HM) : t.format(DAY_HM);
    }

    /** Only touches the list if something changed, so a selection is not lost on every refresh. */
    private static void refill(DefaultListModel<String> model, List<String> items) {
        boolean same = model.size() == items.size();
        for (int i = 0; same && i < items.size(); i++) {
            same = model.get(i).equals(items.get(i));
        }
        if (!same) {
            model.clear();
            items.forEach(model::addElement);
        }
    }

    /** "#general (2)" or "reddy [#general]" -> "general" / "reddy" */
    private static String nameOf(String listItem) {
        return listItem.split(" ")[0].replaceFirst("^#", "");
    }
}
