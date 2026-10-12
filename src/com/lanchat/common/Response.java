package com.lanchat.common;

import java.io.Serializable;

/** Generic server reply. Demonstrates generics. */
public class Response<T extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final T data;

    private Response(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T extends Serializable> Response<T> ok(String message, T data) {
        return new Response<>(true, message, data);
    }

    public static <T extends Serializable> Response<T> fail(String message) {
        return new Response<>(false, message, null);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
