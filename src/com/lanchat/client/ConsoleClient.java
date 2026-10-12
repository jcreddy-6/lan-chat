package com.lanchat.client;

import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.TextMessage;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

/**
 * Temporary text client used to test the server before the Swing UI exists (Phase 6).
 * Usage: ConsoleClient [host] [port]
 */
public class ConsoleClient {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        Scanner keyboard = new Scanner(System.in);

        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            String username = authenticate(keyboard, out, in);
            if (username == null) {
                return;
            }

            Thread reader = new Thread(() -> readLoop(in), "reader");
            reader.setDaemon(true);
            reader.start();

            while (keyboard.hasNextLine()) {
                String line = keyboard.nextLine().trim();
                if (line.equalsIgnoreCase("/quit")) {
                    out.writeObject(new TextMessage(MessageType.LOGOUT, username, null, ""));
                    out.flush();
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                Message message = CommandParser.parse(username, line, System.out::println);
                if (message != null) {
                    out.writeObject(message);
                    out.flush();
                    out.reset();
                }
            }
        } catch (EOFException e) {
            System.out.println("The server closed the connection.");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Connection problem: " + e.getMessage());
        }
    }

    /** Asks for login or register until login succeeds. @return the username, or null if the user exits */
    private static String authenticate(Scanner keyboard, ObjectOutputStream out, ObjectInputStream in)
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
            out.writeObject(new TextMessage(type, user, null, pass));
            out.flush();
            out.reset();

            Message reply = (Message) in.readObject();
            System.out.println(reply.display());
            if (type == MessageType.LOGIN && reply.getType() == MessageType.OK) {
                return user;
            }
        }
    }

    private static void readLoop(ObjectInputStream in) {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof Message) {
                    System.out.println(((Message) obj).display());
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("Disconnected from server.");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Read error: " + e.getMessage());
        }
    }
}
