package com.bassstarling.receiptmanager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class Database {

    private static final Path DIRECTORY =
            Path.of(System.getProperty("user.home"), "ReceiptManager");

    private static final Path FILE = DIRECTORY.resolve("receipts.db");

    private Database() {
    }

    public static Connection connect() throws Exception {
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + FILE.toAbsolutePath()
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }

        return connection;
    }

    public static void initialize() throws Exception {
        Files.createDirectories(DIRECTORY);

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS receipts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    purchase_date TEXT NOT NULL,
                    store_name TEXT NOT NULL,
                    total_yen INTEGER NOT NULL CHECK(total_yen >= 0),
                    memo TEXT
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS receipt_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    receipt_id INTEGER NOT NULL,
                    item_name TEXT NOT NULL,
                    quantity REAL NOT NULL CHECK(quantity > 0),
                    unit_price_yen INTEGER NOT NULL
                        CHECK(unit_price_yen >= 0),
                    category_id INTEGER,
                    FOREIGN KEY (receipt_id)
                        REFERENCES receipts(id) ON DELETE CASCADE,
                    FOREIGN KEY (category_id)
                        REFERENCES categories(id) ON DELETE SET NULL
                )
                """);

            String[] categories = {
                    "食品", "飲料", "日用品", "衣類",
                    "家電", "交通費", "その他"
            };

            for (String category : categories) {
                try (var insert = connection.prepareStatement(
                        "INSERT OR IGNORE INTO categories(name) VALUES (?)")) {
                    insert.setString(1, category);
                    insert.executeUpdate();
                }
            }
        }
    }
}