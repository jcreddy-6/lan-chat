package com.lanchat.common;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base packet sent between client and server (serialized with ObjectOutputStream).
 * Subclasses add their own payload.
 */
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final String sender;
    private final String receiver;   // username for private chat, room name for group chat, null otherwise
    private final LocalDateTime timestamp = LocalDateTime.now();

    protected Message(MessageType type, String sender, String receiver) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
    }

    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public LocalDateTime getTimestamp() { return timestamp; }

    /** Short text shown in the chat window. */
    public abstract String display();
}
