package com.lanchat.common;

/** Every kind of packet that can travel between client and server. */
public enum MessageType {
    REGISTER, LOGIN, LOGOUT,
    TEXT, PRIVATE, FILE,
    USER_LIST, HISTORY, SEARCH, JOIN, ROOMS,
    KICK, BAN, UNBAN, USERS,
    OK, ERROR
}
