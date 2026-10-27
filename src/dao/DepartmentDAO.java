package dao;

import database.DatabaseConnection;
import database.DatabaseException;
import model.Department;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import util.Permissions;

public final class DepartmentDAO {

    private DepartmentDAO() {
    }

    public static List<Department> findAll() {
        String sql = "SELECT * FROM departments ORDER BY department_name";
        List<Department> departments = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                departments.add(map(rs));
            }
            return departments;
        } catch (SQLException e) {
            throw new DatabaseException("Could not load departments.", e);
        }
    }

    public static void create(String name, String description) {
        Permissions.require(Permissions.DEPARTMENT_MANAGE);
        String sql = "INSERT INTO departments (department_name, description) VALUES (?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (DaoSupport.isDuplicateKey(e)) {
                throw new IllegalArgumentException("A department with that name already exists.");
            }
            throw new DatabaseException("Could not save the department.", e);
        }
    }

    public static void update(Department department) {
        Permissions.require(Permissions.DEPARTMENT_MANAGE);
        String sql = "UPDATE departments SET department_name = ?, description = ? WHERE department_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, department.getDepartmentName());
            ps.setString(2, department.getDescription());
            ps.setInt(3, department.getDepartmentId());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (DaoSupport.isDuplicateKey(e)) {
                throw new IllegalArgumentException("A department with that name already exists.");
            }
            throw new DatabaseException("Could not update the department.", e);
        }
    }

    public static void delete(int departmentId) {
        Permissions.require(Permissions.DEPARTMENT_MANAGE);
        String sql = "DELETE FROM departments WHERE department_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, departmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Could not delete the department.", e);
        }
    }

    private static Department map(ResultSet rs) throws SQLException {
        return new Department(
                rs.getInt("department_id"),
                rs.getString("department_name"),
                rs.getString("description"));
    }
}
