package com.lanchat.server;

import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.Role;
import com.lanchat.common.TextMessage;
import com.lanchat.db.DBConnection;
import com.lanchat.db.DataAccessException;
import com.lanchat.db.UserDAO;

import com.lanchat.net.WsStreams;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * The chat server. Accepts WebSocket connections and hands each client to a ClientHandler
 * running on a thread from the pool. (WebSocket instead of raw TCP so it can run on Render.)
 */
public class ChatServer {

    /** Render tells the app which port to use through the PORT environment variable. */
    public static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "5000"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** username -> handler. ConcurrentHashMap because many client threads touch it at once. */
    private final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private volatile boolean running = true;
    private WebSocketServer webSocketServer;

    public void start() throws DataAccessException {
        DBConnection.getInstance().init();
        ensureAdminExists();

        // 0.0.0.0 = listen on all network interfaces (required on Render)
        webSocketServer = new Listener(new InetSocketAddress("0.0.0.0", PORT));
        webSocketServer.setReuseAddr(true);
        webSocketServer.setConnectionLostTimeout(30);   // ping every 30 s: keeps idle connections alive behind proxies
        webSocketServer.start();                        // runs on its own thread; the JVM stays up while it runs
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    /** Receives WebSocket events and feeds them to a ClientHandler through WsStreams. */
    private final class Listener extends WebSocketServer {

        Listener(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onStart() {
            log("Server started on port " + PORT + ". Waiting for clients...");
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String remote = String.valueOf(conn.getRemoteSocketAddress());
            log("Connection from " + remote);
            WsStreams streams = new WsStreams(remote);
            streams.attach(new WsStreams.Transport() {
                @Override
                public void sendBinary(byte[] data) throws java.io.IOException {
                    try {
                        conn.send(data);
                    } catch (RuntimeException e) {   // e.g. WebsocketNotConnectedException
                        throw new java.io.IOException("WebSocket send failed: " + e.getMessage(), e);
                    }
                }

                @Override
                public void close() {
                    conn.close();
                }
            });
            conn.setAttachment(streams);
            pool.execute(new ClientHandler(streams, ChatServer.this));
        }

        @Override
        public void onMessage(WebSocket conn, ByteBuffer message) {
            WsStreams streams = conn.getAttachment();
            if (streams != null) {
                byte[] data = new byte[message.remaining()];
                message.get(data);
                streams.onData(data);
            }
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            // this chat only uses binary frames; ignore text
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            WsStreams streams = conn.getAttachment();
            if (streams != null) {
                streams.onClosed();
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            if (conn == null) {   // the server itself failed (for example the port is already in use)
                System.err.println("Could not start server on port " + PORT + ": " + ex.getMessage());
                System.exit(1);
            }
            WsStreams streams = conn.getAttachment();
            if (streams != null) {
                streams.onClosed();
            }
        }
    }

    /** A fresh database has no admin, so create a default one (uses a stream to check). */
    private void ensureAdminExists() throws DataAccessException {
        UserDAO users = new UserDAO();
        boolean hasAdmin = users.findAll().stream().anyMatch(u -> u.getRole() == Role.ADMIN);
        if (!hasAdmin) {
            // Set ADMIN_PASSWORD on Render so the public server never uses the well-known default.
            String password = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123");
            users.register("admin", password, Role.ADMIN);
            log("No admin account found: created admin account 'admin'"
                    + (System.getenv("ADMIN_PASSWORD") == null ? " with the DEFAULT password 'admin123' - change it!" : "."));
        }
    }

    public void stop() {
        running = false;
        try {
            if (webSocketServer != null) {
                webSocketServer.stop(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        online.values().forEach(ClientHandler::close);
        pool.shutdownNow();
        log("Server stopped.");
    }

    /** Marks a user as online. @return false if that username is already online */
    boolean markOnline(String username, ClientHandler handler) {
        return online.putIfAbsent(username, handler) == null;
    }

    /** Removes the user only if the stored handler is this one. */
    boolean markOffline(String username, ClientHandler handler) {
        return online.remove(username, handler);
    }

    /** Sends to every logged-in user (used for server-wide announcements). */
    void broadcast(Message message) {
        online.values().forEach(h -> h.send(message));
    }

    /** Sends only to users currently in the given room. */
    void broadcastToRoom(String room, Message message) {
        online.values().stream()
                .filter(h -> room.equals(h.getRoom()))
                .forEach(h -> h.send(message));
    }

    /** Sends to one user. @return false if that user is not online */
    boolean sendTo(String username, Message message) {
        ClientHandler handler = online.get(username);
        if (handler == null) {
            return false;
        }
        handler.send(message);
        return true;
    }

    /** room name -> number of users in it (sorted by name). */
    Map<String, Long> roomCounts() {
        return online.values().stream()
                .collect(Collectors.groupingBy(ClientHandler::getRoom, TreeMap::new, Collectors.counting()));
    }

    /** e.g. ["reddy [#general]", "rohith [#dev]"] */
    List<String> onlineSummary() {
        return online.values().stream()
                .map(h -> h.getUsername() + " [#" + h.getRoom() + "]")
                .sorted()
                .collect(Collectors.toList());
    }

    boolean isOnline(String username) {
        return online.containsKey(username);
    }

    /** Tells a user why they are being removed, then closes their connection. @return false if not online */
    boolean kick(String username, String reason) {
        ClientHandler handler = online.get(username);
        if (handler == null) {
            return false;
        }
        handler.send(new TextMessage(MessageType.ERROR, "SERVER", null, reason));
        handler.close();
        return true;
    }

    int onlineCount() {
        return online.size();
    }

    void log(String text) {
        System.out.println("[" + LocalTime.now().format(TIME) + "] " + text);
    }

    public static void main(String[] args) {
        try {
            new ChatServer().start();
        } catch (DataAccessException e) {
            System.err.println("Database problem: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
        }
    }
}
