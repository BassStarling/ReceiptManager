package com.bassstarling.receiptmanager;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class ReceiptApp extends Application {

    private final ReceiptRepository repository =
            new ReceiptRepository();

    private final ObservableList<Receipt> receipts =
            FXCollections.observableArrayList();

    private final ObservableList<ReceiptItem> items =
            FXCollections.observableArrayList();

    // レシート入力欄
    private final DatePicker datePicker =
            new DatePicker(LocalDate.now());

    private final TextField storeField = new TextField();

    // 商品入力欄
    private final TextField itemNameField = new TextField();
    private final TextField quantityField = new TextField();
    private final TextField unitPriceField = new TextField();
    private final TextField categoryNameField = new TextField();

    private final ComboBox<String> categoryBox =
            new ComboBox<>();

    // 2つの一覧表
    private final TableView<Receipt> receiptTable =
            new TableView<>();

    private final TableView<ReceiptItem> itemTable =
            new TableView<>();

    private final Label detailTitle =
            new Label("商品明細（レシートを選択してください）");

    private final Label statusLabel = new Label();

    private final NumberFormat yenFormat =
            NumberFormat.getNumberInstance(Locale.JAPAN);

    @Override
    public void start(Stage stage) {

        try {
            Database.initialize();
        } catch (Exception e) {
            showError("データベースを初期化できませんでした。", e);
            return;
        }

        // -------------------------
        // レシート登録フォーム
        // -------------------------

        storeField.setPromptText("店名");
        storeField.setPrefWidth(150);

        Button addReceiptButton =
                new Button("レシートを登録");

        addReceiptButton.setOnAction(
                event -> addReceipt());

        Button editReceiptButton = new Button("選択レシートを編集");
        editReceiptButton.setOnAction(
                event -> editReceipt()
        );

        HBox receiptForm = new HBox(
                8,
                new Label("購入日"),
                datePicker,
                storeField,
                addReceiptButton,
                editReceiptButton
        );
        receiptForm.setPadding(new Insets(5));

        receiptForm.setPadding(new Insets(5));

        categoryNameField.setPromptText("新しいカテゴリ名");
        categoryNameField.setPrefWidth(160);

        Button addCategoryButton = new Button("カテゴリを追加");
        addCategoryButton.setOnAction(event -> addCategory());

        Button deleteCategoryButton = new Button("選択カテゴリを削除");
        deleteCategoryButton.setOnAction(
                event -> deleteCategory()
        );

        Button renameCategoryButton = new Button("カテゴリ名を変更");
        renameCategoryButton.setOnAction(
                event -> renameCategory()
        );

        HBox categoryForm = new HBox(
                8,
                categoryNameField,
                addCategoryButton,
                renameCategoryButton,
                deleteCategoryButton
        );
        categoryForm.setPadding(new Insets(5));

        // -------------------------
        // レシート一覧表
        // -------------------------

        TableColumn<Receipt, String> dateColumn =
                new TableColumn<>("購入日");

        dateColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue()
                                .getPurchaseDate().toString()
                ));

        TableColumn<Receipt, String> storeColumn =
                new TableColumn<>("店名");

        storeColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getStoreName()
                ));

        TableColumn<Receipt, String> totalColumn =
                new TableColumn<>("合計金額");

        totalColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        yenFormat.format(
                                cell.getValue().getTotalYen()
                        ) + " 円"
                ));

        receiptTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldReceipt, newReceipt) -> {
                    if (newReceipt != null) {
                        datePicker.setValue(newReceipt.getPurchaseDate());
                        storeField.setText(newReceipt.getStoreName());
                    }
                });


        receiptTable.getColumns().addAll(
                dateColumn,
                storeColumn,
                totalColumn
        );

        receiptTable.setItems(receipts);

        receiptTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );

        receiptTable.setPrefHeight(200);

        // 選択されたレシートに応じて商品一覧を更新
        receiptTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldReceipt, newReceipt) ->
                        refreshItemsForSelectedReceipt()
                );

        Button deleteReceiptButton =
                new Button("選択したレシートを削除");

        deleteReceiptButton.setOnAction(
                event -> deleteReceipt());

        // -------------------------
        // 商品明細の入力フォーム
        // -------------------------

        itemNameField.setPromptText("商品名");
        itemNameField.setPrefWidth(180);

        quantityField.setPromptText("数量");
        quantityField.setPrefWidth(80);

        unitPriceField.setPromptText("単価（円）");
        unitPriceField.setPrefWidth(110);

        categoryBox.setPromptText("カテゴリ");
        categoryBox.setPrefWidth(130);

        Button addItemButton = new Button("商品を追加");
        addItemButton.setOnAction(event -> addItem());
        Button editItemButton = new Button("選択商品を編集");
        editItemButton.setOnAction(event -> editItem());

        addItemButton.setOnAction(
                event -> addItem());

        receiptForm.setPadding(new Insets(5));


        // -------------------------
        // 商品明細一覧表
        // -------------------------

        TableColumn<ReceiptItem, String> itemNameColumn =
                new TableColumn<>("商品名");

        itemNameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getItemName()
                ));

        TableColumn<ReceiptItem, String> quantityColumn =
                new TableColumn<>("数量");

        quantityColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(
                                cell.getValue().getQuantity()
                        )
                ));

        TableColumn<ReceiptItem, String> unitPriceColumn =
                new TableColumn<>("単価");

        unitPriceColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        yenFormat.format(
                                cell.getValue().getUnitPriceYen()
                        ) + " 円"
                ));

        TableColumn<ReceiptItem, String> subtotalColumn =
                new TableColumn<>("小計");

        subtotalColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        yenFormat.format(
                                cell.getValue().getSubtotalYen()
                        ) + " 円"
                ));

        TableColumn<ReceiptItem, String> categoryColumn =
                new TableColumn<>("カテゴリ");

        categoryColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getCategoryName() == null
                                ? "未分類"
                                : cell.getValue().getCategoryName()
                ));

        itemTable.getColumns().addAll(
                itemNameColumn,
                quantityColumn,
                unitPriceColumn,
                subtotalColumn,
                categoryColumn
        );

        itemTable.setItems(items);

        itemTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );

        itemTable.setPrefHeight(200);

        Button deleteItemButton =
                new Button("選択した商品を削除");

        deleteItemButton.setOnAction(
                event -> deleteItem());

        // -------------------------
        // 画面全体の配置
        // -------------------------

        Label receiptTitle = new Label("レシート一覧");
        receiptTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        detailTitle.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold;"
        );

        itemNameField.setPromptText("商品名");
        quantityField.setPromptText("数量");
        unitPriceField.setPromptText("単価（円）");

        HBox itemForm = new HBox(
                8,
                itemNameField,
                quantityField,
                unitPriceField,
                categoryBox,
                addItemButton,
                editItemButton
        );
        itemForm.setPadding(new Insets(5));

        itemForm.setPadding(new Insets(5));

        VBox root = new VBox(
                8,
                new Label("レシート管理アプリ"),
                receiptTitle,
                receiptForm,
                receiptTable,
                categoryForm,
                deleteReceiptButton,
                new Separator(),
                detailTitle,
                itemForm,
                itemTable,
                deleteItemButton,
                statusLabel
        );

        root.setPadding(new Insets(12));

        VBox.setVgrow(receiptTable, Priority.ALWAYS);
        VBox.setVgrow(itemTable, Priority.ALWAYS);

        stage.setTitle("レシート管理アプリ");
        stage.setScene(new Scene(root, 1050, 780));
        stage.show();

        refreshReceipts();
        loadCategories();
    }

    // レシートを登録

    private void addReceipt() {
        try {
            LocalDate date = datePicker.getValue();
            String store = storeField.getText().trim();

            if (date == null) {
                showMessage("購入日を入力してください。");
                return;
            }

            if (store.isEmpty()) {
                showMessage("店名を入力してください。");
                return;
            }

            // 商品明細がまだないため、合計金額は0円で登録する
            repository.add(date, store, 0);

            storeField.clear();

            refreshReceipts();

            if (!receipts.isEmpty()) {
                receiptTable.getSelectionModel().select(0);
            }

            showMessage("レシートを登録しました。");

        } catch (Exception e) {
            showError("レシートの登録に失敗しました。", e);
        }
    }


    // レシートを削除
    private void deleteReceipt() {
        Receipt selected =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage("削除するレシートを選択してください。");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "「" + selected.getStoreName()
                        + "」のレシートを削除しますか？",
                ButtonType.YES,
                ButtonType.NO
        );

        confirm.setHeaderText("レシートの削除");

        if (confirm.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }

        try {
            repository.delete(selected.getId());
            refreshReceipts();
            showMessage("レシートを削除しました。");
        } catch (Exception e) {
            showError("削除に失敗しました。", e);
        }
    }

    // レシート一覧を再読み込み
    private void refreshReceipts() {
        try {
            receipts.setAll(repository.findAll());
        } catch (Exception e) {
            showError("レシート一覧を読み込めませんでした。", e);
        }
    }

    // カテゴリを選択欄に読み込む
    private void loadCategories() {
        try {
            List<String> categories =
                    repository.findAllCategories();

            categoryBox.setItems(
                    FXCollections.observableArrayList(categories)
            );

            if (!categories.isEmpty()) {
                categoryBox.getSelectionModel().selectFirst();
            }

        } catch (Exception e) {
            showError("カテゴリを読み込めませんでした。", e);
        }
    }


    private void addCategory() {
        String name = categoryNameField.getText().trim();

        if (name.isEmpty()) {
            showMessage("カテゴリ名を入力してください。");
            return;
        }

        try {
            repository.addCategory(name);

            // カテゴリ一覧を再読み込み
            loadCategories();

            // 入力欄を空にする
            categoryNameField.clear();

            showMessage("カテゴリを追加しました。");

        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());

        } catch (Exception e) {
            showError("カテゴリの追加に失敗しました。", e);
        }
    }


    // 選択中のレシートの商品明細を再読み込み
    private void refreshItemsForSelectedReceipt() {
        Receipt selected =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            items.clear();
            detailTitle.setText(
                    "商品明細（レシートを選択してください）"
            );
            return;
        }

        detailTitle.setText(
                "商品明細（" + selected.getStoreName() + "）"
        );

        try {
            items.setAll(
                    repository.findItemsByReceiptId(selected.getId())
            );
        } catch (Exception e) {
            showError("商品明細を読み込めませんでした。", e);
        }
    }

    // 商品明細を登録
    private void addItem() {
        Receipt selected =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage("先にレシートを選択してください。");
            return;
        }

        String itemName = itemNameField.getText().trim();
        String quantityText = quantityField.getText().trim();
        String priceText = unitPriceField.getText().trim();
        String category = categoryBox.getValue();

        if (itemName.isEmpty()) {
            showMessage("商品名を入力してください。");
            return;
        }

        if (category == null) {
            showMessage("カテゴリを選択してください。");
            return;
        }

        try {
            double quantity = Double.parseDouble(quantityText);
            long unitPrice = Long.parseLong(priceText);

            if (!Double.isFinite(quantity) || quantity <= 0) {
                showMessage("数量は0より大きい数値にしてください。");
                return;
            }

            if (unitPrice < 0) {
                showMessage("単価は0以上で入力してください。");
                return;
            }

            repository.addItem(
                    selected.getId(),
                    itemName,
                    quantity,
                    unitPrice,
                    category
            );

            Receipt selectedReceipt =
                    receiptTable.getSelectionModel().getSelectedItem();

            if (selectedReceipt != null) {
                repository.updateTotalFromItems(selectedReceipt.getId());
                refreshReceipts();
                refreshItemsForSelectedReceipt();
            }

            itemNameField.clear();
            quantityField.clear();
            unitPriceField.clear();

            refreshItemsForSelectedReceipt();

            showMessage("商品を登録しました。");

        } catch (NumberFormatException e) {
            showMessage("数量は数値、単価は整数で入力してください。");
        } catch (Exception e) {
            showError("商品の登録に失敗しました。", e);
        }
    }


    // 選択中の商品明細を削除
    private void deleteItem() {
        ReceiptItem selected =
                itemTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage("削除する商品を選択してください。");
            return;
        }

        Receipt selectedReceipt =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selectedReceipt == null) {
            return;
        }

        int receiptId = selectedReceipt.getId();

        try {
            // 1. 商品明細を先に削除する
            repository.deleteItem(selected.getId());

            // 2. 削除後の商品明細から合計金額を再計算する
            repository.updateTotalFromItems(receiptId);

            // 3. レシート一覧を更新する
            refreshReceipts();

            // 4. 同じレシートを再選択して商品一覧も更新する
            for (Receipt receipt : receipts) {
                if (receipt.getId() == receiptId) {
                    receiptTable.getSelectionModel().select(receipt);
                    break;
                }
            }

            refreshItemsForSelectedReceipt();

            showMessage("商品を削除しました。");

        } catch (Exception e) {
            showError("商品の削除に失敗しました。", e);
        }
    }

    private void showMessage(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message, Exception e) {
        e.printStackTrace();

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("エラー");
        alert.setHeaderText(message);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }


    private void deleteCategory() {
        String selectedCategory = categoryBox.getValue();

        if (selectedCategory == null || selectedCategory.isBlank()) {
            showMessage("削除するカテゴリを選択してください。");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "カテゴリ「" + selectedCategory + "」を削除しますか？",
                ButtonType.YES,
                ButtonType.NO
        );

        confirm.setHeaderText("カテゴリの削除");

        if (confirm.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }

        try {
            repository.deleteCategory(selectedCategory);

            loadCategories();

            showMessage("カテゴリを削除しました。");

        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());

        } catch (Exception e) {
            showError("カテゴリの削除に失敗しました。", e);
        }
    }

    private void renameCategory() {
        String oldName = categoryBox.getValue();
        String newName = categoryNameField.getText().trim();

        if (oldName == null || oldName.isBlank()) {
            showMessage("変更するカテゴリを選択してください。");
            return;
        }

        if (newName.isEmpty()) {
            showMessage("新しいカテゴリ名を入力してください。");
            return;
        }

        if (oldName.equals(newName)) {
            showMessage("変更前と変更後のカテゴリ名が同じです。");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "カテゴリ名を「" + oldName + "」から「"
                        + newName + "」に変更しますか？",
                ButtonType.YES,
                ButtonType.NO
        );

        confirm.setHeaderText("カテゴリ名の変更");

        if (confirm.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }

        try {
            repository.renameCategory(oldName, newName);

            loadCategories();

            // 変更後のカテゴリを選択する
            categoryBox.setValue(newName);

            categoryNameField.clear();

            showMessage("カテゴリ名を変更しました。");

        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());

        } catch (Exception e) {
            showError("カテゴリ名の変更に失敗しました。", e);
        }
    }

    private void editReceipt() {
        Receipt selected =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage("編集するレシートを選択してください。");
            return;
        }

        LocalDate newDate = datePicker.getValue();
        String newStore = storeField.getText().trim();

        if (newDate == null) {
            showMessage("購入日を入力してください。");
            return;
        }

        if (newStore.isEmpty()) {
            showMessage("店名を入力してください。");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "選択したレシートの購入日と店名を変更しますか？",
                ButtonType.YES,
                ButtonType.NO
        );

        confirm.setHeaderText("レシートの編集");

        if (confirm.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }

        try {
            repository.updateReceipt(
                    selected.getId(),
                    newDate,
                    newStore
            );

            refreshReceipts();

            // 更新後も同じレシートを選択する
            for (Receipt receipt : receipts) {
                if (receipt.getId() == selected.getId()) {
                    receiptTable.getSelectionModel().select(receipt);
                    break;
                }
            }

            refreshItemsForSelectedReceipt();

            showMessage("レシートを更新しました。");

        } catch (Exception e) {
            showError("レシートの更新に失敗しました。", e);
        }
    }

    private void editItem() {
        ReceiptItem selected =
                itemTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage("編集する商品を選択してください。");
            return;
        }

        Receipt selectedReceipt =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selectedReceipt == null) {
            return;
        }

        String itemName = itemNameField.getText().trim();
        String quantityText = quantityField.getText().trim();
        String unitPriceText = unitPriceField.getText().trim();
        String categoryName = categoryBox.getValue();

        if (itemName.isEmpty()) {
            showMessage("商品名を入力してください。");
            return;
        }

        if (categoryName == null || categoryName.isBlank()) {
            showMessage("カテゴリを選択してください。");
            return;
        }

        try {
            double quantity = Double.parseDouble(quantityText);
            int unitPrice = Integer.parseInt(unitPriceText);

            repository.updateItem(
                    selected.getId(),
                    itemName,
                    quantity,
                    unitPrice,
                    categoryName
            );

            int receiptId = selectedReceipt.getId();

            repository.updateTotalFromItems(receiptId);

            refreshReceipts();

            for (Receipt receipt : receipts) {
                if (receipt.getId() == receiptId) {
                    receiptTable.getSelectionModel().select(receipt);
                    break;
                }
            }

            refreshItemsForSelectedReceipt();

            showMessage("商品を更新しました。");

        } catch (NumberFormatException e) {
            showMessage("数量と単価は正しい数値で入力してください。");
        } catch (Exception e) {
            showError("商品の更新に失敗しました。", e);
        }
    }
}