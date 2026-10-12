package com.lanchat.db;

import com.lanchat.common.ChatRecord;
import com.lanchat.common.MessageType;
import com.lanchat.common.Role;
import com.lanchat.common.TextMessage;
import com.lanchat.common.User;

import java.util.Optional;

/** Quick check for Phase 2. Run it from the project root. Safe to run many times. */
public class Phase2Demo {
    public static void main(String[] args) {
        try {
            DBConnection.getInstance().init();
            UserDAO users = new UserDAO();
            MessageDAO messages = new MessageDAO();

            System.out.println("Register reddy : " + users.register("reddy", "admin123", Role.ADMIN));
            System.out.println("Register friend: " + users.register("friend", "pass456", Role.MEMBER));
            System.out.println("Register friend again (should be false): " + users.register("friend", "x", Role.MEMBER));

            Optional<User> ok = users.authenticate("reddy", "admin123");
            Optional<User> bad = users.authenticate("reddy", "wrong");
            System.out.println("Login correct password: " + ok.map(User::toString).orElse("FAILED"));
            System.out.println("Login wrong password  : " + bad.map(User::toString).orElse("FAILED (expected)"));

            messages.save(new TextMessage(MessageType.TEXT, "reddy", "general", "Hello everyone"));
            messages.save(new TextMessage(MessageType.TEXT, "friend", "general", "Hi reddy, project started"));
            messages.save(new TextMessage(MessageType.PRIVATE, "reddy", "friend", "Phase 2 done"));

            System.out.println("\n--- general room history ---");
            messages.roomHistory("general", 10).stream().map(ChatRecord::display).forEach(System.out::println);

            System.out.println("\n--- private reddy <-> friend ---");
            messages.privateHistory("reddy", "friend", 10).forEach(r -> System.out.println(r.display()));

            System.out.println("\n--- search 'project' ---");
            messages.search("project", "reddy", 10).forEach(r -> System.out.println(r.display()));

            users.setBanned("friend", true);
            System.out.println("\nfriend after ban: " + users.findByUsername("friend").orElseThrow());
            users.setBanned("friend", false);

            System.out.println("\nAll users: " + users.findAll());
        } catch (DataAccessException e) {
            System.err.println("DB error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
