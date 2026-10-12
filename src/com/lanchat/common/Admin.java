package com.lanchat.common;

/** Admin user: can kick and ban other users. */
public class Admin extends User {
    private static final long serialVersionUID = 1L;

    public Admin(int id, String username, String passwordHash) {
        super(id, username, passwordHash);
    }

    @Override
    public Role getRole() { return Role.ADMIN; }

    @Override
    public boolean canModerate() { return true; }
}
