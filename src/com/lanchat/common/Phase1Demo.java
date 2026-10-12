package com.lanchat.common;

import java.io.*;

/** Quick check that Phase 1 works: serialize a message and read it back. Delete later. */
public class Phase1Demo {
    public static void main(String[] args) throws Exception {
        User admin = new Admin(1, "reddy", "hash");
        User member = new Member(2, "friend", "hash");
        System.out.println(admin + " canModerate=" + admin.canModerate());
        System.out.println(member + " canModerate=" + member.canModerate());

        Message m = new TextMessage(MessageType.TEXT, "reddy", "general", "Hello LAN!");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(m);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Message copy = (Message) in.readObject();
            System.out.println("After serialization: " + copy.display());
        }

        Response<String> r = Response.ok("Login successful", "token123");
        System.out.println(r.isSuccess() + " - " + r.getMessage() + " - " + r.getData());
    }
}
