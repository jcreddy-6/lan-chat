package com.lanchat.client;

import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.TextMessage;

import java.util.function.Consumer;

/** Turns what the user typed (plain text or a /command) into a packet. Shared by the console and Swing clients. */
public final class CommandParser {

    public static final String HELP = String.join("\n",
            "Commands:",
            "  /msg <user> <text>  private message",
            "  /join <room>        switch to (or create) a room",
            "  /rooms              list rooms",
            "  /who                who is online",
            "  /history [user]     last messages of this room, or your chat with <user>",
            "  /search <text>      search the messages you can see",
            "  /quit               leave",
            "Admin only:",
            "  /users              list every account",
            "  /kick <user>        disconnect a user",
            "  /ban <user>         ban a user",
            "  /unban <user>       lift a ban");

    private CommandParser() { }

    /**
     * @param localOutput where to print usage errors and help (handled on the client, not sent)
     * @return the packet to send, or null if nothing should be sent
     */
    public static Message parse(String username, String line, Consumer<String> localOutput) {
        if (!line.startsWith("/")) {
            return new TextMessage(MessageType.TEXT, username, null, line);
        }
        String[] parts = line.split("\\s+", 3);
        String command = parts[0].toLowerCase();
        switch (command) {
            case "/msg":
                if (parts.length < 3) {
                    localOutput.accept("Usage: /msg <user> <text>");
                    return null;
                }
                return new TextMessage(MessageType.PRIVATE, username, parts[1], parts[2]);
            case "/join":
                if (parts.length < 2) {
                    localOutput.accept("Usage: /join <room>");
                    return null;
                }
                return new TextMessage(MessageType.JOIN, username, parts[1], "");
            case "/rooms":
                return new TextMessage(MessageType.ROOMS, username, null, "");
            case "/who":
                return new TextMessage(MessageType.USER_LIST, username, null, "");
            case "/history":
                return new TextMessage(MessageType.HISTORY, username, parts.length > 1 ? parts[1] : null, "");
            case "/search":
                if (parts.length < 2) {
                    localOutput.accept("Usage: /search <text>");
                    return null;
                }
                return new TextMessage(MessageType.SEARCH, username, null, line.substring(parts[0].length()).trim());
            case "/users":
                return new TextMessage(MessageType.USERS, username, null, "");
            case "/kick":
            case "/ban":
            case "/unban": {
                if (parts.length < 2) {
                    localOutput.accept("Usage: " + command + " <user>");
                    return null;
                }
                MessageType type = command.equals("/kick") ? MessageType.KICK
                        : command.equals("/ban") ? MessageType.BAN : MessageType.UNBAN;
                return new TextMessage(type, username, parts[1], "");
            }
            case "/help":
                localOutput.accept(HELP);
                return null;
            default:
                localOutput.accept("Unknown command. Type /help");
                return null;
        }
    }
}
