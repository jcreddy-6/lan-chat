package com.lanchat.db;

import com.lanchat.common.ChatRecord;
import com.lanchat.common.FileMessage;
import com.lanchat.common.Message;
import com.lanchat.common.MessageType;
import com.lanchat.common.TextMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Saves chat messages and reads history / search results. File contents are NOT stored, only the file name. */
public class MessageDAO {

    private final DBConnection db = DBConnection.getInstance();

    public void save(Message m) throws DataAccessException {
        String content;
        if (m instanceof TextMessage t) {
            content = t.getContent();
        } else if (m instanceof FileMessage f) {
            content = f.getFileName();
        } else {
            content = m.display();
        }
        String sql = "INSERT INTO messages (sender, receiver, type, content, sent_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, m.getSender());
            ps.setString(2, m.getReceiver());
            ps.setString(3, m.getType().name());
            ps.setString(4, content);
            ps.setString(5, m.getTimestamp().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save message", e);
        }
    }

    /** Last {@code limit} messages of a group room, oldest first. */
    public List<ChatRecord> roomHistory(String room, int limit) throws DataAccessException {
        String sql = "SELECT sender, receiver, type, content, sent_at FROM messages "
                + "WHERE type IN ('TEXT', 'FILE') AND receiver = ? ORDER BY id DESC LIMIT ?";
        return query(sql, room, limit);
    }

    /** Last {@code limit} private messages between two users, oldest first. */
    public List<ChatRecord> privateHistory(String userA, String userB, int limit) throws DataAccessException {
        String sql = "SELECT sender, receiver, type, content, sent_at FROM messages "
                + "WHERE type IN ('PRIVATE', 'FILE') "
                + "AND ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) "
                + "ORDER BY id DESC LIMIT ?";
        return query(sql, userA, userB, userB, userA, limit);
    }

    /** Search text in group messages plus any private messages this user sent or received. */
    public List<ChatRecord> search(String keyword, String username, int limit) throws DataAccessException {
        String sql = "SELECT sender, receiver, type, content, sent_at FROM messages "
                + "WHERE content LIKE ? AND (type = 'TEXT' OR sender = ? OR receiver = ?) "
                + "ORDER BY id DESC LIMIT ?";
        return query(sql, "%" + keyword + "%", username, username, limit);
    }

    private List<ChatRecord> query(String sql, Object... params) throws DataAccessException {
        List<ChatRecord> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ChatRecord(
                            rs.getString("sender"),
                            rs.getString("receiver"),
                            MessageType.valueOf(rs.getString("type")),
                            rs.getString("content"),
                            LocalDateTime.parse(rs.getString("sent_at"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read messages", e);
        }
        Collections.reverse(list);   // queries fetch newest first; show oldest first
        return list;
    }
}
