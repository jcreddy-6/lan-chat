package com.lanchat.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Makes a WebSocket look like an InputStream and an OutputStream, so the existing
 * ObjectInputStream / ObjectOutputStream code keeps working unchanged.
 *
 * - Incoming binary frames are queued; the InputStream reads them back as one continuous byte stream.
 * - Writes are buffered; flush() sends everything buffered as ONE binary frame.
 *
 * The server and the client both use this class. Each side supplies a Transport that knows how to
 * send a frame and close the socket with its own WebSocket library.
 */
public class WsStreams {

    /**
     * Class filter for ObjectInputStream (see ObjectInputFilter.Config.createFilter).
     * A public endpoint must never deserialize arbitrary classes, so only the chat's own message
     * classes and basic JDK types are accepted; everything else is rejected.
     */
    public static final String ALLOWED_CLASSES =
            "maxdepth=30;maxarray=8388608;com.lanchat.common.*;java.lang.*;java.util.*;java.time.*;!*";

    /** How a side actually talks to its WebSocket. */
    public interface Transport {
        void sendBinary(byte[] data) throws IOException;

        void close();
    }

    private static final byte[] EMPTY = new byte[0];
    private static final byte[] END = new byte[0];   // compared by identity: marks "connection closed"

    private final LinkedBlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
    private final InputStream in = new FrameInput();
    private final OutputStream out = new FrameOutput();
    private final String remoteAddress;
    private volatile Transport transport;
    private volatile boolean closed;

    public WsStreams(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    /** Called once the WebSocket exists (the client only gets it after the connection is open). */
    public void attach(Transport transport) {
        this.transport = transport;
    }

    public InputStream getInputStream() {
        return in;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    /** The WebSocket received some bytes (a whole frame, or a piece of one). */
    public void onData(byte[] data) {
        if (!closed && data.length > 0) {
            frames.add(data);
        }
    }

    /** The WebSocket was closed by the other side or failed: readers now see end-of-stream. */
    public void onClosed() {
        if (!closed) {
            closed = true;
            frames.add(END);
        }
    }

    /** We want to hang up: end the streams and close the WebSocket. */
    public void close() {
        onClosed();
        Transport t = transport;
        if (t != null) {
            t.close();
        }
    }

    // ------------------------------------------------------------------ input

    private final class FrameInput extends InputStream {
        private byte[] current = EMPTY;
        private int pos;
        private boolean ended;

        /** Makes sure there are unread bytes in 'current'. @return false at end of stream */
        private boolean fill() throws IOException {
            while (pos >= current.length) {
                if (ended) {
                    return false;
                }
                try {
                    byte[] next = frames.take();
                    if (next == END) {
                        ended = true;
                        return false;
                    }
                    current = next;
                    pos = 0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted while waiting for data");
                }
            }
            return true;
        }

        @Override
        public int read() throws IOException {
            if (!fill()) {
                return -1;
            }
            return current[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (!fill()) {
                return -1;
            }
            int n = Math.min(len, current.length - pos);
            System.arraycopy(current, pos, b, off, n);
            pos += n;
            return n;
        }

        @Override
        public int available() {
            return current.length - pos;
        }

        @Override
        public void close() {
            WsStreams.this.close();
        }
    }

    // ----------------------------------------------------------------- output

    private final class FrameOutput extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public synchronized void write(int b) {
            buffer.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            buffer.write(b, off, len);
        }

        /** Sends everything buffered so far as one binary frame. */
        @Override
        public synchronized void flush() throws IOException {
            if (buffer.size() == 0) {
                return;
            }
            Transport t = transport;
            if (closed || t == null) {
                buffer.reset();
                throw new IOException("WebSocket is closed");
            }
            byte[] frame = buffer.toByteArray();
            buffer.reset();
            t.sendBinary(frame);
        }

        @Override
        public void close() {
            WsStreams.this.close();
        }
    }
}
