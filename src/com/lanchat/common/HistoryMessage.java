package com.lanchat.common;

import java.util.ArrayList;
import java.util.List;

/** Server -> client packet carrying a list of stored messages (room or private history). */
public class HistoryMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final String title;
    private final ArrayList<ChatRecord> records;

    public HistoryMessage(String title, String receiver, List<ChatRecord> records) {
        super(MessageType.HISTORY, "SERVER", receiver);
        this.title = title;
        this.records = new ArrayList<>(records);
    }

    public String getTitle() { return title; }

    public List<ChatRecord> getRecords() { return records; }

    @Override
    public String display() {
        StringBuilder sb = new StringBuilder("--- " + title + " ---");
        if (records.isEmpty()) {
            sb.append("\n(no messages yet)");
        } else {
            records.forEach(r -> sb.append('\n').append(r.display()));
        }
        sb.append("\n--- end of history ---");
        return sb.toString();
    }
}
