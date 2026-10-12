package com.lanchat.client;

import com.lanchat.common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Owns the socket for the GUI client. Incoming packets are reported to a Listener (Observer pattern). */
public class ChatConnection {

    /** Called on the reader thread, NOT the Swing thread. */
    public interface Listener {
        void onMessage(Message message);

        void onDisconnect();
    }

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private volatile Listener listener;
    private volatile boolean closed;

    public ChatConnection(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 4000);
        // Output stream first, then input stream (same order as the server) to avoid a deadlock.
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    public synchronized void send(Message message) throws IOException {
        out.writeObject(message);
        out.flush();
        out.reset();
    }

    /** Blocking read, used by the login window before the reader thread starts. */
    public Message readOne() throws IOException, ClassNotFoundException {
        return (Message) in.readObject();
    }

    public void startListening(Listener newListener) {
        this.listener = newListener;
        Thread reader = new Thread(this::readLoop, "chat-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        try {
            while (!closed) {
                Object obj = in.readObject();
                if (obj instanceof Message) {
                    listener.onMessage((Message) obj);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // connection ended
        } finally {
            if (!closed) {
                closed = true;
                listener.onDisconnect();
            }
        }
    }

    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
