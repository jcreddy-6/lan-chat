package com.lanchat.client;

import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.TextMessage;

import java.io.IOException;
import java.util.Scanner;

/**
 * Text client used to test the server without the Swing UI.
 * Usage: ConsoleClient [server-url]      e.g. ConsoleClient wss://your-app.onrender.com
 * (default: ws://localhost:5000)
 */
public class ConsoleClient {

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "ws://localhost:5000";
        Scanner keyboard = new Scanner(System.in);
        ChatConnection connection = null;

        try {
            connection = new ChatConnection(url);

            String username = authenticate(keyboard, connection);
            if (username == null) {
                return;
            }

            connection.startListening(new ChatConnection.Listener() {
                @Override
                public void onMessage(Message message) {
                    System.out.println(message.display());
                }

                @Override
                public void onDisconnect() {
                    System.out.println("Disconnected from server.");
                }
            });

            while (keyboard.hasNextLine()) {
                String line = keyboard.nextLine().trim();
                if (line.equalsIgnoreCase("/quit")) {
                    connection.send(new TextMessage(MessageType.LOGOUT, username, null, ""));
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                Message message = CommandParser.parse(username, line, System.out::println);
                if (message != null) {
                    connection.send(message);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Connection problem: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    /** Asks for login or register until login succeeds. @return the username, or null if the user exits */
    private static String authenticate(Scanner keyboard, ChatConnection connection)
            throws IOException, ClassNotFoundException {
        while (true) {
            System.out.print("1) Login   2) Register   3) Exit > ");
            String choice = keyboard.nextLine().trim();
            if (choice.equals("3")) {
                return null;
            }
            if (!choice.equals("1") && !choice.equals("2")) {
                continue;
            }
            System.out.print("Username: ");
            String user = keyboard.nextLine().trim();
            System.out.print("Password: ");
            String pass = keyboard.nextLine();

            MessageType type = choice.equals("1") ? MessageType.LOGIN : MessageType.REGISTER;
            connection.send(new TextMessage(type, user, null, pass));

            Message reply = connection.readOne();
            System.out.println(reply.display());
            if (type == MessageType.LOGIN && reply.getType() == MessageType.OK) {
                return user;
            }
        }
    }
}
