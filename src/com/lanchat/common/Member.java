package com.lanchat.common;

/** Normal chat user. */
public class Member extends User {
    private static final long serialVersionUID = 1L;

    public Member(int id, String username, String passwordHash) {
        super(id, username, passwordHash);
    }

    @Override
    public Role getRole() { return Role.MEMBER; }

    @Override
    public boolean canModerate() { return false; }
}
