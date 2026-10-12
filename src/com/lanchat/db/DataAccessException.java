package com.lanchat.db;

/** Custom checked exception: wraps any SQL problem so upper layers don't deal with SQLException. */
public class DataAccessException extends Exception {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
