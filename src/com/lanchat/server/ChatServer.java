package com.lanchat.server;

import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.Role;
import com.lanchat.common.TextMessage;
import com.lanchat.db.DBConnection;
import com.lanchat.db.DataAccessException;
import com.lanchat.db.UserDAO;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
 * The chat server. Accepts TCP connections and hands each client to a ClientHandler
 * running on a thread from the pool.
 */
public class ChatServer {

    public static final int PORT = 5000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** username -> handler. ConcurrentHashMap because many client threads touch it at once. */
    private final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public void start() throws IOException, DataAccessException {
        DBConnection.getInstance().init();
        ensureAdminExists();

        serverSocket = new ServerSocket(PORT);
        log("Server started on port " + PORT + ". Waiting for clients...");
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                log("Connection from " + socket.getRemoteSocketAddress());
                pool.execute(new ClientHandler(socket, this));
            } catch (IOException e) {
                if (running) {
                    log("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    /** A fresh database has no admin, so create a default one (uses a stream to check). */
    private void ensureAdminExists() throws DataAccessException {
        UserDAO users = new UserDAO();
        boolean hasAdmin = users.findAll().stream().anyMatch(u -> u.getRole() == Role.ADMIN);
        if (!hasAdmin) {
            users.register("admin", "admin123", Role.ADMIN);
            log("No admin account found: created default admin 'admin' / 'admin123'.");
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // shutting down anyway
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
        } catch (IOException e) {
            System.err.println("Could not start server (is port " + PORT + " already in use?): " + e.getMessage());
        }
    }
}
