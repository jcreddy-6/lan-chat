package com.lanchat.common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One stored chat message, as read back from the database (history and search results). */
public record ChatRecord(String sender, String receiver, MessageType type,
                         String content, LocalDateTime sentAt) implements Serializable {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    public String display() {
        String time = sentAt.format(FMT);
        if (type == MessageType.PRIVATE) {
            return "[" + time + "] (private) " + sender + " -> " + receiver + ": " + content;
        }
        String body = (type == MessageType.FILE) ? "sent file: " + content : content;
        return "[" + time + "] " + sender + ": " + body;
    }
}
