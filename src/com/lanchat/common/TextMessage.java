package com.lanchat.common;

import java.time.format.DateTimeFormatter;

/** A normal chat message (group or private). Also used for login/register commands. */
public class TextMessage extends Message {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final String content;

    public TextMessage(MessageType type, String sender, String receiver, String content) {
        super(type, sender, receiver);
        this.content = content;
    }

    public String getContent() { return content; }

    @Override
    public String display() {
        String time = getTimestamp().format(FMT);
        if (getType() == MessageType.PRIVATE) {
            return "[" + time + "] (private) " + getSender() + " -> " + getReceiver() + ": " + content;
        }
        return "[" + time + "] " + getSender() + ": " + content;
    }
}
