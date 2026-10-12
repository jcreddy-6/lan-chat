package com.lanchat.client;

import com.lanchat.common.Message;
import com.lanchat.net.WsStreams;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Owns the connection for the GUI client. It talks WebSocket (the JDK's built-in client, nothing to install)
 * but wraps it in WsStreams, so the rest of the code still sees plain object streams.
 * Incoming packets are reported to a Listener (Observer pattern).
 */
public class ChatConnection {

    /** Called on the reader thread, NOT the Swing thread. */
    public interface Listener {
        void onMessage(Message message);

        void onDisconnect();
    }

    private final WebSocket webSocket;
    private final WsStreams streams;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private volatile Listener listener;
    private volatile boolean closed;

    /** @param url for example wss://your-app.onrender.com (deployed) or ws://localhost:5000 (local) */
    public ChatConnection(String url) throws IOException {
        URI uri = parse(url);
        streams = new WsStreams(uri.getHost());
        try {
            webSocket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, new Receiver(streams))
                    .get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while connecting");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException(cause.getMessage() != null ? cause.getMessage() : cause.toString(), cause);
        } catch (TimeoutException e) {
            throw new IOException("Connection timed out (a free Render server may take a minute to wake up)");
        }
        streams.attach(new WsStreams.Transport() {
            @Override
            public void sendBinary(byte[] data) throws IOException {
                try {
                    webSocket.sendBinary(ByteBuffer.wrap(data), true).get(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while sending");
                } catch (ExecutionException | TimeoutException e) {
                    throw new IOException("WebSocket send failed: " + e.getMessage(), e);
                }
            }

            @Override
            public void close() {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                        .orTimeout(2, TimeUnit.SECONDS)
                        .whenComplete((ok, err) -> webSocket.abort());
            }
        });
        // Output stream first, then input stream (same order as the server) to avoid a deadlock.
        out = new ObjectOutputStream(streams.getOutputStream());
        out.flush();
        in = new ObjectInputStream(streams.getInputStream());
        in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(WsStreams.ALLOWED_CLASSES));
    }

    /** Accepts "wss://host", "ws://host:port", or just "host" (treated as wss://). */
    private static URI parse(String url) throws IOException {
        String text = url == null ? "" : url.trim();
        if (text.isEmpty()) {
            throw new IOException("Please enter the server URL.");
        }
        if (!text.contains("://")) {
            text = "wss://" + text;
        }
        if (text.startsWith("https://")) {
            text = "wss://" + text.substring(8);
        } else if (text.startsWith("http://")) {
            text = "ws://" + text.substring(7);
        }
        try {
            URI uri = URI.create(text);
            if (uri.getPath() == null || uri.getPath().isEmpty()) {
                uri = URI.create(text + "/");
            }
            if (uri.getHost() == null || !(uri.getScheme().equals("ws") || uri.getScheme().equals("wss"))) {
                throw new IOException("The server URL must look like wss://your-app.onrender.com");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid server URL: " + text);
        }
    }

    /** Feeds what the JDK WebSocket receives into WsStreams. */
    private static final class Receiver implements WebSocket.Listener {
        private final WsStreams streams;

        Receiver(WsStreams streams) {
            this.streams = streams;
        }

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            streams.onData(bytes);   // a message may arrive in pieces; the stream just stitches them together
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            ws.request(1);           // this chat only uses binary frames
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            streams.onClosed();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            streams.onClosed();
        }
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
        streams.close();
    }
}
