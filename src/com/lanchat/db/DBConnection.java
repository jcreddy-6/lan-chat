package com.lanchat.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Singleton that knows where the database is and creates the tables.
 * Every DAO call opens its own short-lived connection (safe with many server threads).
 */
public class DBConnection {

    private static final DBConnection INSTANCE = new DBConnection();

    private final String url = "jdbc:sqlite:lanchat.db";   // file is created in the folder you run from

    private DBConnection() { }

    public static DBConnection getInstance() {
        return INSTANCE;
    }

    public Connection getConnection() throws DataAccessException {
        try {
            Connection c = DriverManager.getConnection(url);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA busy_timeout = 5000");   // wait instead of failing if DB is busy
            }
            return c;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open database (is the sqlite-jdbc jar in lib/?)", e);
        }
    }

    /** Call once at startup. Safe to call many times. */
    public void init() throws DataAccessException {
        String users = """
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role          TEXT NOT NULL,
                    banned        INTEGER NOT NULL DEFAULT 0
                )""";
        String messages = """
                CREATE TABLE IF NOT EXISTS messages (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    sender   TEXT NOT NULL,
                    receiver TEXT,
                    type     TEXT NOT NULL,
                    content  TEXT,
                    sent_at  TEXT NOT NULL
                )""";
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(users);
            st.execute(messages);
        } catch (SQLException e) {
            throw new DataAccessException("Could not create tables", e);
        }
    }
}
