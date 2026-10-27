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
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.List;
import java.text.NumberFormat;
import java.time.LocalDate;
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

    // 商品名検索時に、商品明細を一致した商品だけに絞り込む設定
    private boolean showOnlyMatchedProducts = true;
    private String activeProductNameFilter = null;

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


        ComboBox<String> searchTypeBox = new ComboBox<>();
        searchTypeBox.getItems().addAll(
                "店舗名",
                "商品名",
                "タグ（カテゴリ）",
                "購入日"
        );
        searchTypeBox.setValue("店舗名");


        TextField searchField = new TextField();
        searchField.setPromptText("店舗名を入力");

// 入力候補の一覧
        ListView<String> searchSuggestions = new ListView<>();
        searchSuggestions.setMaxHeight(120);
        searchSuggestions.setVisible(false);
        searchSuggestions.setManaged(false);

// 入力欄と候補一覧を縦に配置
        VBox searchInputArea = new VBox(
                0,
                searchField,
                searchSuggestions
        );

        DatePicker searchDatePicker = new DatePicker();

        ComboBox<String> searchCategoryBox = new ComboBox<>();
        try {
            searchCategoryBox.getItems().setAll(
                    repository.findAllCategories()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        Button searchButton = new Button("検索");
        Button clearSearchButton = new Button("検索解除");
        Button productOnlyFilterButton = new Button(
                "商品名検索で商品だけを表示する（ON）");

        productOnlyFilterButton.setOnAction(event -> {
            showOnlyMatchedProducts = !showOnlyMatchedProducts;
            productOnlyFilterButton.setText(
                    "商品名検索で商品だけを表示する（"
                            + (showOnlyMatchedProducts ? "ON" : "OFF") + "）");
            refreshItemsForSelectedReceipt();
        });

        Label searchResultLabel = new Label("全件表示");

        // 追加検索条件を管理する領域
        List<SearchConditionRow> extraSearchConditions = new ArrayList<>();
        VBox extraSearchConditionsBox = new VBox(5);

        Button addSearchConditionButton = new Button("条件を追加");
        addSearchConditionButton.setOnAction(event -> {
            try {
                SearchConditionRow row =
                        new SearchConditionRow(repository.findAllCategories());

                row.removeButton.setOnAction(removeEvent -> {
                    extraSearchConditionsBox.getChildren().remove(row.container);
                    extraSearchConditions.remove(row);
                });

                extraSearchConditions.add(row);
                extraSearchConditionsBox.getChildren().add(row.container);
            } catch (Exception e) {
                showError("検索条件を追加できませんでした。", e);
            }
        });

        HBox searchForm = new HBox(
                10,
                searchTypeBox,
                searchInputArea,
                searchDatePicker,
                searchCategoryBox,
                searchButton,
                addSearchConditionButton,
                clearSearchButton,
                searchResultLabel
        );

        HBox productFilterForm = new HBox(8, productOnlyFilterButton);
        productFilterForm.setPadding(new Insets(3, 5, 3, 5));

        searchDatePicker.setVisible(false);
        searchDatePicker.setManaged(false);

        searchCategoryBox.setVisible(false);
        searchCategoryBox.setManaged(false);

        searchTypeBox.setOnAction(event -> {
            String type = searchTypeBox.getValue();

            searchField.setVisible("店舗名".equals(type)
                    || "商品名".equals(type));
            searchField.setManaged(searchField.isVisible());

            searchDatePicker.setVisible("購入日".equals(type));
            searchDatePicker.setManaged(searchDatePicker.isVisible());

            searchCategoryBox.setVisible("タグ（カテゴリ）".equals(type));
            searchCategoryBox.setManaged(searchCategoryBox.isVisible());

            if ("店舗名".equals(type)) {
                searchField.setPromptText("店舗名を入力");
            } else if ("商品名".equals(type)) {
                searchField.setPromptText("商品名を入力");
            }
        });


        // 候補リストを更新する
        Runnable updateSuggestions = () -> {
            String type = searchTypeBox.getValue();

            if (!"店舗名".equals(type)
                    && !"商品名".equals(type)) {
                searchSuggestions.getItems().clear();
                searchSuggestions.setVisible(false);
                searchSuggestions.setManaged(false);
                return;
            }

            try {
                List<String> allSuggestions = new ArrayList<>();

                // 店舗名の候補を取得
                if ("店舗名".equals(type)) {
                    for (Receipt receipt : repository.findAll()) {
                        String name = receipt.getStoreName();

                        if (name != null && !name.isBlank()
                                && !allSuggestions.contains(name)) {
                            allSuggestions.add(name);
                        }
                    }
                }

                // 商品名の候補を取得
                else {
                    for (Receipt receipt : repository.findAll()) {
                        for (ReceiptItem item :
                                repository.findItemsByReceiptId(
                                        receipt.getId())) {

                            String name = item.getItemName();

                            if (name != null && !name.isBlank()
                                    && !allSuggestions.contains(name)) {
                                allSuggestions.add(name);
                            }
                        }
                    }
                }

                // 入力文字に一致する候補だけを表示
                String keyword = searchField.getText()
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT);

                List<String> matched = allSuggestions.stream()
                        .filter(name -> name.toLowerCase(
                                        java.util.Locale.ROOT)
                                .contains(keyword))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();

                searchSuggestions.getItems().setAll(matched);

                boolean show = !matched.isEmpty()
                        && searchField.isVisible();

                searchSuggestions.setVisible(show);
                searchSuggestions.setManaged(show);

            } catch (Exception e) {
                e.printStackTrace();
                searchSuggestions.getItems().clear();
                searchSuggestions.setVisible(false);
                searchSuggestions.setManaged(false);
            }
        };

        // 入力文字が変わるたびに候補を更新
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) ->
                        updateSuggestions.run()
        );

        // 検索方法を変更したときも候補を更新
        searchTypeBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    searchField.clear();
                    updateSuggestions.run();
                }
        );


        // 選択した候補を入力欄に反映
        Runnable selectSuggestion = () -> {
            String selected =
                    searchSuggestions.getSelectionModel()
                            .getSelectedItem();

            if (selected != null) {
                searchField.setText(selected);
                searchField.positionCaret(selected.length());

                searchSuggestions.setVisible(false);
                searchSuggestions.setManaged(false);
                searchField.requestFocus();
            }
        };

        // マウスクリックで選択
        searchSuggestions.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 1) {
                selectSuggestion.run();
            }
        });

        // 候補リスト上でもEnterやTabで選択
        searchSuggestions.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER, TAB -> {
                    selectSuggestion.run();
                    event.consume();
                }
                default -> {
                }
            }
        });

        // 入力欄でTabを押すと先頭の候補を選択
        searchField.setOnKeyPressed(event -> {
            if (event.getCode()
                    == javafx.scene.input.KeyCode.TAB
                    && searchSuggestions.isVisible()
                    && !searchSuggestions.getItems().isEmpty()) {

                searchSuggestions.getSelectionModel()
                        .selectFirst();

                selectSuggestion.run();
                event.consume();
            }
        });


        // 検索ボタン：最初の条件と追加条件をすべて満たすレシートを表示
        searchButton.setOnAction(event -> {
            try {
                List<Receipt> allReceipts = repository.findAll();
                List<SearchConditionValue> conditions = new ArrayList<>();

                // 既存の検索欄を最初の条件として追加
                String type = searchTypeBox.getValue();
                String keyword = searchField.getText().trim();
                LocalDate searchDate = searchDatePicker.getValue();
                String category = searchCategoryBox.getValue();

                String validationMessage = validateSearchCondition(
                        type, keyword, searchDate, category);
                if (validationMessage != null) {
                    showMessage(validationMessage);
                    return;
                }

                conditions.add(new SearchConditionValue(
                        type, keyword, searchDate, category));

                // 追加された検索条件を確認して追加
                for (SearchConditionRow row : extraSearchConditions) {
                    String extraType = row.typeBox.getValue();
                    String extraKeyword = row.textField.getText().trim();
                    LocalDate extraDate = row.datePicker.getValue();
                    String extraCategory = row.categoryBox.getValue();

                    validationMessage = validateSearchCondition(
                            extraType, extraKeyword, extraDate, extraCategory);
                    if (validationMessage != null) {
                        showMessage("追加条件：" + validationMessage);
                        return;
                    }

                    conditions.add(new SearchConditionValue(
                            extraType, extraKeyword, extraDate, extraCategory));
                }

                List<Receipt> results = new ArrayList<>();

                for (Receipt receipt : allReceipts) {
                    boolean matchesAll = true;

                    for (SearchConditionValue condition : conditions) {
                        if (!matchesSearchCondition(receipt, condition)) {
                            matchesAll = false;
                            break;
                        }
                    }

                    if (matchesAll) {
                        results.add(receipt);
                    }
                }

                // メイン条件が商品名検索の場合、商品明細側の絞り込み語を設定
                activeProductNameFilter = "商品名".equals(type) ? keyword : null;

                receiptTable.getSelectionModel().clearSelection();
                receipts.setAll(results);

                if (!results.isEmpty()) {
                    receiptTable.getSelectionModel().select(0);
                } else {
                    showMessage("該当するレシートはありません。");
                }

                searchResultLabel.setText(
                        "検索結果：" + results.size()
                                + " 件 / 全 " + allReceipts.size() + " 件"
                );

            } catch (Exception e) {
                showError("検索に失敗しました。", e);
            }
        });

        // 検索解除ボタン
        clearSearchButton.setOnAction(event -> {
            activeProductNameFilter = null;
            searchField.clear();
            searchDatePicker.setValue(null);
            searchCategoryBox.setValue(null);

            searchSuggestions.getItems().clear();
            searchSuggestions.setVisible(false);
            searchSuggestions.setManaged(false);

            // 追加した検索条件もすべて解除
            for (SearchConditionRow row :
                    new ArrayList<>(extraSearchConditions)) {
                extraSearchConditionsBox.getChildren().remove(row.container);
            }
            extraSearchConditions.clear();

            receiptTable.getSelectionModel().clearSelection();

            refreshReceipts();

            searchResultLabel.setText("全 " + receipts.size() + " 件");
            statusLabel.setText("検索を解除しました。");
        });

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

        // 商品を選択したら、編集フォームに現在の値を自動入力する
        itemTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldItem, newItem) -> {
                    if (newItem != null) {
                        itemNameField.setText(newItem.getItemName());
                        quantityField.setText(
                                String.valueOf(newItem.getQuantity()));
                        unitPriceField.setText(
                                String.valueOf(newItem.getUnitPriceYen()));
                        categoryBox.setValue(newItem.getCategoryName());
                    }
                });

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
                searchForm,
                productFilterForm,
                extraSearchConditionsBox,
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
            List<ReceiptItem> receiptItems =
                    repository.findItemsByReceiptId(selected.getId());

            if (showOnlyMatchedProducts
                    && activeProductNameFilter != null
                    && !activeProductNameFilter.isBlank()) {
                String keyword = activeProductNameFilter
                        .toLowerCase(Locale.ROOT);

                items.setAll(receiptItems.stream()
                        .filter(item -> item.getItemName() != null
                                && item.getItemName().toLowerCase(Locale.ROOT)
                                .contains(keyword))
                        .toList());
            } else {
                items.setAll(receiptItems);
            }
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


    // 検索条件の入力値をまとめる
    private record SearchConditionValue(
            String type,
            String keyword,
            LocalDate date,
            String category) {
    }

    // 追加検索条件1行分のUI
    private static class SearchConditionRow {
        final ComboBox<String> typeBox = new ComboBox<>();
        final TextField textField = new TextField();
        final DatePicker datePicker = new DatePicker();
        final ComboBox<String> categoryBox = new ComboBox<>();
        final Button removeButton = new Button("条件を削除");
        final HBox container;

        SearchConditionRow(List<String> categories) {
            typeBox.getItems().addAll(
                    "店舗名", "商品名", "タグ（カテゴリ）", "購入日");
            typeBox.setValue("店舗名");

            textField.setPromptText("店舗名を入力");
            categoryBox.getItems().setAll(categories);

            datePicker.setVisible(false);
            datePicker.setManaged(false);
            categoryBox.setVisible(false);
            categoryBox.setManaged(false);

            typeBox.valueProperty().addListener(
                    (observable, oldValue, newValue) -> updateVisibility());

            container = new HBox(
                    8,
                    new Label("追加条件"),
                    typeBox,
                    textField,
                    datePicker,
                    categoryBox,
                    removeButton
            );
            container.setPadding(new Insets(3));
            updateVisibility();
        }

        private void updateVisibility() {
            String type = typeBox.getValue();

            boolean textVisible =
                    "店舗名".equals(type) || "商品名".equals(type);
            textField.setVisible(textVisible);
            textField.setManaged(textVisible);

            boolean dateVisible = "購入日".equals(type);
            datePicker.setVisible(dateVisible);
            datePicker.setManaged(dateVisible);

            boolean categoryVisible = "タグ（カテゴリ）".equals(type);
            categoryBox.setVisible(categoryVisible);
            categoryBox.setManaged(categoryVisible);

            if ("店舗名".equals(type)) {
                textField.setPromptText("店舗名を入力");
            } else if ("商品名".equals(type)) {
                textField.setPromptText("商品名を入力");
            }
        }
    }

    // 検索条件の入力漏れを確認する。問題がなければnullを返す
    private String validateSearchCondition(
            String type, String keyword, LocalDate date, String category) {

        if ("店舗名".equals(type) && keyword.isBlank()) {
            return "店舗名を入力してください。";
        }
        if ("商品名".equals(type) && keyword.isBlank()) {
            return "商品名を入力してください。";
        }
        if ("タグ（カテゴリ）".equals(type) && category == null) {
            return "カテゴリを選択してください。";
        }
        if ("購入日".equals(type) && date == null) {
            return "購入日を選択してください。";
        }
        return null;
    }

    // 指定された1つの条件をレシートが満たすか確認する
    private boolean matchesSearchCondition(
            Receipt receipt, SearchConditionValue condition) throws Exception {

        String type = condition.type();

        if ("店舗名".equals(type)) {
            return receipt.getStoreName() != null
                    && receipt.getStoreName().toLowerCase(Locale.ROOT)
                    .contains(condition.keyword().toLowerCase(Locale.ROOT));
        }

        if ("購入日".equals(type)) {
            return condition.date().equals(receipt.getPurchaseDate());
        }

        List<ReceiptItem> receiptItems =
                repository.findItemsByReceiptId(receipt.getId());

        if ("商品名".equals(type)) {
            String keyword = condition.keyword().toLowerCase(Locale.ROOT);
            for (ReceiptItem item : receiptItems) {
                if (item.getItemName() != null
                        && item.getItemName().toLowerCase(Locale.ROOT)
                        .contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        if ("タグ（カテゴリ）".equals(type)) {
            for (ReceiptItem item : receiptItems) {
                if (condition.category().equals(item.getCategoryName())) {
                    return true;
                }
            }
            return false;
        }

        return false;
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