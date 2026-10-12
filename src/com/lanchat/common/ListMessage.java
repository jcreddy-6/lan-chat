package com.lanchat.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client packet with a list of strings.
 * type ROOMS: header = the room you are in, items = "#room (count)".
 * type USER_LIST: items = "user [#room]".
 */
public class ListMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final String header;
    private final ArrayList<String> items;

    public ListMessage(MessageType type, String header, List<String> items) {
        super(type, "SERVER", null);
        this.header = header;
        this.items = new ArrayList<>(items);
    }

    public String getHeader() { return header; }

    public List<String> getItems() { return items; }

    @Override
    public String display() {
        if (getType() == MessageType.ROOMS) {
            return "Rooms (you are in #" + header + "): " + String.join(", ", items);
        }
        return "Online (" + items.size() + "): " + String.join(", ", items);
    }
}
