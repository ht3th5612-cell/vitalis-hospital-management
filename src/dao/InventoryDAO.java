package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.InventoryItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import util.Permissions;

public final class InventoryDAO {

    private static final String BASE_SELECT = """
            SELECT item_id, item_name, category, unit_price, unit_cost, stock_quantity,
                   reorder_level, expiry_date, updated_at
            FROM inventory
            WHERE category <> 'Room'
            """;

    private InventoryDAO() {
    }

    public static List<InventoryItem> findAll(String searchTerm) {
        String sql = BASE_SELECT
                + " AND (? = '' OR item_name LIKE ? OR category LIKE ?)"
                + " ORDER BY item_name";
        String term = searchTerm == null ? "" : searchTerm.trim();
        List<InventoryItem> items = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, DaoSupport.like(term));
            ps.setString(3, DaoSupport.like(term));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
            return items;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load inventory.", e);
        }
    }

    public static List<InventoryItem> findLowStock() {
        String sql = BASE_SELECT + " AND stock_quantity <= reorder_level ORDER BY stock_quantity";
        List<InventoryItem> items = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(map(rs));
            }
            return items;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load low-stock items.", e);
        }
    }

    public static List<InventoryItem> findExpiringSoon(int days) {
        String sql = BASE_SELECT + " AND expiry_date IS NOT NULL"
                + " AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY) ORDER BY expiry_date";
        List<InventoryItem> items = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
            return items;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the items that expire soon.", e);
        }
    }

    public static void create(InventoryItem item) {
        Permissions.require(Permissions.INVENTORY_EDIT);
        requireNotRoom(item);
        String sql = """
                INSERT INTO inventory
                    (item_name, category, unit_price, stock_quantity, reorder_level, expiry_date, unit_cost)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, item);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the inventory item.", e);
        }
    }

    public static void update(InventoryItem item) {
        Permissions.require(Permissions.INVENTORY_EDIT);
        requireNotRoom(item);
        String sql = """
                UPDATE inventory
                SET item_name = ?, category = ?, unit_price = ?, stock_quantity = ?,
                    reorder_level = ?, expiry_date = ?, unit_cost = ?
                WHERE item_id = ? AND category <> 'Room'
                """;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, item);
            ps.setInt(8, item.getItemId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not update the inventory item.", e);
        }
    }

    public static void delete(int itemId) {
        Permissions.require(Permissions.INVENTORY_EDIT);
        String sql = "DELETE FROM inventory WHERE item_id = ? AND category <> 'Room'";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete the inventory item.", e);
        }
    }

    private static void bind(PreparedStatement ps, InventoryItem item) throws SQLException {
        ps.setString(1, item.getItemName());
        ps.setString(2, item.getCategory());
        ps.setDouble(3, item.getUnitPrice());
        ps.setInt(4, item.getStockQuantity());
        ps.setInt(5, item.getReorderLevel());
        DaoSupport.setNullableDate(ps, 6, item.getExpiryDate());
        ps.setDouble(7, item.getUnitCost());
    }

    private static void requireNotRoom(InventoryItem item) {
        if (RoomDAO.CATEGORY.equalsIgnoreCase(item.getCategory() == null ? "" : item.getCategory().trim())) {
            throw new IllegalArgumentException("The category Room is for hospital rooms. Use the Rooms screen.");
        }
    }

    public static void restock(int itemId, int quantity, Double newUnitCost) {
        Permissions.require(Permissions.INVENTORY_RESTOCK);
        if (quantity < 1 || quantity > 1_000_000) {
            throw new IllegalArgumentException("The quantity to add must be between 1 and 1,000,000.");
        }
        if (newUnitCost != null && (Double.isNaN(newUnitCost) || newUnitCost < 0 || newUnitCost > 99_999_999.99)) {
            throw new IllegalArgumentException("The unit cost is not valid.");
        }
        String sql = "UPDATE inventory SET stock_quantity = stock_quantity + ?, unit_cost = COALESCE(?, unit_cost) "
                + "WHERE item_id = ? AND category <> 'Room'";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            if (newUnitCost == null) {
                ps.setNull(2, java.sql.Types.DECIMAL);
            } else {
                ps.setDouble(2, Math.round(newUnitCost * 100) / 100.0);
            }
            ps.setInt(3, itemId);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException("The inventory item was not found.");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not add the stock.", e);
        }
    }

    static InventoryItem map(ResultSet rs) throws SQLException {
        InventoryItem item = new InventoryItem(
                rs.getInt("item_id"),
                rs.getString("item_name"),
                rs.getString("category"),
                rs.getDouble("unit_price"),
                rs.getInt("stock_quantity"),
                rs.getInt("reorder_level"),
                DaoSupport.getLocalDate(rs, "expiry_date"),
                DaoSupport.getLocalDateTime(rs, "updated_at"));
        item.setUnitCost(rs.getDouble("unit_cost"));
        return item;
    }
}
