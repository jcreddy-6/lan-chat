package com.lanchat.common;

import java.io.Serializable;

/** Base class for all users. Demonstrates encapsulation and abstraction. */
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String username;
    private transient String passwordHash; // never sent over the network
    private Status status = Status.OFFLINE;

    protected User(int id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public abstract Role getRole();

    /** Polymorphism: each subclass decides what it is allowed to do. */
    public abstract boolean canModerate();

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        return username + " (" + getRole() + ", " + status + ")";
    }
}
