package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.InventoryItem;
import util.Permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RoomDAO {

    public static final String CATEGORY = "Room";

    private static final int MAX_BEDS = 500;
    private static final double MAX_PRICE = 99_999_999.99;

    private static final String BASE_SELECT = """
            SELECT item_id, item_name, category, unit_price, unit_cost, stock_quantity,
                   reorder_level, expiry_date, updated_at
            FROM inventory
            WHERE category = 'Room'
            """;

    private RoomDAO() {
    }

    public static List<InventoryItem> findAll() {
        List<InventoryItem> rooms = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + " ORDER BY item_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rooms.add(InventoryDAO.map(rs));
            }
            return rooms;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the rooms.", e);
        }
    }

    public static Map<String, Integer> occupiedBeds() {
        Map<String, Integer> beds = new HashMap<>();
        String sql = "SELECT room, COUNT(*) FROM appointments WHERE status = 'Admitted' AND room IS NOT NULL GROUP BY room";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                beds.put(rs.getString(1), rs.getInt(2));
            }
            return beds;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the occupied rooms.", e);
        }
    }

    public static List<String> freeRoomNames() {
        Map<String, Integer> occupied = occupiedBeds();
        List<String> names = new ArrayList<>();
        for (InventoryItem room : findAll()) {
            if (room.getStockQuantity() > occupied.getOrDefault(room.getItemName(), 0)) {
                names.add(room.getItemName());
            }
        }
        return names;
    }

    public static void create(String name, int beds, double pricePerDay) {
        Permissions.require(Permissions.ROOM_MANAGE);
        checkValues(name, beds, pricePerDay);
        try (Connection con = DatabaseConnection.getConnection()) {
            requireNameFree(con, name, 0);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO inventory (item_name, category, unit_price, unit_cost, stock_quantity, reorder_level) "
                            + "VALUES (?, 'Room', ?, 0, ?, 0)")) {
                ps.setString(1, name.trim());
                ps.setDouble(2, Math.round(pricePerDay * 100) / 100.0);
                ps.setInt(3, beds);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the room.", e);
        }
    }

    public static void update(int roomId, String name, int beds, double pricePerDay) {
        Permissions.require(Permissions.ROOM_MANAGE);
        checkValues(name, beds, pricePerDay);
        try (Connection con = DatabaseConnection.getConnection()) {
            String oldName = nameOf(con, roomId);
            requireNameFree(con, name, roomId);
            if (!oldName.equals(name.trim()) && timesUsed(con, oldName) > 0) {
                throw new IllegalArgumentException("Room " + oldName + " has visits in the history, so it cannot be renamed. Create a new room instead.");
            }
            int occupied = occupiedBeds().getOrDefault(oldName, 0);
            if (beds < occupied) {
                throw new IllegalArgumentException("Room " + oldName + " has " + occupied + " patient(s) in it now, so it needs at least " + occupied + " beds.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE inventory SET item_name = ?, unit_price = ?, stock_quantity = ? WHERE item_id = ? AND category = 'Room'")) {
                ps.setString(1, name.trim());
                ps.setDouble(2, Math.round(pricePerDay * 100) / 100.0);
                ps.setInt(3, beds);
                ps.setInt(4, roomId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not update the room.", e);
        }
    }

    public static void delete(int roomId) {
        Permissions.require(Permissions.ROOM_MANAGE);
        try (Connection con = DatabaseConnection.getConnection()) {
            String name = nameOf(con, roomId);
            if (timesUsed(con, name) > 0) {
                throw new IllegalArgumentException("Room " + name + " has visits in the history and cannot be removed. Change its beds to 0 to close it.");
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM inventory WHERE item_id = ? AND category = 'Room'")) {
                ps.setInt(1, roomId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not remove the room.", e);
        }
    }

    static double priceOf(Connection con, String roomName) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT unit_price FROM inventory WHERE category = 'Room' AND item_name = ?")) {
            ps.setString(1, roomName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    private static void checkValues(String name, int beds, double price) {
        if (name == null || name.isBlank() || name.trim().length() > 30) {
            throw new IllegalArgumentException("Room name is required (at most 30 characters).");
        }
        if (beds < 0 || beds > MAX_BEDS) {
            throw new IllegalArgumentException("Beds must be between 0 and " + MAX_BEDS + ".");
        }
        if (Double.isNaN(price) || price <= 0 || price > MAX_PRICE) {
            throw new IllegalArgumentException("The price per day must be above 0.");
        }
    }

    private static void requireNameFree(Connection con, String name, int ownId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item_id FROM inventory WHERE category = 'Room' AND LOWER(item_name) = LOWER(?) AND item_id <> ?")) {
            ps.setString(1, name.trim());
            ps.setInt(2, ownId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("A room named " + name.trim() + " already exists.");
                }
            }
        }
    }

    private static String nameOf(Connection con, int roomId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item_name FROM inventory WHERE item_id = ? AND category = 'Room'")) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("The room was not found.");
                }
                return rs.getString(1);
            }
        }
    }

    private static int timesUsed(Connection con, String roomName) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM appointments WHERE room = ?")) {
            ps.setString(1, roomName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
