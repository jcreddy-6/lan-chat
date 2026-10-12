package com.lanchat.db;

import com.lanchat.common.Admin;
import com.lanchat.common.Member;
import com.lanchat.common.Role;
import com.lanchat.common.Status;
import com.lanchat.common.User;
import com.lanchat.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All database operations for users. */
public class UserDAO {

    private static final String COLUMNS = "id, username, password_hash, role, banned";
    private final DBConnection db = DBConnection.getInstance();

    /** @return false if the username is already taken */
    public boolean register(String username, String password, Role role) throws DataAccessException {
        if (findByUsername(username).isPresent()) {
            return false;
        }
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(password));
            ps.setString(3, role.name());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Could not register " + username, e);
        }
    }

    public Optional<User> findByUsername(String username) throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE username = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not look up " + username, e);
        }
    }

    /** Empty if the user does not exist or the password is wrong. Check getStatus() == BANNED afterwards. */
    public Optional<User> authenticate(String username, String password) throws DataAccessException {
        return findByUsername(username)
                .filter(u -> PasswordUtil.verify(password, u.getPasswordHash()));
    }

    public boolean setBanned(String username, boolean banned) throws DataAccessException {
        String sql = "UPDATE users SET banned = ? WHERE username = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, banned ? 1 : 0);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update ban for " + username, e);
        }
    }

    public List<User> findAll() throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM users ORDER BY username";
        List<User> users = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new DataAccessException("Could not list users", e);
        }
    }

    /** Factory-style helper: builds the right subclass from the role column. */
    private User mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("username");
        String hash = rs.getString("password_hash");
        Role role = Role.valueOf(rs.getString("role"));
        User user = switch (role) {
            case ADMIN -> new Admin(id, name, hash);
            case MEMBER -> new Member(id, name, hash);
        };
        if (rs.getInt("banned") == 1) {
            user.setStatus(Status.BANNED);
        }
        return user;
    }
}
