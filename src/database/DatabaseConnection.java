package database;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConnection {

    private static final Properties SETTINGS = new Properties();

    static {
        try (InputStream in = DatabaseConnection.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                SETTINGS.load(in);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Unable to read db.properties: " + e.getMessage());
        }
    }

    private DatabaseConnection() {
    }

    public static Connection getConnection() {
        String url = setting("db.url", "jdbc:mysql://localhost:3306/hospital_db");
        String user = setting("db.user", "root");
        String password = setting("db.password", "");

        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DatabaseException("Could not connect to the database.\n\n1. Start MySQL in XAMPP.\n2. Import sql/hospital_db.sql (new install) or sql/migration_v3.sql (older database) in phpMyAdmin.\n3. Check src/db.properties.\n\nDetails: " + e.getMessage(), e);
        }
    }

    private static String setting(String name, String defaultValue) {
        return System.getProperty(name, SETTINGS.getProperty(name, defaultValue));
    }
}
