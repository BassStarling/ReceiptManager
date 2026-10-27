package com.bassstarling.receiptmanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReceiptRepository {

    // レシートを登録
    public void add(
            LocalDate date,
            String storeName,
            long totalYen
    ) throws Exception {

        String sql = """
            INSERT INTO receipts
                (purchase_date, store_name, total_yen)
            VALUES (?, ?, ?)
            """;

        try (var connection = Database.connect();
             var statement = connection.prepareStatement(sql)) {

            statement.setString(1, date.toString());
            statement.setString(2, storeName);
            statement.setLong(3, totalYen);
            statement.executeUpdate();
        }
    }

    // レシートをすべて取得
    public List<Receipt> findAll() throws Exception {
        List<Receipt> receipts = new ArrayList<>();

        String sql = """
            SELECT id, purchase_date, store_name, total_yen
            FROM receipts
            ORDER BY purchase_date DESC, id DESC
            """;

        try (var connection = Database.connect();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {

            while (result.next()) {
                receipts.add(new Receipt(
                        result.getInt("id"),
                        LocalDate.parse(
                                result.getString("purchase_date")),
                        result.getString("store_name"),
                        result.getLong("total_yen")
                ));
            }
        }

        return receipts;
    }

    // レシートを削除
    public void delete(int id) throws Exception {
        String sql = "DELETE FROM receipts WHERE id = ?";

        try (var connection = Database.connect();
             var statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    public void updateTotalFromItems(int receiptId) {
        String sql = """
        UPDATE receipts
        SET total_yen = (
            SELECT COALESCE(
                SUM(CAST(ROUND(quantity * unit_price_yen) AS INTEGER)),
                0
            )
            FROM receipt_items
            WHERE receipt_id = ?
        )
        WHERE id = ?
        """;

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, receiptId);
            ps.setInt(2, receiptId);
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("レシート合計金額の更新に失敗しました", e);
        }
    }

    // カテゴリ名を取得
    public List<String> findAllCategories() throws Exception {
        List<String> categories = new ArrayList<>();

        String sql = "SELECT name FROM categories ORDER BY id";

        try (var connection = Database.connect();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {

            while (result.next()) {
                categories.add(result.getString("name"));
            }
        }

        return categories;
    }

    // 商品明細を登録
    public void addItem(
            int receiptId,
            String itemName,
            double quantity,
            long unitPriceYen,
            String categoryName
    ) throws Exception {

        String sql = """
            INSERT INTO receipt_items
                (receipt_id, item_name, quantity,
                 unit_price_yen, category_id)
            VALUES (
                ?, ?, ?, ?,
                (SELECT id FROM categories WHERE name = ?)
            )
            """;

        try (var connection = Database.connect();
             var statement = connection.prepareStatement(sql)) {

            statement.setInt(1, receiptId);
            statement.setString(2, itemName);
            statement.setDouble(3, quantity);
            statement.setLong(4, unitPriceYen);
            statement.setString(5, categoryName);

            statement.executeUpdate();
        }
    }

    // 指定されたレシートの商品明細を取得
    public List<ReceiptItem> findItemsByReceiptId(
            int receiptId
    ) throws Exception {

        List<ReceiptItem> items = new ArrayList<>();

        String sql = """
            SELECT
                ri.id,
                ri.receipt_id,
                ri.item_name,
                ri.quantity,
                ri.unit_price_yen,
                c.name AS category_name
            FROM receipt_items ri
            LEFT JOIN categories c
                ON ri.category_id = c.id
            WHERE ri.receipt_id = ?
            ORDER BY ri.id
            """;

        try (var connection = Database.connect();
             var statement = connection.prepareStatement(sql)) {

            statement.setInt(1, receiptId);

            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    items.add(new ReceiptItem(
                            result.getInt("id"),
                            result.getInt("receipt_id"),
                            result.getString("item_name"),
                            result.getDouble("quantity"),
                            result.getLong("unit_price_yen"),
                            result.getString("category_name")
                    ));
                }
            }
        }

        return items;
    }

    // 商品明細を削除
    public void deleteItem(int itemId) throws Exception {
        String sql = "DELETE FROM receipt_items WHERE id = ?";

        try (var connection = Database.connect();
             var statement = connection.prepareStatement(sql)) {

            statement.setInt(1, itemId);
            statement.executeUpdate();
        }
    }


    public void addCategory(String name) {
        String categoryName = name.trim();

        if (categoryName.isEmpty()) {
            throw new IllegalArgumentException("カテゴリ名を入力してください。");
        }

        String sql = "INSERT INTO categories (name) VALUES (?)";

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, categoryName);
            ps.executeUpdate();

        } catch (SQLException e) {
            if (e.getErrorCode() == 19) {
                throw new IllegalArgumentException(
                        "同じ名前のカテゴリが既に存在します。", e
                );
            }

            throw new RuntimeException("カテゴリの登録に失敗しました。", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteCategory(String name) {
        String categoryName = name.trim();

        if (categoryName.isEmpty()) {
            throw new IllegalArgumentException(
                    "削除するカテゴリを選択してください。"
            );
        }

        try (Connection conn = Database.connect()) {

            // このカテゴリを使用している商品があるか確認
            String checkSql = """
            SELECT COUNT(*)
            FROM receipt_items ri
            JOIN categories c ON ri.category_id = c.id
            WHERE c.name = ?
            """;

            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, categoryName);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new IllegalArgumentException(
                                "このカテゴリは商品に使用されているため削除できません。"
                        );
                    }
                }
            }

            // 使用されていないカテゴリを削除
            String deleteSql = "DELETE FROM categories WHERE name = ?";

            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, categoryName);

                int deleted = ps.executeUpdate();

                if (deleted == 0) {
                    throw new IllegalArgumentException(
                            "削除するカテゴリが見つかりません。"
                    );
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "カテゴリの削除に失敗しました。", e
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void renameCategory(String oldName, String newName) {
        String oldCategoryName = oldName == null ? "" : oldName.trim();
        String newCategoryName = newName == null ? "" : newName.trim();

        if (oldCategoryName.isEmpty()) {
            throw new IllegalArgumentException(
                    "変更するカテゴリを選択してください。"
            );
        }

        if (newCategoryName.isEmpty()) {
            throw new IllegalArgumentException(
                    "新しいカテゴリ名を入力してください。"
            );
        }

        String sql = "UPDATE categories SET name = ? WHERE name = ?";

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newCategoryName);
            ps.setString(2, oldCategoryName);

            int updated = ps.executeUpdate();

            if (updated == 0) {
                throw new IllegalArgumentException(
                        "変更対象のカテゴリが見つかりません。"
                );
            }

        } catch (SQLException e) {
            if (e.getErrorCode() == 19) {
                throw new IllegalArgumentException(
                        "同じ名前のカテゴリが既に存在します。", e
                );
            }

            throw new RuntimeException(
                    "カテゴリ名の変更に失敗しました。", e
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void updateReceipt(
            int id,
            LocalDate purchaseDate,
            String storeName
    ) {
        if (purchaseDate == null) {
            throw new IllegalArgumentException(
                    "購入日を入力してください。"
            );
        }

        String name = storeName == null ? "" : storeName.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "店名を入力してください。"
            );
        }

        String sql = """
        UPDATE receipts
        SET purchase_date = ?, store_name = ?
        WHERE id = ?
        """;

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, purchaseDate.toString());
            ps.setString(2, name);
            ps.setInt(3, id);

            int updated = ps.executeUpdate();

            if (updated == 0) {
                throw new IllegalArgumentException(
                        "編集対象のレシートが見つかりません。"
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "レシートの更新に失敗しました。", e
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void updateItem(
            int itemId,
            String itemName,
            double quantity,
            int unitPriceYen,
            String categoryName
    ) {
        String name = itemName == null ? "" : itemName.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "商品名を入力してください。"
            );
        }

        if (!Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException(
                    "数量は0より大きい数値を入力してください。"
            );
        }

        if (unitPriceYen < 0) {
            throw new IllegalArgumentException(
                    "単価は0以上で入力してください。"
            );
        }

        String sql = """
        UPDATE receipt_items
        SET item_name = ?,
            quantity = ?,
            unit_price_yen = ?,
            category_id = (
                SELECT id FROM categories WHERE name = ?
            )
        WHERE id = ?
        """;

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setDouble(2, quantity);
            ps.setInt(3, unitPriceYen);
            ps.setString(4, categoryName);
            ps.setInt(5, itemId);

            int updated = ps.executeUpdate();

            if (updated == 0) {
                throw new IllegalArgumentException(
                        "編集対象の商品が見つからないか、カテゴリが存在しません。"
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "商品明細の更新に失敗しました。", e
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}