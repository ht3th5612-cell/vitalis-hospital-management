package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Message;
import model.User;
import util.Session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MessageDAO {

    public static final int MAX_LENGTH = 1000;
    public static final int MAX_FILE_BYTES = 25 * 1024 * 1024;
    private static final int CONVERSATION_LIMIT = 200;
    private static final String LAST_PREVIEW = "CASE m.kind WHEN 'text' THEN m.body WHEN 'image' THEN '[Photo]' ELSE '[Video]' END";

    public record Contact(int userId, String name, String role, String department, String lastMessage,
                          LocalDateTime lastAt, int unread) {
    }

    private MessageDAO() {
    }

    public static List<Contact> findContacts() {
        int me = requireSignedIn().getUserId();
        String sql = """
                SELECT u.user_id, u.full_name, u.role, d.department_name,
                       (SELECT %s FROM messages m
                        WHERE (m.sender_id = ? AND m.receiver_id = u.user_id) OR (m.sender_id = u.user_id AND m.receiver_id = ?)
                        ORDER BY m.message_id DESC LIMIT 1) AS last_body,
                       (SELECT m.sent_at FROM messages m
                        WHERE (m.sender_id = ? AND m.receiver_id = u.user_id) OR (m.sender_id = u.user_id AND m.receiver_id = ?)
                        ORDER BY m.message_id DESC LIMIT 1) AS last_at,
                       (SELECT COUNT(*) FROM messages m
                        WHERE m.sender_id = u.user_id AND m.receiver_id = ? AND m.read_at IS NULL) AS unread
                FROM users u
                LEFT JOIN departments d ON d.department_id = u.department_id
                WHERE u.active = 1 AND u.user_id <> ?
                ORDER BY last_at IS NULL, last_at DESC, u.full_name
                """.formatted(LAST_PREVIEW);
        List<Contact> contacts = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= 6; i++) {
                ps.setInt(i, me);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contacts.add(new Contact(rs.getInt("user_id"), rs.getString("full_name"), rs.getString("role"),
                            rs.getString("department_name"), rs.getString("last_body"),
                            DaoSupport.getLocalDateTime(rs, "last_at"), rs.getInt("unread")));
                }
            }
            return contacts;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the staff list.", e);
        }
    }

    private static final String MESSAGE_COLUMNS =
            "message_id, sender_id, receiver_id, body, kind, file_name, file_size, pinned, sent_at, read_at";

    public static List<Message> findConversation(int otherUserId) {
        int me = requireSignedIn().getUserId();
        String sql = """
                SELECT * FROM (
                    SELECT %s
                    FROM messages
                    WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)
                    ORDER BY message_id DESC LIMIT %d
                ) newest ORDER BY message_id
                """.formatted(MESSAGE_COLUMNS, CONVERSATION_LIMIT);
        return query(sql, "Could not load the conversation.", me, otherUserId, otherUserId, me);
    }

    public static List<Message> findPinned(int otherUserId) {
        int me = requireSignedIn().getUserId();
        String sql = """
                SELECT %s
                FROM messages
                WHERE pinned = 1 AND ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?))
                ORDER BY message_id
                """.formatted(MESSAGE_COLUMNS);
        return query(sql, "Could not load the pinned messages.", me, otherUserId, otherUserId, me);
    }

    private static List<Message> query(String sql, String failure, int... ids) {
        List<Message> messages = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                ps.setInt(i + 1, ids[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(new Message(rs.getInt("message_id"), rs.getInt("sender_id"), rs.getInt("receiver_id"),
                            rs.getString("body"), rs.getString("kind"), rs.getString("file_name"),
                            rs.getLong("file_size"), rs.getBoolean("pinned"), DaoSupport.getLocalDateTime(rs, "sent_at"),
                            DaoSupport.getLocalDateTime(rs, "read_at")));
                }
            }
            return messages;
        } catch (SQLException e) {
            throw new DatabaseException(failure, e);
        }
    }

    public static void send(int receiverId, String body) {
        int me = requireSignedIn().getUserId();
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Write a message first.");
        }
        checkLength(text);
        try (Connection con = DatabaseConnection.getConnection()) {
            checkReceiver(con, me, receiverId);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO messages (sender_id, receiver_id, body) VALUES (?, ?, ?)")) {
                ps.setInt(1, me);
                ps.setInt(2, receiverId);
                ps.setString(3, text);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not send the message.", e);
        }
    }

    public static void sendFile(int receiverId, String kind, String fileName, byte[] content, String caption) {
        int me = requireSignedIn().getUserId();
        if (!List.of(Message.IMAGE, Message.VIDEO).contains(kind)) {
            throw new IllegalArgumentException("Choose a photo or a video.");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("The file is empty.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("The file is too big (at most " + MAX_FILE_BYTES / (1024 * 1024) + " MB).");
        }
        String text = caption == null ? "" : caption.trim();
        checkLength(text);
        String name = fileName == null || fileName.isBlank() ? kind : fileName.trim();
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            checkReceiver(con, me, receiverId);
            String key = UUID.randomUUID().toString();
            Path stored = folder().resolve(key);
            try {
                Files.createDirectories(folder());
                Files.write(stored, content);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO messages (sender_id, receiver_id, body, kind, file_name, file_key, file_size) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, me);
                    ps.setInt(2, receiverId);
                    ps.setString(3, text);
                    ps.setString(4, kind);
                    ps.setString(5, name);
                    ps.setString(6, key);
                    ps.setInt(7, content.length);
                    ps.executeUpdate();
                }
            } catch (SQLException | IOException | RuntimeException e) {
                Files.deleteIfExists(stored);
                throw e;
            }
        } catch (SQLException | IOException e) {
            throw new DatabaseException("Could not send the file.", e);
        }
    }

    public static byte[] findFile(int messageId) {
        int me = requireSignedIn().getUserId();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT file_key FROM messages WHERE message_id = ? AND (sender_id = ? OR receiver_id = ?)")) {
            ps.setInt(1, messageId);
            ps.setInt(2, me);
            ps.setInt(3, me);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SecurityException("You can only open files of your own conversations.");
                }
                String key = rs.getString(1);
                if (key == null) {
                    throw new IllegalArgumentException("This message has no file.");
                }
                Path stored = folder().resolve(key);
                if (!Files.isRegularFile(stored)) {
                    throw new IllegalStateException("The file is not on this computer any more.");
                }
                return Files.readAllBytes(stored);
            }
        } catch (SQLException | IOException e) {
            throw new DatabaseException("Could not load the file.", e);
        }
    }

    public static void setPinned(int messageId, boolean pinned) {
        int me = requireSignedIn().getUserId();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE messages SET pinned = ? WHERE message_id = ? AND (sender_id = ? OR receiver_id = ?)")) {
            ps.setBoolean(1, pinned);
            ps.setInt(2, messageId);
            ps.setInt(3, me);
            ps.setInt(4, me);
            if (ps.executeUpdate() == 0) {
                throw new SecurityException("You can only pin messages of your own conversations.");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not pin the message.", e);
        }
    }

    public static Path folder() {
        return Path.of(System.getProperty("chat.folder", "chat_files"));
    }

    private static void checkLength(String text) {
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The message is too long (at most " + MAX_LENGTH + " characters).");
        }
    }

    private static void checkReceiver(Connection con, int me, int receiverId) throws SQLException {
        if (receiverId == me) {
            throw new IllegalArgumentException("Choose another staff member.");
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT active FROM users WHERE user_id = ?")) {
            ps.setInt(1, receiverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    throw new IllegalArgumentException("This staff member is not active, so the message cannot be sent.");
                }
            }
        }
    }

    public static void markRead(int fromUserId) {
        int me = requireSignedIn().getUserId();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE messages SET read_at = NOW() WHERE receiver_id = ? AND sender_id = ? AND read_at IS NULL")) {
            ps.setInt(1, me);
            ps.setInt(2, fromUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not mark the messages as read.", e);
        }
    }

    public static int countUnread() {
        int me = requireSignedIn().getUserId();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM messages WHERE receiver_id = ? AND read_at IS NULL")) {
            ps.setInt(1, me);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not count the new messages.", e);
        }
    }

    private static User requireSignedIn() {
        User me = Session.getUser();
        if (me == null) {
            throw new SecurityException("Please sign in first.");
        }
        return me;
    }
}
