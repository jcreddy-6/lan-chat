package com.lanchat.server;

import com.lanchat.common.ChatRecord;
import com.lanchat.common.FileMessage;
import com.lanchat.common.HistoryMessage;
import com.lanchat.common.ListMessage;
import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.Role;
import com.lanchat.common.Status;
import com.lanchat.common.TextMessage;
import com.lanchat.common.User;
import com.lanchat.db.DataAccessException;
import com.lanchat.db.MessageDAO;
import com.lanchat.db.UserDAO;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Serves one connected client on its own thread. */
public class ClientHandler implements Runnable {

    private static final int MAX_AUTH_ATTEMPTS = 10;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;   // 5 MB
    private static final int HISTORY_LIMIT = 20;
    private static final int SEARCH_LIMIT = 30;
    private static final String DEFAULT_ROOM = "general";

    private final Socket socket;
    private final ChatServer server;
    private final UserDAO userDao = new UserDAO();
    private final MessageDAO messageDao = new MessageDAO();
    private final Object sendLock = new Object();

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile User user;                     // set only after a successful login
    private volatile String username;
    private volatile String room = DEFAULT_ROOM;    // the room this user is currently in

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    String getUsername() { return username; }

    String getRoom() { return room; }

    @Override
    public void run() {
        try {
            // Output stream first, then input stream (the client does the same) to avoid a deadlock.
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            if (authenticate()) {
                listen();
            }
        } catch (EOFException | SocketException e) {
            // client closed the connection (or was kicked): normal
        } catch (IOException | ClassNotFoundException e) {
            server.log("Connection error (" + describe() + "): " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    // ---------------------------------------------------------------- login / register

    /** Lets the client register and/or log in, up to MAX_AUTH_ATTEMPTS requests. */
    private boolean authenticate() throws IOException, ClassNotFoundException {
        for (int i = 0; i < MAX_AUTH_ATTEMPTS; i++) {
            Object obj = in.readObject();
            if (!(obj instanceof TextMessage)) {
                send(system(MessageType.ERROR, "Please log in or register first."));
                continue;
            }
            TextMessage m = (TextMessage) obj;
            try {
                if (m.getType() == MessageType.REGISTER) {
                    handleRegister(m);
                } else if (m.getType() == MessageType.LOGIN) {
                    if (handleLogin(m)) {
                        return true;
                    }
                } else {
                    send(system(MessageType.ERROR, "Please log in or register first."));
                }
            } catch (DataAccessException e) {
                server.log("Database error: " + e.getMessage());
                send(system(MessageType.ERROR, "Server database error. Please try again later."));
            }
        }
        send(system(MessageType.ERROR, "Too many attempts. Disconnecting."));
        return false;
    }

    private void handleRegister(TextMessage m) throws DataAccessException {
        String name = String.valueOf(m.getSender()).trim();
        String password = m.getContent() == null ? "" : m.getContent();

        if (!name.matches("[A-Za-z0-9_]{3,20}")) {
            send(system(MessageType.ERROR, "Username must be 3-20 letters, digits or underscores."));
        } else if (password.length() < 4 || password.length() > 50) {
            send(system(MessageType.ERROR, "Password must be 4-50 characters."));
        } else if (userDao.register(name, password, Role.MEMBER)) {
            server.log("New account registered: " + name);
            send(system(MessageType.OK, "Account created. You can log in now."));
        } else {
            send(system(MessageType.ERROR, "That username is already taken."));
        }
    }

    private boolean handleLogin(TextMessage m) throws DataAccessException {
        String name = String.valueOf(m.getSender()).trim();
        String password = m.getContent() == null ? "" : m.getContent();

        Optional<User> found = userDao.authenticate(name, password);
        if (found.isEmpty()) {
            // Same message for "no such user" and "wrong password" so nobody can probe for usernames.
            server.log("Failed login for '" + name + "' from " + socket.getRemoteSocketAddress());
            send(system(MessageType.ERROR, "Invalid username or password."));
            return false;
        }
        User account = found.get();
        if (account.getStatus() == Status.BANNED) {
            server.log("Banned user tried to log in: " + name);
            send(system(MessageType.ERROR, "This account has been banned."));
            return false;
        }
        if (!server.markOnline(account.getUsername(), this)) {
            send(system(MessageType.ERROR, "This account is already logged in somewhere else."));
            return false;
        }

        account.setStatus(Status.ONLINE);
        this.user = account;
        this.username = account.getUsername();
        server.log(username + " logged in as " + account.getRole() + " (" + server.onlineCount() + " online)");
        send(system(MessageType.OK, "Welcome, " + username + "! Role: " + account.getRole()
                + ". You are in #" + room + ". Type /help for commands."));
        sendRoomHistory();
        handleRooms();
        handleWho();
        server.broadcastToRoom(room, system(MessageType.TEXT, username + " joined #" + room));
        return true;
    }

    // ---------------------------------------------------------------- chatting

    private void listen() throws IOException, ClassNotFoundException {
        while (true) {
            Object obj = in.readObject();
            if (!(obj instanceof Message)) {
                send(system(MessageType.ERROR, "Unsupported message."));
                continue;
            }
            Message m = (Message) obj;
            if (m.getType() == MessageType.LOGOUT) {
                return;
            }
            try {
                dispatch(m);
            } catch (DataAccessException e) {
                server.log("Database error for " + username + ": " + e.getMessage());
                send(system(MessageType.ERROR, "Server database error. Please try again."));
            }
        }
    }

    private void dispatch(Message m) throws DataAccessException {
        if (m instanceof FileMessage) {
            handleFile((FileMessage) m);
            return;
        }
        if (!(m instanceof TextMessage)) {
            send(system(MessageType.ERROR, "Unsupported message."));
            return;
        }
        TextMessage t = (TextMessage) m;
        switch (t.getType()) {
            case TEXT:
                handleRoomText(t);
                break;
            case PRIVATE:
                handlePrivate(t);
                break;
            case JOIN:
                handleJoin(t);
                break;
            case ROOMS:
                handleRooms();
                break;
            case USER_LIST:
                handleWho();
                break;
            case HISTORY:
                handleHistory(t);
                break;
            case SEARCH:
                handleSearch(t);
                break;
            case KICK:
                handleKick(t);
                break;
            case BAN:
                handleBan(t);
                break;
            case UNBAN:
                handleUnban(t);
                break;
            case USERS:
                handleUsers();
                break;
            default:
                send(system(MessageType.ERROR, "Not supported: " + t.getType()));
        }
    }

    /** Normal message to everyone in the sender's current room. */
    private void handleRoomText(TextMessage t) {
        String content = cleanContent(t);
        if (content == null) {
            return;
        }
        // Rebuild with the server-known sender and room so nobody can fake them.
        Message m = new TextMessage(MessageType.TEXT, username, room, content);
        save(m);
        server.broadcastToRoom(room, m);
    }

    /** /msg user text */
    private void handlePrivate(TextMessage t) throws DataAccessException {
        String to = t.getReceiver() == null ? "" : t.getReceiver().trim();
        String content = cleanContent(t);
        if (to.isEmpty() || content == null) {
            send(system(MessageType.ERROR, "Usage: /msg <user> <text>"));
            return;
        }
        Optional<User> target = userDao.findByUsername(to);
        if (target.isEmpty()) {
            send(system(MessageType.ERROR, "No such user: " + to));
            return;
        }
        String toName = target.get().getUsername();
        if (toName.equals(username)) {
            send(system(MessageType.ERROR, "You cannot message yourself."));
            return;
        }

        Message pm = new TextMessage(MessageType.PRIVATE, username, toName, content);
        save(pm);
        send(pm);   // echo to the sender so they see what they sent
        if (!server.sendTo(toName, pm)) {
            send(system(MessageType.OK, toName + " is offline. Message saved; they can read it with /history " + username));
        }
    }

    /** A file for the whole room (receiver empty) or for one user. Only the file name is stored in the database. */
    private void handleFile(FileMessage f) throws DataAccessException {
        byte[] data = f.getData();
        if (data == null || data.length == 0) {
            send(system(MessageType.ERROR, "That file is empty."));
            return;
        }
        if (data.length > MAX_FILE_BYTES) {
            send(system(MessageType.ERROR, "File too large (limit is " + MAX_FILE_BYTES / (1024 * 1024) + " MB)."));
            return;
        }
        String name = safeFileName(f.getFileName());
        String to = f.getReceiver() == null ? "" : f.getReceiver().trim();

        if (to.isEmpty()) {
            Message shared = new FileMessage(username, room, name, data, false);
            save(shared);
            server.log(username + " shared " + name + " (" + data.length / 1024 + " KB) in #" + room);
            server.broadcastToRoom(room, shared);
            return;
        }

        Optional<User> target = userDao.findByUsername(to);
        if (target.isEmpty()) {
            send(system(MessageType.ERROR, "No such user: " + to));
            return;
        }
        String toName = target.get().getUsername();
        if (toName.equals(username)) {
            send(system(MessageType.ERROR, "You cannot send a file to yourself."));
            return;
        }
        Message direct = new FileMessage(username, toName, name, data, true);
        save(direct);
        server.log(username + " sent " + name + " (" + data.length / 1024 + " KB) to " + toName);
        send(direct);   // the sender's own copy
        if (!server.sendTo(toName, direct)) {
            send(system(MessageType.ERROR, toName + " is offline, so the file was not delivered "
                    + "(files are not stored on the server)."));
        }
    }

    /** /join room: switch (or create) a room. */
    private void handleJoin(TextMessage t) {
        String target = t.getReceiver() == null ? "" : t.getReceiver().trim().toLowerCase();
        if (!target.matches("[a-z0-9_-]{1,20}")) {
            send(system(MessageType.ERROR, "Room names are 1-20 letters, digits, - or _."));
            return;
        }
        String old = room;
        if (old.equals(target)) {
            send(system(MessageType.OK, "You are already in #" + target));
            return;
        }
        room = target;
        server.broadcastToRoom(old, system(MessageType.TEXT, username + " left #" + old));
        server.broadcastToRoom(target, system(MessageType.TEXT, username + " joined #" + target));
        sendRoomHistory();
        handleRooms();   // also tells the client which room it is in now
    }

    private void handleRooms() {
        List<String> rooms = server.roomCounts().entrySet().stream()
                .map(e -> "#" + e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.toList());
        send(new ListMessage(MessageType.ROOMS, room, rooms));
    }

    /** The header carries the requester's role so the GUI knows whether to show admin actions. */
    private void handleWho() {
        String role = user == null ? null : user.getRole().name();
        send(new ListMessage(MessageType.USER_LIST, role, server.onlineSummary()));
    }

    /** /history shows the current room, /history bob shows your private chat with bob. */
    private void handleHistory(TextMessage t) throws DataAccessException {
        String other = t.getReceiver() == null ? "" : t.getReceiver().trim();
        if (other.isEmpty()) {
            List<ChatRecord> recent = messageDao.roomHistory(room, HISTORY_LIMIT);
            send(new HistoryMessage("Last messages in #" + room, room, recent));
        } else {
            List<ChatRecord> recent = messageDao.privateHistory(username, other, HISTORY_LIMIT);
            send(new HistoryMessage("Private chat with " + other, other, recent));
        }
    }

    /** /search text: group messages plus your own private messages. */
    private void handleSearch(TextMessage t) throws DataAccessException {
        String raw = t.getContent() == null ? "" : t.getContent().trim();
        if (raw.isEmpty()) {
            send(system(MessageType.ERROR, "Usage: /search <text>"));
            return;
        }
        String keyword = cleanContent(t);   // also enforces the length limit
        if (keyword == null) {
            return;
        }
        List<ChatRecord> found = messageDao.search(keyword, username, SEARCH_LIMIT);
        send(new HistoryMessage("Search results for '" + keyword + "'", null, found));
    }

    // ---------------------------------------------------------------- admin commands

    /** @return true if the logged-in user may moderate (polymorphism: Admin overrides canModerate) */
    private boolean isModerator() {
        return user != null && user.canModerate();
    }

    /** Checks permission and target for /kick, /ban and /unban. Sends the error itself and returns empty if invalid. */
    private Optional<User> moderationTarget(TextMessage t) throws DataAccessException {
        if (!isModerator()) {
            send(system(MessageType.ERROR, "Only admins can use that command."));
            return Optional.empty();
        }
        String name = t.getReceiver() == null ? "" : t.getReceiver().trim();
        if (name.isEmpty()) {
            send(system(MessageType.ERROR, "Usage: /kick <user>, /ban <user> or /unban <user>"));
            return Optional.empty();
        }
        Optional<User> found = userDao.findByUsername(name);
        if (found.isEmpty()) {
            send(system(MessageType.ERROR, "No such user: " + name));
            return Optional.empty();
        }
        User target = found.get();
        if (target.getUsername().equals(username) || target.canModerate()) {
            send(system(MessageType.ERROR, "You cannot moderate admins (including yourself)."));
            return Optional.empty();
        }
        return found;
    }

    private void handleKick(TextMessage t) throws DataAccessException {
        Optional<User> target = moderationTarget(t);
        if (target.isEmpty()) {
            return;
        }
        String name = target.get().getUsername();
        if (server.kick(name, "You were kicked by " + username + ".")) {
            server.log(username + " kicked " + name);
            send(system(MessageType.OK, name + " was kicked."));
        } else {
            send(system(MessageType.ERROR, name + " is not online."));
        }
    }

    private void handleBan(TextMessage t) throws DataAccessException {
        Optional<User> target = moderationTarget(t);
        if (target.isEmpty()) {
            return;
        }
        String name = target.get().getUsername();
        userDao.setBanned(name, true);
        boolean wasOnline = server.kick(name, "You have been banned by " + username + ".");
        server.log(username + " banned " + name);
        send(system(MessageType.OK, name + " was banned" + (wasOnline ? " and disconnected." : ".")));
    }

    private void handleUnban(TextMessage t) throws DataAccessException {
        Optional<User> target = moderationTarget(t);
        if (target.isEmpty()) {
            return;
        }
        String name = target.get().getUsername();
        userDao.setBanned(name, false);
        server.log(username + " unbanned " + name);
        send(system(MessageType.OK, name + " was unbanned."));
    }

    /** /users (admins): every registered account with role and state. */
    private void handleUsers() throws DataAccessException {
        if (!isModerator()) {
            send(system(MessageType.ERROR, "Only admins can use that command."));
            return;
        }
        List<String> accounts = userDao.findAll().stream()
                .map(u -> u.getUsername() + " (" + u.getRole()
                        + (u.getStatus() == Status.BANNED ? ", BANNED" : "")
                        + (server.isOnline(u.getUsername()) ? ", online" : "") + ")")
                .collect(Collectors.toList());
        send(system(MessageType.OK, "Accounts (" + accounts.size() + "): " + String.join(", ", accounts)));
    }

    // ---------------------------------------------------------------- helpers

    /** Sends the last messages of the current room. Never throws: history is nice-to-have. */
    private void sendRoomHistory() {
        try {
            List<ChatRecord> recent = messageDao.roomHistory(room, HISTORY_LIMIT);
            send(new HistoryMessage("Last messages in #" + room, room, recent));
        } catch (DataAccessException e) {
            server.log("Could not load history: " + e.getMessage());
        }
    }

    private void save(Message m) {
        try {
            messageDao.save(m);
        } catch (DataAccessException e) {
            server.log("Could not save message from " + username + ": " + e.getMessage());
        }
    }

    /** @return trimmed text, or null (after telling the user) if it is empty or too long */
    private String cleanContent(TextMessage t) {
        String content = t.getContent() == null ? "" : t.getContent().trim();
        if (content.isEmpty()) {
            return null;
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            send(system(MessageType.ERROR, "Message too long (max " + MAX_MESSAGE_LENGTH + " characters)."));
            return null;
        }
        return content;
    }

    /** Removes any path and odd characters from a file name sent by a client. */
    static String safeFileName(String name) {
        String n = name == null ? "" : name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = n.replaceAll("[^A-Za-z0-9._ ()\\-]", "_").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) {
            n = "file";
        }
        if (n.length() > 80) {
            n = n.substring(n.length() - 80);   // keep the end so the extension survives
        }
        return n;
    }

    public void send(Message message) {
        try {
            synchronized (sendLock) {
                out.writeObject(message);
                out.flush();
                out.reset();   // forget cached objects so memory does not grow
            }
        } catch (IOException e) {
            server.log("Could not send to " + describe() + ": " + e.getMessage());
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    private void disconnect() {
        String name = this.username;
        if (name != null && server.markOffline(name, this)) {
            if (user != null) {
                user.setStatus(Status.OFFLINE);
            }
            server.log(name + " disconnected (" + server.onlineCount() + " online)");
            server.broadcastToRoom(room, system(MessageType.TEXT, name + " left the chat"));
        }
        close();
    }

    private String describe() {
        return username != null ? username : String.valueOf(socket.getRemoteSocketAddress());
    }

    private static TextMessage system(MessageType type, String text) {
        return new TextMessage(type, "SERVER", null, text);
    }
}
