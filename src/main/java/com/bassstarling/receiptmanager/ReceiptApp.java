package com.bassstarling.receiptmanager;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.scene.control.ListView;
import javafx.util.Callback;

import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;

public class ReceiptApp extends Application {

    // アプリの設定保存先
    private final Preferences uiPreferences =
            Preferences.userNodeForPackage(ReceiptApp.class);

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
            new Label(tr("language.select.receit"));

    TextField statusLabel = new TextField();

    // 商品名検索時に、商品明細を一致した商品だけに絞り込む設定
    private boolean showOnlyMatchedItems = true;
    private String activeItemNameFilter = null;

    private final NumberFormat yenFormat =
            NumberFormat.getNumberInstance(Locale.JAPAN);

    @Override
    public void start(Stage stage) {

        try {
            loadLanguage();
            Database.initialize();
        } catch (Exception e) {
            showError(tr("language.failed.reset.database"), e);
            return;
        }

        // -------------------------
        // レシート登録フォーム
        // -------------------------

        storeField.setPromptText(tr("language.shopname"));
        storeField.setPrefWidth(150);

        Button addReceiptButton =
                new Button(tr("language.button.add.receipt0"));

        addReceiptButton.setOnAction(
                event -> addReceipt());

        Button editReceiptButton = new Button(tr("language.button.edit.receipt0"));
        editReceiptButton.setOnAction(
                event -> editReceipt()
        );

        HBox receiptForm = new HBox(
                8,
                new Label(tr("language.date")),
                datePicker,
                storeField,
                addReceiptButton,
                editReceiptButton
        );
        receiptForm.setPadding(new Insets(5));

        receiptForm.setPadding(new Insets(5));


        ComboBox<String> searchTypeBox = new ComboBox<>();
        searchTypeBox.getItems().addAll(
                tr("language.shopname"),
                tr("language.itemname"),
                tr("language.categorytab"),
                tr("language.date")
        );
        searchTypeBox.setValue(tr("language.shopname"));


        TextField searchField = new TextField();
        searchField.setPromptText(tr("language.write.shopname"));

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

        Button searchButton = new Button(tr("language.button.search"));
        Button clearSearchButton = new Button(tr("language.button.clear.search"));
        Button itemOnlyFilterButton = new Button(
                tr("language.display.item0"));

        itemOnlyFilterButton.setOnAction(event -> {
            showOnlyMatchedItems = !showOnlyMatchedItems;
            itemOnlyFilterButton.setText(
                    tr("language.display.item")
                            + (showOnlyMatchedItems ? tr("language.on") : tr("language.off")) + "）");
            refreshItemsForSelectedReceipt();
        });

        Label searchResultLabel = new Label(tr("language.show.all"));

        // 追加検索条件を管理する領域
        List<SearchConditionRow> extraSearchConditions = new ArrayList<>();
        VBox extraSearchConditionsBox = new VBox(5);

        Button addSearchConditionButton = new Button(tr("language.add.conditions"));
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
                showError(tr("language.failed.add.condditions"), e);
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

        Button aggregationButton = new Button(tr("language.expense.summary"));

        aggregationButton.setOnAction(event -> showAggregationDialog());

        HBox itemFilterForm = new HBox(8, itemOnlyFilterButton);
        itemFilterForm.setPadding(new Insets(3, 5, 3, 5));

        searchDatePicker.setVisible(false);
        searchDatePicker.setManaged(false);

        searchCategoryBox.setVisible(false);
        searchCategoryBox.setManaged(false);

        searchTypeBox.setOnAction(event -> {
            String type = searchTypeBox.getValue();

            searchField.setVisible(tr("language.shopname").equals(type)
                    || tr("language.itemname").equals(type));
            searchField.setManaged(searchField.isVisible());

            searchDatePicker.setVisible(tr("language.date").equals(type));
            searchDatePicker.setManaged(searchDatePicker.isVisible());

            searchCategoryBox.setVisible(tr("language.categorytab").equals(type));
            searchCategoryBox.setManaged(searchCategoryBox.isVisible());

            if (tr("language.shopname").equals(type)) {
                searchField.setPromptText(tr("language.write.shopname"));
            } else if (tr("language.itemname").equals(type)) {
                searchField.setPromptText(tr("language.write.itemname"));
            }
        });


        // 候補リストを更新する
        Runnable updateSuggestions = () -> {
            String type = searchTypeBox.getValue();

            if (!tr("language.shopname").equals(type)
                    && !tr("language.itemname").equals(type)) {
                searchSuggestions.getItems().clear();
                searchSuggestions.setVisible(false);
                searchSuggestions.setManaged(false);
                return;
            }

            try {
                List<String> allSuggestions = new ArrayList<>();

                // 店舗名の候補を取得
                if (tr("language.shopname").equals(type)) {
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
                        showMessage(tr("language.additional.conditions") + validationMessage);
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
                activeItemNameFilter = tr("language.itemname").equals(type) ? keyword : null;

                receiptTable.getSelectionModel().clearSelection();
                receipts.setAll(results);

                if (!results.isEmpty()) {
                    receiptTable.getSelectionModel().select(0);
                } else {
                    showMessage(tr("language.notfounded.receipt"));
                }

                searchResultLabel.setText(
                        tr("language.result") + results.size()
                                + tr("language.allcase") + allReceipts.size() + tr("language.case")
                );

            } catch (Exception e) {
                showError(tr("language.failed.search"), e);
            }
        });

        // 検索解除ボタン
        clearSearchButton.setOnAction(event -> {
            activeItemNameFilter = null;
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

            searchResultLabel.setText(tr("language.all") + receipts.size() + tr("language.case"));
            statusLabel.setText(tr("language.canceled.search"));
        });

        categoryNameField.setPromptText(tr("language.add.categoryname"));
        categoryNameField.setPrefWidth(160);

        Button addCategoryButton = new Button(tr("language.button.add.category0"));
        addCategoryButton.setOnAction(event -> addCategory());

        Button deleteCategoryButton = new Button(tr("language.button.delete.category0"));
        deleteCategoryButton.setOnAction(
                event -> deleteCategory()
        );

        Button renameCategoryButton = new Button(tr("language.button.edit.category0"));
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
                new TableColumn<>(tr("language.date"));

        dateColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue()
                                .getPurchaseDate().toString()
                ));

        TableColumn<Receipt, String> storeColumn =
                new TableColumn<>(tr("language.shopname"));

        storeColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getStoreName()
                ));

        TableColumn<Receipt, String> totalColumn =
                new TableColumn<>(tr("language.total.amount"));

        totalColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        yenFormat.format(
                                cell.getValue().getTotalYen()
                        ) + tr("language.currency")
                ));

        receiptTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldReceipt, newReceipt) -> {
                    if (newReceipt != null) {
                        datePicker.setValue(newReceipt.getPurchaseDate());
                        storeField.setText(newReceipt.getStoreName());
                    }
                });

        dateColumn.setCellFactory(selectableTextCellFactory());
        storeColumn.setCellFactory(selectableTextCellFactory());
        totalColumn.setCellFactory(selectableTextCellFactory());

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
                new Button(tr("language.delete.receipt"));

        deleteReceiptButton.setOnAction(
                event -> deleteReceipt());

        // -------------------------
        // 商品明細の入力フォーム
        // -------------------------

        itemNameField.setPromptText(tr("language.itemname"));
        itemNameField.setPrefWidth(180);

        quantityField.setPromptText(tr("language.quantity0"));
        quantityField.setPrefWidth(80);

        unitPriceField.setPromptText(tr("language.unitprice"));
        unitPriceField.setPrefWidth(110);

        categoryBox.setPromptText(tr("language.categorytab"));
        categoryBox.setPrefWidth(130);

        Button addItemButton = new Button(tr("language.adding.item"));
        addItemButton.setOnAction(event -> addItem());
        Button editItemButton = new Button(tr("language.button.edit.item0"));
        editItemButton.setOnAction(event -> editItem());

        addItemButton.setOnAction(
                event -> addItem());

        receiptForm.setPadding(new Insets(5));


        // -------------------------
        // 商品明細一覧表
        // -------------------------

        TableColumn<ReceiptItem, String> itemNameColumn =
                new TableColumn<>(tr("language.itemname"));

        itemNameColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getItemName()
                ));

        TableColumn<ReceiptItem, String> quantityColumn =
                new TableColumn<>(tr("language.quantity0"));

        quantityColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(
                                cell.getValue().getQuantity()
                        )
                ));

        TableColumn<ReceiptItem, String> unitPriceColumn =
                new TableColumn<>(tr("language.unitprice"));

        unitPriceColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        yenFormat.format(
                                cell.getValue().getUnitPriceYen()
                        ) + tr("language.currency")
                ));

        TableColumn<ReceiptItem, String> subtotalColumn =
                new TableColumn<>(tr("language.subtotal0"));


        subtotalColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        yenFormat.format(
                                cell.getValue().getSubtotalYen()
                        ) + tr("language.currency")
                ));

        TableColumn<ReceiptItem, String> categoryColumn =
                new TableColumn<>(tr("language.categorytab"));

        categoryColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getCategoryName() == null
                                ? tr("language.uncategorized")
                                : cell.getValue().getCategoryName()
                ));

        itemNameColumn.setCellFactory(selectableTextCellFactory());
        quantityColumn.setCellFactory(selectableTextCellFactory());
        unitPriceColumn.setCellFactory(selectableTextCellFactory());
        subtotalColumn.setCellFactory(selectableTextCellFactory());
        categoryColumn.setCellFactory(selectableTextCellFactory());

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
                new Button(tr("language.delete.item"));

        deleteItemButton.setOnAction(
                event -> deleteItem());

        // -------------------------
        // 画面全体の配置
        // -------------------------

        Label receiptTitle = new Label(tr("language.receipt.list"));
        receiptTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        detailTitle.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold;"
        );

        itemNameField.setPromptText(tr("language.itemname"));
        quantityField.setPromptText(tr("language.quantity0"));
        unitPriceField.setPromptText(tr("language.unitprice"));

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

        // レシート一覧側の領域
        VBox receiptListArea = new VBox(
                8,
                receiptTitle,
                receiptForm,
                receiptTable,
                searchForm,
                aggregationButton,
                itemFilterForm,
                extraSearchConditionsBox,
                categoryForm,
                deleteReceiptButton
        );

        // レシート一覧の列幅を個別に変更できるようにする
        receiptTable.setColumnResizePolicy(
                TableView.UNCONSTRAINED_RESIZE_POLICY
        );

// 商品一覧の列幅を個別に変更できるようにする
        itemTable.setColumnResizePolicy(
                TableView.UNCONSTRAINED_RESIZE_POLICY
        );

        // レシート一覧の列幅を復元する
        restoreTableColumnWidths(receiptTable, tr("table.receipt"));

// 商品一覧の列幅を復元する
        restoreTableColumnWidths(itemTable, tr("table.item"));

// 商品一覧側の領域
        VBox itemListArea = new VBox(
                8,
                detailTitle,
                itemForm,
                itemTable,
                deleteItemButton
        );

// レシート一覧のテーブルを縦方向に伸縮させる
        VBox.setVgrow(receiptTable, Priority.ALWAYS);

// 商品一覧のテーブルを縦方向に伸縮させる
        VBox.setVgrow(itemTable, Priority.ALWAYS);

// 2つの領域を分割する
        SplitPane listSplitPane = new SplitPane();

// 上下に分割する
        listSplitPane.setOrientation(Orientation.VERTICAL);

// 上側にレシート一覧、下側に商品一覧を配置する
        listSplitPane.getItems().addAll(
                receiptListArea,
                itemListArea
        );

// 前回終了時の仕切り位置を復元する
        restoreSplitPaneDivider(
                listSplitPane,
                "layout.listDivider",
                0.6
        );

// SplitPane自体も画面の空きスペースに合わせて伸縮させる
        VBox.setVgrow(listSplitPane, Priority.ALWAYS);

// メイン画面
        VBox root = new VBox(
                8,
                new Label(tr("language.receiptmanager")),
                listSplitPane,
                statusLabel
        );

        // メイン画面の入力欄を管理する一覧
        Map<String, javafx.scene.Node> mainScreenSections =
                new LinkedHashMap<>();

        mainScreenSections.put(tr("language.section.receiptform"), receiptForm);
        mainScreenSections.put(tr("language.section.itemform"), itemForm);
        mainScreenSections.put(tr("language.section.categoryform"), categoryForm);

        root.setPadding(new Insets(12));

        VBox.setVgrow(receiptTable, Priority.ALWAYS);
        VBox.setVgrow(itemTable, Priority.ALWAYS);

        stage.setTitle(tr("language.receiptmanager"));

        // 上部操作バー
        ToolBar operationBar = new ToolBar();
        javafx.scene.layout.BorderPane windowLayout =
                new javafx.scene.layout.BorderPane();
        operationBar.setOrientation(
                javafx.geometry.Orientation.HORIZONTAL
        );

        Button addButton =
                new Button(tr("toolbar.add"));

        Button editButton =
                new Button(tr("toolbar.edit"));

        Button deleteButton =
                new Button(tr("toolbar.delete"));

        Button settingsButton =
                new Button(tr("toolbar.settings"));

        // メイン画面のボタンを管理する一覧
        Map<String, Button> mainScreenButtons =
                new LinkedHashMap<>();

        mainScreenButtons.put(tr("language.button.add.receipt"), addReceiptButton);
        mainScreenButtons.put(tr("language.button.edit.receipt"), editReceiptButton);
        mainScreenButtons.put(tr("language.button.delete.receipt"), deleteReceiptButton);

        mainScreenButtons.put(tr("language.button.add.item"), addItemButton);
        mainScreenButtons.put(tr("language.button.edit.item"), editItemButton);
        mainScreenButtons.put(tr("language.button.delete.item"), deleteItemButton);

        mainScreenButtons.put(tr("language.button.search"), searchButton);
        mainScreenButtons.put(tr("language.button.add.search.criteria"), addSearchConditionButton);
        mainScreenButtons.put(tr("language.button.clear.search"), clearSearchButton);
        mainScreenButtons.put(tr("language.button.toggle.item.name.search"), itemOnlyFilterButton);
        mainScreenButtons.put(tr("language.button.expense.summary"), aggregationButton);

        mainScreenButtons.put(tr("language.button.add.category"), addCategoryButton);
        mainScreenButtons.put(tr("language.button.edit.category"), renameCategoryButton);
        mainScreenButtons.put(tr("language.button.delete.category"), deleteCategoryButton);

        addButton.setOnAction(event ->
                openAddWindow(stage, searchCategoryBox, extraSearchConditions)
        );

        editButton.setOnAction(event ->
                openEditWindow(
                        stage,
                        searchCategoryBox,
                        extraSearchConditions
                )
        );

        deleteButton.setOnAction(event ->
                openDeleteWindow(
                        stage,
                        searchCategoryBox,
                        extraSearchConditions
                )
        );

        settingsButton.setOnAction(event ->
                openToolbarSettings(
                        stage,
                        windowLayout,
                        operationBar,
                        mainScreenButtons,
                        mainScreenSections
                )
        );

        operationBar.getItems().addAll(
                addButton,
                editButton,
                deleteButton,
                settingsButton
        );

        operationBar.setStyle(
                "-fx-background-color: #eeeeee;" +
                        "-fx-padding: 8px;"
        );

        windowLayout.setTop(operationBar);
        windowLayout.setCenter(root);

        // 保存されている設定を復元
        restoreUiSettings(
                windowLayout,
                operationBar,
                mainScreenButtons,
                mainScreenSections
        );

// Sceneを作成
        Scene scene = new Scene(windowLayout, 1050, 780);

// ウィンドウにSceneを設定
        stage.setTitle(tr("app.title"));
        stage.setScene(scene);
        stage.show();

        refreshReceipts();
        loadCategories();

        stage.setScene(scene);
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
                showMessage(tr("language.write.date"));
                return;
            }

            if (store.isEmpty()) {
                showMessage(tr("language.write.shopname"));
                return;
            }

            // 商品明細がまだないため、合計金額は0円で登録する
            repository.add(date, store, 0);

            storeField.clear();

            refreshReceipts();

            if (!receipts.isEmpty()) {
                receiptTable.getSelectionModel().select(0);
            }

            showMessage(tr("language.added.receipt"));

        } catch (Exception e) {
            showError(tr("language.failedadd.receipt"), e);
        }
    }


    // レシートを削除
    private void deleteReceipt() {
        Receipt selected =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage(tr("language.select.delete.receipt0"));
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                tr("language.confirm.delete.receipt") + selected.getStoreName()
                        + tr("language.confirm.delete.receipt0"),
                ButtonType.YES,
                ButtonType.NO
        );

        confirm.setHeaderText(tr("language.confirm.deleting.receipt"));

        if (confirm.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }

        try {
            repository.delete(selected.getId());
            refreshReceipts();
            showMessage(tr("language.deleted.receipt"));
        } catch (Exception e) {
            showError(tr("language.faileddelete"), e);
        }
    }

    // レシート一覧を再読み込み
    private void refreshReceipts() {
        try {
            receipts.setAll(repository.findAll());
        } catch (Exception e) {
            showError(tr("language.error.receipt"), e);
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
            showError(tr("language.error0.category"), e);
        }
    }


    private void addCategory() {
        String name = categoryNameField.getText().trim();

        if (name.isEmpty()) {
            showMessage(tr("language.add.category"));
            return;
        }

        try {
            repository.addCategory(name);

            // カテゴリ一覧を再読み込み
            loadCategories();

            // 入力欄を空にする
            categoryNameField.clear();

            showMessage(tr("language.added.category"));

        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());

        } catch (Exception e) {
            showError(tr("language.filedadd.category0"), e);
        }
    }


    // 選択中のレシートの商品明細を再読み込み
    private void refreshItemsForSelectedReceipt() {
        Receipt selected =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            items.clear();
            detailTitle.setText(
                    tr("language.select.receit")
            );
            return;
        }

        detailTitle.setText(
                tr("language.item0") + "（" + selected.getStoreName() + "）"
        );

        try {
            List<ReceiptItem> receiptItems =
                    repository.findItemsByReceiptId(selected.getId());

            if (showOnlyMatchedItems
                    && activeItemNameFilter != null
                    && !activeItemNameFilter.isBlank()) {
                String keyword = activeItemNameFilter
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
            showError(tr("language.error.item0"), e);
        }
    }

    // 商品明細を登録
    private void addItem() {
        Receipt selected =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage(tr("language.select.before.receipt"));
            return;
        }

        String itemName = itemNameField.getText().trim();
        String quantityText = quantityField.getText().trim();
        String priceText = unitPriceField.getText().trim();
        String category = categoryBox.getValue();

        if (itemName.isEmpty()) {
            showMessage(tr("language.write.itemname"));
            return;
        }

        if (category == null) {
            showMessage(tr("language.select.category"));
            return;
        }

        try {
            double quantity = Double.parseDouble(quantityText);
            long unitPrice = Long.parseLong(priceText);

            if (!Double.isFinite(quantity) || quantity <= 0) {
                showMessage(tr("language.showeditwindowmessage.isfinite"));
                return;
            }

            if (unitPrice < 0) {
                showMessage(tr("language.showeditwindowmessage.unitprice"));
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

            showMessage(tr("language.added.item0"));

        } catch (NumberFormatException e) {
            showMessage(tr("language.showeditwindowmessage.numberformatexception"));
        } catch (Exception e) {
            showError(tr("language.filedadd.item0"), e);
        }
    }


    // 選択中の商品明細を削除
    private void deleteItem() {
        ReceiptItem selected =
                itemTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage(tr("language.select.edititem"));
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

            showMessage(tr("language.deleted.item"));

        } catch (Exception e) {
            showError(tr("language.fileddelete.item0"), e);
        }
    }

    private void showMessage(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message, Exception e) {
        e.printStackTrace();

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(tr("language.error"));
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
        final Button removeButton = new Button(tr("language.delete.howadd"));
        final HBox container;

        SearchConditionRow(List<String> categories) {
            typeBox.getItems().addAll(
                    "language.shopname", "language.itemname", "language.categorytab", "language.date");
            typeBox.setValue("language.shopname");

            textField.setPromptText("language.write.shopname");
            categoryBox.getItems().setAll(categories);

            datePicker.setVisible(false);
            datePicker.setManaged(false);
            categoryBox.setVisible(false);
            categoryBox.setManaged(false);

            typeBox.valueProperty().addListener(
                    (observable, oldValue, newValue) -> updateVisibility());

            container = new HBox(
                    8,
                    new Label("language.howadd"),
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
                    "language.shopname".equals(type) || "language.itemname".equals(type);
            textField.setVisible(textVisible);
            textField.setManaged(textVisible);

            boolean dateVisible = "language.date".equals(type);
            datePicker.setVisible(dateVisible);
            datePicker.setManaged(dateVisible);

            boolean categoryVisible = "language.categorytab".equals(type);
            categoryBox.setVisible(categoryVisible);
            categoryBox.setManaged(categoryVisible);

            if ("language.shopname".equals(type)) {
                textField.setPromptText("language.write.shopname");
            } else if ("language.itemname".equals(type)) {
                textField.setPromptText("language.write.itemname");
            }
        }
    }

    // 検索条件の入力漏れを確認する。問題がなければnullを返す
    private String validateSearchCondition(
            String type, String keyword, LocalDate date, String category) {

        if ("language.shopname".equals(type) && keyword.isBlank()) {
            return "language.write.shopname";
        }
        if ("language.itemname".equals(type) && keyword.isBlank()) {
            return "language.add.item";
        }
        if ("language.categorytab".equals(type) && category == null) {
            return "language.select.category";
        }
        if ("language.date".equals(type) && date == null) {
            return "language.select.date";
        }
        return null;
    }

    // 指定された1つの条件をレシートが満たすか確認する
    private boolean matchesSearchCondition(
            Receipt receipt, SearchConditionValue condition) throws Exception {

        String type = condition.type();

        if ("language.shopname".equals(type)) {
            return receipt.getStoreName() != null
                    && receipt.getStoreName().toLowerCase(Locale.ROOT)
                    .contains(condition.keyword().toLowerCase(Locale.ROOT));
        }

        if ("language.date".equals(type)) {
            return condition.date().equals(receipt.getPurchaseDate());
        }

        List<ReceiptItem> receiptItems =
                repository.findItemsByReceiptId(receipt.getId());

        if ("language.itemname".equals(type)) {
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

        if ("language.categorytab".equals(type)) {
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
            showMessage("language.select.delete.category");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "language.confirm.delete.category" + selectedCategory + "language.confirm.delete.alert",
                ButtonType.YES,
                ButtonType.NO
        );

        confirm.setHeaderText("language.confirm.deleting.category");

        if (confirm.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }

        try {
            repository.deleteCategory(selectedCategory);

            loadCategories();

            showMessage("language.deleted.category");

        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());

        } catch (Exception e) {
            showError("language.fileddelete.category0", e);
        }
    }

    private void renameCategory() {
        String oldName = categoryBox.getValue();
        String newName = categoryNameField.getText().trim();

        if (oldName == null || oldName.isBlank()) {
            showMessage("language.select.edit.category");
            return;
        }

        if (newName.isEmpty()) {
            showMessage("language.write.newname.category");
            return;
        }

        if (oldName.equals(newName)) {
            showMessage("language.editerror.category");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "language.confirm.edit.category" + oldName + "language.confirm.edit.category0"
                        + newName + "language.confirm.edit.category1",
                ButtonType.YES,
                ButtonType.NO
        );

        confirm.setHeaderText("language.confirm.editing.category");

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

            showMessage("language.edited.category");

        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());

        } catch (Exception e) {
            showError("language.filededit.category", e);
        }
    }

    private void editReceipt() {
        Receipt selected =
                receiptTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage("language.select.edit.receipt0");
            return;
        }

        LocalDate newDate = datePicker.getValue();
        String newStore = storeField.getText().trim();

        if (newDate == null) {
            showMessage("language.write.date");
            return;
        }

        if (newStore.isEmpty()) {
            showMessage("language.write.shopname");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "language.corfirm.edit.receipt0",
                ButtonType.YES,
                ButtonType.NO
        );

        confirm.setHeaderText("language.confirm.editing.receipt");

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

            showMessage("language.updated.receipt");

        } catch (Exception e) {
            showError("language.filedupdate.receipt", e);
        }
    }

    private void editItem() {
        ReceiptItem selected =
                itemTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showMessage("language.select.edititem");
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
            showMessage("language.edititem");
            return;
        }

        if (categoryName == null || categoryName.isBlank()) {
            showMessage("language.select.category");
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

            showMessage("language.updated.item");

        } catch (NumberFormatException e) {
            showMessage("language.refreshitemsforselectedreceipt.numberformatexception");
        } catch (Exception e) {
            showError("language.filedupdate.item0", e);
        }
    }

    private static class AggregationRow {
        private final String period;
        private final String groupName;
        private final double totalYen;

        public AggregationRow(
                String period,
                String groupName,
                double totalYen) {
            this.period = period;
            this.groupName = groupName;
            this.totalYen = totalYen;
        }

        public String getPeriod() {
            return period;
        }

        public String getGroupName() {
            return groupName;
        }

        public double getTotalYen() {
            return totalYen;
        }
    }

    private void showAggregationDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(tr("language.calculation.expenditures"));

        dialog.getDialogPane().getButtonTypes()
                .add(ButtonType.CLOSE);

        ComboBox<String> periodBox = new ComboBox<>();
        periodBox.getItems().addAll(tr("language.byweek"), tr("language.bymonth"));
        periodBox.setValue("language.bymonth");

        ComboBox<String> groupBox = new ComboBox<>();
        groupBox.getItems().addAll(tr("language.byshop"), tr("language.bycategory"));
        groupBox.setValue(tr("language.byshop"));

        DatePicker startDatePicker = new DatePicker();
        DatePicker endDatePicker = new DatePicker();

        Button aggregateButton = new Button(tr("language.calculate"));

        Label totalLabel = new Label();

        TableView<AggregationRow> table = new TableView<>();

        TableColumn<AggregationRow, String> periodColumn =
                new TableColumn<>(tr("language.calculation.period"));

        periodColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getPeriod()));

        TableColumn<AggregationRow, String> groupColumn =
                new TableColumn<>(tr("language.shopcategory"));

        groupColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getGroupName()));

        TableColumn<AggregationRow, String> amountColumn =
                new TableColumn<>(tr("language.expenditures"));

        amountColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        yenFormat.format(
                                cell.getValue().getTotalYen()) + tr("language.currency")));

        table.getColumns().addAll(
                periodColumn,
                groupColumn,
                amountColumn
        );

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY);

        table.setPrefHeight(400);

        Runnable runAggregation = () -> {
            try {
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();

                if (startDate != null && endDate != null
                        && startDate.isAfter(endDate)) {
                    showMessage(tr("language.calculation.dateerror"));
                    return;
                }

                // 集計結果を「期間＋店舗／カテゴリ」ごとに保存する
                Map<String, Double> totals = new TreeMap<>();

                List<Receipt> allReceipts = repository.findAll();

                for (Receipt receipt : allReceipts) {
                    LocalDate date = receipt.getPurchaseDate();

                    if (date == null) {
                        continue;
                    }

                    if (startDate != null && date.isBefore(startDate)) {
                        continue;
                    }

                    if (endDate != null && date.isAfter(endDate)) {
                        continue;
                    }

                    String period;

                    if (tr("language.byweek").equals(periodBox.getValue())) {
                        LocalDate monday = date.with(
                                TemporalAdjusters.previousOrSame(
                                        DayOfWeek.MONDAY));

                        LocalDate sunday = monday.plusDays(6);

                        period = monday + " ～ " + sunday;
                    } else {
                        period = YearMonth.from(date).toString();
                    }

                    // 店舗別集計
                    if (tr("language.byshop").equals(groupBox.getValue())) {
                        String store = receipt.getStoreName();

                        if (store == null || store.isBlank()) {
                            store = tr("language.noshopname");
                        }

                        String key = period + "\u0001" + store;

                        totals.merge(
                                key,
                                (double) receipt.getTotalYen(),
                                Double::sum
                        );
                    }

                    // カテゴリ別集計
                    else {
                        List<ReceiptItem> receiptItems =
                                repository.findItemsByReceiptId(
                                        receipt.getId());

                        for (ReceiptItem item : receiptItems) {
                            String category = item.getCategoryName();

                            if (category == null || category.isBlank()) {
                                category = tr("language.uncategorized");
                            }

                            String key = period + "\u0001" + category;

                            totals.merge(
                                    key,
                                    (double) item.getSubtotalYen(),
                                    Double::sum
                            );
                        }
                    }
                }

                List<AggregationRow> rows = new ArrayList<>();

                for (Map.Entry<String, Double> entry
                        : totals.entrySet()) {

                    String[] parts = entry.getKey().split("\u0001", 2);

                    rows.add(new AggregationRow(
                            parts[0],
                            parts[1],
                            entry.getValue()
                    ));
                }

                // 期間、次に店舗／カテゴリの順に並べる
                rows.sort(
                        Comparator.comparing(AggregationRow::getPeriod)
                                .thenComparing(
                                        AggregationRow::getGroupName)
                );

                table.getItems().setAll(rows);

                double grandTotal = rows.stream()
                        .mapToDouble(AggregationRow::getTotalYen)
                        .sum();

                totalLabel.setText(
                        tr("language.calculation.scope") + rows.size()
                                + tr("language.calculation.casetotal")
                                + yenFormat.format(grandTotal) + tr("language.currency")
                );

            } catch (Exception e) {
                showError(tr("language.filedcalculate"), e);
            }
        };

        aggregateButton.setOnAction(
                event -> runAggregation.run());

        HBox controls = new HBox(
                8,
                new Label(tr("language.calculation.period")),
                periodBox,
                new Label(tr("language.calculation.method")),
                groupBox
        );

        HBox dateControls = new HBox(
                8,
                new Label(tr("language.calculation.startdate")),
                startDatePicker,
                new Label(tr("language.calculation.enddate")),
                endDatePicker,
                aggregateButton
        );

        VBox content = new VBox(
                10,
                controls,
                dateControls,
                totalLabel,
                table
        );

        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(750);

        // 初期表示時は全期間を集計する
        runAggregation.run();

        dialog.show();
    }

    private <S> Callback<TableColumn<S, String>, TableCell<S, String>>
    selectableTextCellFactory() {

        return column -> new TableCell<S, String>() {

            private final TextField textField = new TextField();

            private boolean textSelectionGesture = false;

            // ドラッグ開始位置
            private int dragAnchorIndex = 0;

            // ドラッグが開始されたか
            private boolean textDragStarted = false;

            private boolean dragLogPrinted = false;

            private int getTextIndex(
                    javafx.scene.input.MouseEvent event) {

                if (textField.getSkin() instanceof
                        javafx.scene.control.skin.TextFieldSkin skin) {

                    javafx.geometry.Point2D point =
                            textField.sceneToLocal(
                                    event.getSceneX(),
                                    event.getSceneY()
                            );

                    javafx.scene.text.HitInfo hit =
                            skin.getIndex(
                                    point.getX(),
                                    point.getY()
                            );

                    return Math.max(
                            0,
                            Math.min(
                                    textField.getText().length(),
                                    hit.getInsertionIndex()
                            )
                    );
                }

                return textField.getCaretPosition();
            }

            {
                textField.setEditable(false);
                textField.setFocusTraversable(true);
                textField.setMaxWidth(Double.MAX_VALUE);

                // TextFieldが常にマウス操作を受け取る
                textField.setMouseTransparent(false);

                textField.setStyle(
                        "-fx-background-color: transparent;" +
                                "-fx-control-inner-background: transparent;" +
                                "-fx-border-color: transparent;" +
                                "-fx-background-insets: 0;" +
                                "-fx-padding: 0 3 0 3;" +
                                "-fx-text-fill: #202020;" +
                                "-fx-font-size: 12px;"
                );

                // マウスボタンを押したとき
                textField.addEventFilter(
                        javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                        event -> {

                            if (event.getButton() !=
                                    javafx.scene.input.MouseButton.PRIMARY) {
                                return;
                            }

                            TableView<S> table = getTableView();

                            if (table != null && !isEmpty()
                                    && getIndex() >= 0) {

                                table.getSelectionModel().select(getIndex());
                            }

                            if (event.isShiftDown()) {

                                textSelectionGesture = true;
                                textDragStarted = false;

                                // ドラッグ開始位置を記録
                                dragAnchorIndex = getTextIndex(event);

                                textField.requestFocus();

                                // Shift＋クリックの標準動作を維持する
                            } else {

                                textSelectionGesture = false;
                                textDragStarted = false;

                                if (table != null) {
                                    table.requestFocus();
                                }

                                event.consume();
                            }
                        }
                );

// マウスをドラッグしたとき
                textField.addEventFilter(
                        javafx.scene.input.MouseEvent.MOUSE_DRAGGED,
                        event -> {

                            if (textSelectionGesture) {

                                textDragStarted = true;

                                // 現在のマウス位置を取得
                                int currentIndex = getTextIndex(event);

                                // 開始位置から現在位置まで選択
                                textField.selectRange(
                                        dragAnchorIndex,
                                        currentIndex
                                );
                            }

                            event.consume();
                        }
                );

// マウスボタンを離したとき
                textField.addEventFilter(
                        javafx.scene.input.MouseEvent.MOUSE_RELEASED,
                        event -> {

                            if (textSelectionGesture && textDragStarted) {

                                int currentIndex = getTextIndex(event);

                                textField.selectRange(
                                        dragAnchorIndex,
                                        currentIndex
                                );

                                event.consume();
                            }

                            textSelectionGesture = false;
                            textDragStarted = false;
                        }
                );
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    textField.clear();
                    setGraphic(null);
                } else {
                    textField.setText(item);
                    setGraphic(textField);
                    setText(null);
                }
            }
        };
    }

    // ==============================
// 追加ウィンドウ
// ==============================

    private void openAddWindow(
            Stage owner,
            ComboBox<String> searchCategoryBox,
            List<SearchConditionRow> extraSearchConditions) {

        Stage addStage = new Stage();

        addStage.initOwner(owner);
        addStage.initModality(
                javafx.stage.Modality.WINDOW_MODAL
        );

        addStage.setTitle(tr("language.add"));


        // ==============================
        // タブの準備
        // ==============================

        TabPane tabPane = new TabPane();


        // ==============================
        // レシート一覧（商品追加用）
        // ==============================

        ObservableList<Receipt> receiptChoices =
                FXCollections.observableArrayList();

        ListView<Receipt> receiptChoiceList =
                new ListView<>(receiptChoices);

        receiptChoiceList.setPrefWidth(270);
        receiptChoiceList.setPrefHeight(280);
        receiptChoiceList.setPlaceholder(
                new Label(tr("language.cantfind.receipt")
        ));

        // レシートの表示内容
        receiptChoiceList.setCellFactory(listView ->
                new ListCell<Receipt>() {

                    @Override
                    protected void updateItem(
                            Receipt receipt,
                            boolean empty) {

                        super.updateItem(receipt, empty);

                        if (empty || receipt == null) {
                            setText(null);
                        } else {
                            setText(
                                    receipt.getPurchaseDate()
                                            + "  "
                                            + receipt.getStoreName()
                                            + "\n"
                                            + yenFormat.format(
                                            receipt.getTotalYen()
                                    )
                                            + tr("language.currency")
                            );
                        }
                    }
                }
        );

        // レシート一覧をデータベースから更新
        Runnable refreshReceiptChoices = () -> {

            int previousId = -1;

            Receipt previous =
                    receiptChoiceList.getSelectionModel()
                            .getSelectedItem();

            if (previous != null) {
                previousId = previous.getId();
            }

            try {
                receiptChoices.setAll(repository.findAll());

                boolean restored = false;

                for (Receipt receipt : receiptChoices) {

                    if (receipt.getId() == previousId) {
                        receiptChoiceList.getSelectionModel()
                                .select(receipt);

                        restored = true;
                        break;
                    }
                }

                if (!restored && !receiptChoices.isEmpty()) {
                    receiptChoiceList.getSelectionModel()
                            .selectFirst();
                }

            } catch (Exception e) {
                showError(
                        tr("language.error.addreceipt"),
                        e
                );
            }
        };

        refreshReceiptChoices.run();


        // ==============================
        // ① レシート追加タブ
        // ==============================

        DatePicker newReceiptDate =
                new DatePicker(LocalDate.now());

        TextField newReceiptStore =
                new TextField();

        newReceiptStore.setPromptText(tr("language.shopname"));
        newReceiptStore.setPrefWidth(250);

        Button saveReceiptButton =
                new Button(tr("language.button.add.receipt0"));

        saveReceiptButton.setOnAction(event -> {

            LocalDate date = newReceiptDate.getValue();

            String store =
                    newReceiptStore.getText().trim();

            if (date == null) {
                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.WARNING,
                        tr("language.write.date")
                );
                return;
            }

            if (store.isEmpty()) {
                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.WARNING,
                        tr("language.write.shopname")
                );
                return;
            }

            try {

                repository.add(date, store, 0);

                refreshReceipts();
                refreshReceiptChoices.run();

                newReceiptStore.clear();

                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.added.receipt")
                );

            } catch (Exception e) {

                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.ERROR,
                        tr("language.filedadd.receipt")
                                + e.getMessage()
                );
            }
        });

        VBox receiptTabContent = new VBox(
                12,
                new Label(tr("language.date")),
                newReceiptDate,
                new Label(tr("language.shopname")),
                newReceiptStore,
                saveReceiptButton
        );

        receiptTabContent.setPadding(
                new Insets(20)
        );

        Tab receiptTab = new Tab(
                tr("language.receipt.tab"),
                receiptTabContent
        );

        receiptTab.setClosable(false);


        // ==============================
        // ② 商品追加タブ
        // ==============================

        TextField newItemName = new TextField();
        newItemName.setPromptText(tr("language.itemname"));

        TextField newQuantity = new TextField();
        newQuantity.setPromptText(tr("language.quantity0"));

        TextField newUnitPrice = new TextField();
        newUnitPrice.setPromptText(tr("language.unitprice"));

        ComboBox<String> newCategory =
                new ComboBox<>();

        newCategory.setPromptText(tr("language.categorytab"));
        newCategory.setMaxWidth(Double.MAX_VALUE);

        // カテゴリ一覧を読み込む
        try {
            newCategory.getItems().setAll(
                    repository.findAllCategories()
            );

            if (!newCategory.getItems().isEmpty()) {
                newCategory.getSelectionModel().selectFirst();
            }

        } catch (Exception e) {
            showError(tr("language.error0.category"), e);
        }


        // 商品入力フォーム
        Button saveItemButton =
                new Button(tr("language.adding.item"));

        VBox itemForm = new VBox(
                10,
                new Label(tr("language.itemname")),
                newItemName,
                new Label(tr("language.quantity0")),
                newQuantity,
                new Label(tr("language.unitprice")),
                newUnitPrice,
                new Label(tr("language.categorytab")),
                newCategory,
                saveItemButton
        );

        itemForm.setPadding(
                new Insets(10)
        );

        itemForm.setPrefWidth(390);


        // 左側：レシート選択欄
        VBox receiptSelectPane = new VBox(
                8,
                new Label(tr("language.additem.selectereceipt")),
                receiptChoiceList
        );

        receiptSelectPane.setPadding(
                new Insets(10)
        );

        receiptSelectPane.setPrefWidth(280);
        receiptSelectPane.setMinWidth(230);

        VBox.setVgrow(
                receiptChoiceList,
                Priority.ALWAYS
        );


        // 左右に配置
        HBox itemTabContent = new HBox(
                10,
                receiptSelectPane,
                itemForm
        );

        itemTabContent.setPadding(
                new Insets(10)
        );

        HBox.setHgrow(
                itemForm,
                Priority.ALWAYS
        );


        // 商品追加処理
        saveItemButton.setOnAction(event -> {

            Receipt targetReceipt =
                    receiptChoiceList.getSelectionModel()
                            .getSelectedItem();

            if (targetReceipt == null) {
                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.add.receipt")
                );
                return;
            }

            String name = newItemName.getText().trim();
            String quantityText = newQuantity.getText().trim();
            String priceText = newUnitPrice.getText().trim();
            String category = newCategory.getValue();

            if (name.isEmpty()) {
                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.WARNING,
                        tr("language.add.item")
                );
                return;
            }

            if (category == null) {
                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.category")
                );
                return;
            }

            try {

                double quantity =
                        Double.parseDouble(quantityText);

                long unitPrice =
                        Long.parseLong(priceText);

                if (!Double.isFinite(quantity) || quantity <= 0) {
                    showAddWindowMessage(
                            addStage,
                            Alert.AlertType.WARNING,
                            tr("language.showEditWindowMessage.isfinite")
                    );
                    return;
                }

                if (unitPrice < 0) {
                    showAddWindowMessage(
                            addStage,
                            Alert.AlertType.WARNING,
                            tr("language.showEditWindowMessage.unitprice")
                    );
                    return;
                }

                int targetReceiptId = targetReceipt.getId();

                repository.addItem(
                        targetReceiptId,
                        name,
                        quantity,
                        unitPrice,
                        category
                );

                // 商品明細からレシート合計を再計算
                repository.updateTotalFromItems(
                        targetReceiptId
                );

                // メイン画面を更新
                refreshReceipts();

                for (Receipt receipt : receipts) {

                    if (receipt.getId() == targetReceiptId) {

                        receiptTable.getSelectionModel()
                                .select(receipt);

                        break;
                    }
                }

                refreshItemsForSelectedReceipt();

                // 左側のレシート一覧も更新
                refreshReceiptChoices.run();

                newItemName.clear();
                newQuantity.clear();
                newUnitPrice.clear();

                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.added.item")
                );

            } catch (NumberFormatException e) {

                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.WARNING,
                        tr("language.showeditwindowmessage.numberformatexception")
                );

            } catch (Exception e) {

                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.ERROR,
                        tr("language.filedadd.item")
                                + e.getMessage()
                );
            }
        });


        Tab itemTab = new Tab(
                tr("language.item.tab"),
                itemTabContent
        );

        itemTab.setClosable(false);


        // ==============================
        // ③ タグ追加タブ
        // ==============================

        TextField newCategoryName = new TextField();

        newCategoryName.setPromptText(tr("language.add.categoryname"));
        newCategoryName.setPrefWidth(250);

        Button saveCategoryButton =
                new Button(tr("language.save.category"));

        saveCategoryButton.setOnAction(event -> {

            String name =
                    newCategoryName.getText().trim();

            if (name.isEmpty()) {
                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.WARNING,
                        tr("language.add.category")
                );
                return;
            }

            try {

                repository.addCategory(name);

                // データベースからカテゴリ一覧を再取得
                List<String> categories =
                        repository.findAllCategories();

                // メイン画面のカテゴリ欄を更新
                loadCategories();

                // 検索用カテゴリ欄も更新
                searchCategoryBox.getItems().setAll(categories);

                // 既存の追加検索条件のカテゴリ欄も更新
                for (SearchConditionRow row :
                        extraSearchConditions) {

                    row.categoryBox.getItems().setAll(categories);
                }

                // このウィンドウの商品追加用カテゴリ欄も更新
                newCategory.getItems().setAll(categories);
                newCategory.setValue(name);

                newCategoryName.clear();

                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.added.category")
                );

            } catch (IllegalArgumentException e) {

                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.WARNING,
                        e.getMessage()
                );

            } catch (Exception e) {

                showAddWindowMessage(
                        addStage,
                        Alert.AlertType.ERROR,
                        tr("language.filedadd.category")
                                + e.getMessage()
                );
            }
        });

        VBox categoryTabContent = new VBox(
                12,
                new Label(tr("language.categoryname")),
                newCategoryName,
                saveCategoryButton
        );

        categoryTabContent.setPadding(
                new Insets(20)
        );

        Tab categoryTab = new Tab(
                tr("language.categorytab"),
                categoryTabContent
        );

        categoryTab.setClosable(false);


        // ==============================
        // ウィンドウを表示
        // ==============================

        tabPane.getTabs().addAll(
                receiptTab,
                itemTab,
                categoryTab
        );

        tabPane.setTabClosingPolicy(
                TabPane.TabClosingPolicy.UNAVAILABLE
        );

        addStage.setScene(
                new Scene(tabPane, 760, 430)
        );

        addStage.show();
    }


    // 追加ウィンドウ内のメッセージ表示
    private void showAddWindowMessage(
            Stage owner,
            Alert.AlertType type,
            String message) {

        Alert alert = new Alert(type);

        alert.initOwner(owner);
        alert.setTitle(tr("language.add"));
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }

    // ======================================
// 編集ウィンドウ
// ======================================

    private void openEditWindow(
            Stage owner,
            ComboBox<String> searchCategoryBox,
            List<SearchConditionRow> extraSearchConditions) {

        Stage editStage = new Stage();
        editStage.initOwner(owner);
        editStage.initModality(
                javafx.stage.Modality.WINDOW_MODAL
        );
        editStage.setTitle(tr("language.alert.setTitle.edit"));

        TabPane tabPane = new TabPane();


        // ======================================
        // ① レシート編集
        // ======================================

        ObservableList<Receipt> receiptChoices =
                FXCollections.observableArrayList();

        ListView<Receipt> receiptList =
                new ListView<>(receiptChoices);

        receiptList.setPrefWidth(280);
        receiptList.setPrefHeight(300);

        receiptList.setCellFactory(listView ->
                new ListCell<Receipt>() {

                    @Override
                    protected void updateItem(
                            Receipt receipt,
                            boolean empty) {

                        super.updateItem(receipt, empty);

                        if (empty || receipt == null) {
                            setText(null);
                        } else {
                            setText(
                                    receipt.getPurchaseDate()
                                            + "  "
                                            + receipt.getStoreName()
                                            + "\n"
                                            + yenFormat.format(
                                            receipt.getTotalYen()
                                    )
                                            + tr("language.currency")
                            );
                        }
                    }
                }
        );

        DatePicker editDatePicker = new DatePicker();

        TextField editStoreField = new TextField();
        editStoreField.setPromptText(tr("language.shopname"));

        receiptList.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldReceipt, newReceipt) -> {

                    if (newReceipt != null) {
                        editDatePicker.setValue(
                                newReceipt.getPurchaseDate()
                        );

                        editStoreField.setText(
                                newReceipt.getStoreName()
                        );
                    }
                });

        // レシート一覧を再読み込み
        Runnable reloadReceiptList = () -> {

            Receipt previous =
                    receiptList.getSelectionModel().getSelectedItem();

            int previousId =
                    previous == null ? -1 : previous.getId();

            try {
                receiptChoices.setAll(repository.findAll());

                boolean restored = false;

                for (Receipt receipt : receiptChoices) {

                    if (receipt.getId() == previousId) {
                        receiptList.getSelectionModel()
                                .select(receipt);

                        restored = true;
                        break;
                    }
                }

                if (!restored && !receiptChoices.isEmpty()) {
                    receiptList.getSelectionModel().selectFirst();
                }

            } catch (Exception e) {
                showError(
                        tr("language.error.receipt"),
                        e
                );
            }
        };

        Button saveReceiptButton =
                new Button(tr("language.save.category"));

        saveReceiptButton.setOnAction(event -> {

            Receipt selected =
                    receiptList.getSelectionModel().getSelectedItem();

            if (selected == null) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.edit.receipt0")
                );
                return;
            }

            LocalDate date = editDatePicker.getValue();
            String store = editStoreField.getText().trim();

            if (date == null || store.isEmpty()) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        tr("language.add.receipt")
                );
                return;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    tr("language.confirm.edit.receipt"),
                    ButtonType.YES,
                    ButtonType.NO
            );

            confirm.initOwner(editStage);
            confirm.setHeaderText(tr("language.confirm.editing.receipt"));

            if (confirm.showAndWait().orElse(ButtonType.NO)
                    != ButtonType.YES) {
                return;
            }

            try {
                int receiptId = selected.getId();

                repository.updateReceipt(
                        receiptId,
                        date,
                        store
                );

                // メイン画面を更新
                refreshReceipts();

                for (Receipt receipt : receipts) {
                    if (receipt.getId() == receiptId) {
                        receiptTable.getSelectionModel()
                                .select(receipt);
                        break;
                    }
                }

                refreshItemsForSelectedReceipt();

                // 編集ウィンドウの一覧も更新
                reloadReceiptList.run();

                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.updated.receipt")
                );

            } catch (Exception e) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.ERROR,
                        tr("language.filedupdate.receipt")
                                + e.getMessage()
                );
            }
        });

        VBox receiptEditForm = new VBox(
                12,
                new Label(tr("language.date")),
                editDatePicker,
                new Label(tr("language.shopname")),
                editStoreField,
                saveReceiptButton
        );

        receiptEditForm.setPadding(new Insets(15));

        VBox receiptListPane = new VBox(
                8,
                new Label(tr("language.editreceiptbutton")),
                receiptList
        );

        receiptListPane.setPadding(new Insets(10));

        VBox.setVgrow(receiptList, Priority.ALWAYS);

        HBox receiptEditContent = new HBox(
                10,
                receiptListPane,
                receiptEditForm
        );

        receiptEditContent.setPadding(new Insets(10));
        HBox.setHgrow(receiptEditForm, Priority.ALWAYS);

        reloadReceiptList.run();

        Tab receiptTab = new Tab(
                tr("language.receipt.tab"),
                receiptEditContent
        );

        receiptTab.setClosable(false);


        // ======================================
        // ② 商品編集
        // ======================================

        // 編集対象レシート一覧

        ObservableList<Receipt> itemReceiptChoices =
                FXCollections.observableArrayList();

        ListView<Receipt> itemReceiptList =
                new ListView<>(itemReceiptChoices);

        itemReceiptList.setPrefWidth(220);

        itemReceiptList.setCellFactory(listView ->
                new ListCell<Receipt>() {

                    @Override
                    protected void updateItem(
                            Receipt receipt,
                            boolean empty) {

                        super.updateItem(receipt, empty);

                        if (empty || receipt == null) {
                            setText(null);
                        } else {
                            setText(
                                    receipt.getPurchaseDate()
                                            + "\n"
                                            + receipt.getStoreName()
                                            + " / "
                                            + yenFormat.format(
                                            receipt.getTotalYen()
                                    )
                                            + tr("language.currency")
                            );
                        }
                    }
                }
        );


        // 選択レシートの商品一覧

        ObservableList<ReceiptItem> itemChoices =
                FXCollections.observableArrayList();

        ListView<ReceiptItem> itemList =
                new ListView<>(itemChoices);

        itemList.setPrefWidth(240);

        itemList.setCellFactory(listView ->
                new ListCell<ReceiptItem>() {

                    @Override
                    protected void updateItem(
                            ReceiptItem item,
                            boolean empty) {

                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(
                                    item.getItemName()
                                            + tr("language.quantity")
                                            + item.getQuantity()
                                            + tr("language.subtotal")
                                            + yenFormat.format(
                                            item.getSubtotalYen()
                                    )
                                            + tr("language.currency")
                                            + tr("language.category")
                                            + (item.getCategoryName() == null
                                            ? tr("language.uncategorized")
                                            : item.getCategoryName())
                            );
                        }
                    }
                }
        );


        // 商品編集用の入力欄

        TextField editItemName = new TextField();
        editItemName.setPromptText(tr("language.itemname"));

        TextField editQuantity = new TextField();
        editQuantity.setPromptText(tr("language.quantity0"));

        TextField editUnitPrice = new TextField();
        editUnitPrice.setPromptText(tr("language.unitprice"));

        ComboBox<String> editCategory = new ComboBox<>();
        editCategory.setPromptText(tr("language.categorytab"));
        editCategory.setMaxWidth(Double.MAX_VALUE);

        try {
            editCategory.getItems().setAll(
                    repository.findAllCategories()
            );

        } catch (Exception e) {
            showError(tr("language.error.category"), e);
        }


        // レシートを選択したときに商品一覧を更新する

        itemReceiptList.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldReceipt, newReceipt) -> {

                    itemChoices.clear();
                    itemList.getSelectionModel().clearSelection();

                    if (newReceipt == null) {
                        return;
                    }

                    try {
                        itemChoices.setAll(
                                repository.findItemsByReceiptId(
                                        newReceipt.getId()
                                )
                        );

                    } catch (Exception e) {
                        showError(
                                tr("language.error.item"),
                                e
                        );
                    }
                });


        // 商品を選択したときに編集欄へ反映する

        itemList.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldItem, newItem) -> {

                    if (newItem != null) {

                        editItemName.setText(
                                newItem.getItemName()
                        );

                        editQuantity.setText(
                                String.valueOf(
                                        newItem.getQuantity()
                                )
                        );

                        editUnitPrice.setText(
                                String.valueOf(
                                        newItem.getUnitPriceYen()
                                )
                        );

                        editCategory.setValue(
                                newItem.getCategoryName()
                        );
                    }
                });


        // 編集対象レシートを読み込む

        Runnable reloadItemReceiptList = () -> {

            Receipt previous =
                    itemReceiptList.getSelectionModel()
                            .getSelectedItem();

            int previousId =
                    previous == null ? -1 : previous.getId();

            try {
                itemReceiptChoices.setAll(
                        repository.findAll()
                );

                boolean restored = false;

                for (Receipt receipt : itemReceiptChoices) {

                    if (receipt.getId() == previousId) {
                        itemReceiptList.getSelectionModel()
                                .select(receipt);

                        restored = true;
                        break;
                    }
                }

                if (!restored && !itemReceiptChoices.isEmpty()) {
                    itemReceiptList.getSelectionModel()
                            .selectFirst();
                }

            } catch (Exception e) {
                showError(
                        tr("language.error.receipt"),
                        e
                );
            }
        };


        // 商品の保存

        Button saveItemButton =
                new Button(tr("language.save.category"));

        saveItemButton.setOnAction(event -> {

            Receipt selectedReceipt =
                    itemReceiptList.getSelectionModel()
                            .getSelectedItem();

            ReceiptItem selectedItem =
                    itemList.getSelectionModel()
                            .getSelectedItem();

            if (selectedReceipt == null || selectedItem == null) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.edit.receipt0")
                );
                return;
            }

            String name = editItemName.getText().trim();
            String quantityText = editQuantity.getText().trim();
            String priceText = editUnitPrice.getText().trim();
            String category = editCategory.getValue();

            if (name.isEmpty()) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        tr("language.add.item")
                );
                return;
            }

            if (category == null) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.category")
                );
                return;
            }

            try {
                double quantity = Double.parseDouble(quantityText);
                int unitPrice = Integer.parseInt(priceText);

                if (!Double.isFinite(quantity) || quantity <= 0) {
                    showEditWindowMessage(
                            editStage,
                            Alert.AlertType.WARNING,
                            tr("language.showEditWindowMessage.isfinite")
                    );
                    return;
                }

                if (unitPrice < 0) {
                    showEditWindowMessage(
                            editStage,
                            Alert.AlertType.WARNING,
                            tr("language.showEditWindowMessage.unitprice")
                    );
                    return;
                }

                int receiptId = selectedReceipt.getId();
                int itemId = selectedItem.getId();

                repository.updateItem(
                        itemId,
                        name,
                        quantity,
                        unitPrice,
                        category
                );

                // 商品からレシート合計を再計算
                repository.updateTotalFromItems(receiptId);

                // メイン画面を更新
                refreshReceipts();

                for (Receipt receipt : receipts) {
                    if (receipt.getId() == receiptId) {
                        receiptTable.getSelectionModel()
                                .select(receipt);
                        break;
                    }
                }

                refreshItemsForSelectedReceipt();

                // 編集ウィンドウ側の一覧も更新
                reloadItemReceiptList.run();

                // 更新した商品を再選択
                for (ReceiptItem item : itemChoices) {
                    if (item.getId() == itemId) {
                        itemList.getSelectionModel().select(item);
                        break;
                    }
                }

                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.updated.item")
                );

            } catch (NumberFormatException e) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        tr("language.showeditwindowmessage.numberformatexception")
                );

            } catch (Exception e) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.ERROR,
                        tr("language.filedupdate.item")
                                + e.getMessage()
                );
            }
        });


        VBox itemReceiptPane = new VBox(
                8,
                new Label(tr("language.receipt.list")),
                itemReceiptList
        );

        VBox itemListPane = new VBox(
                8,
                new Label(tr("language.item.list")),
                itemList
        );

        VBox itemEditForm = new VBox(
                10,
                new Label(tr("language.itemname")),
                editItemName,
                new Label(tr("language.quantity0")),
                editQuantity,
                new Label(tr("language.unitprice")),
                editUnitPrice,
                new Label(tr("language.categorytab")),
                editCategory,
                saveItemButton
        );

        itemReceiptPane.setPadding(new Insets(8));
        itemListPane.setPadding(new Insets(8));
        itemEditForm.setPadding(new Insets(8));

        VBox.setVgrow(itemReceiptList, Priority.ALWAYS);
        VBox.setVgrow(itemList, Priority.ALWAYS);

        HBox itemEditContent = new HBox(
                8,
                itemReceiptPane,
                itemListPane,
                itemEditForm
        );

        itemEditContent.setPadding(new Insets(8));

        HBox.setHgrow(itemEditForm, Priority.ALWAYS);

        reloadItemReceiptList.run();

        Tab itemTab = new Tab(
                tr("language.item.tab"),
                itemEditContent
        );

        itemTab.setClosable(false);


        // ======================================
        // ③ タグ編集
        // ======================================

        ObservableList<String> categoryChoices =
                FXCollections.observableArrayList();

        ListView<String> categoryList =
                new ListView<>(categoryChoices);

        categoryList.setPrefWidth(280);

        TextField editCategoryName = new TextField();
        editCategoryName.setPromptText(tr("language.editcategory"));

        categoryList.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldName, newName) -> {

                    if (newName != null) {
                        editCategoryName.setText(newName);
                    }
                });


        Runnable reloadCategoryList = () -> {

            String previous =
                    categoryList.getSelectionModel().getSelectedItem();

            try {
                categoryChoices.setAll(
                        repository.findAllCategories()
                );

                if (previous != null &&
                        categoryChoices.contains(previous)) {

                    categoryList.getSelectionModel().select(previous);

                } else if (!categoryChoices.isEmpty()) {

                    categoryList.getSelectionModel().selectFirst();
                }

            } catch (Exception e) {
                showError(tr("language.error.category"), e);
            }
        };


        Button saveCategoryButton =
                new Button(tr("language.save.category"));

        saveCategoryButton.setOnAction(event -> {

            String oldName =
                    categoryList.getSelectionModel().getSelectedItem();

            String newName =
                    editCategoryName.getText().trim();

            if (oldName == null) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.edit.category")
                );
                return;
            }

            if (newName.isEmpty()) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        tr("language.write.newname.category")
                );
                return;
            }

            if (oldName.equals(newName)) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.confirm.equals.category")
                );
                return;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    tr("language.confirm.edit.category") + oldName + tr("language.confirm.edit.category0")
                            + newName + tr("language.confirm.edit.category1"),
                    ButtonType.YES,
                    ButtonType.NO
            );

            confirm.initOwner(editStage);
            confirm.setHeaderText(tr("language.confirm.deleting.category"));

            if (confirm.showAndWait().orElse(ButtonType.NO)
                    != ButtonType.YES) {
                return;
            }

            try {
                repository.renameCategory(oldName, newName);

                // メイン画面のカテゴリ一覧を更新
                loadCategories();

                if (categoryBox.getItems().contains(newName)) {
                    categoryBox.setValue(newName);
                }

                // 検索用カテゴリ欄を更新
                String oldSearchValue = searchCategoryBox.getValue();

                searchCategoryBox.getItems().setAll(
                        repository.findAllCategories()
                );

                if (oldName.equals(oldSearchValue)) {
                    searchCategoryBox.setValue(newName);
                }

                // 追加検索条件のカテゴリ欄を更新
                List<String> updatedCategories =
                        repository.findAllCategories();

                for (SearchConditionRow row :
                        extraSearchConditions) {

                    String oldValue = row.categoryBox.getValue();

                    row.categoryBox.getItems().setAll(
                            updatedCategories
                    );

                    if (oldName.equals(oldValue)) {
                        row.categoryBox.setValue(newName);
                    }
                }

                // 商品編集用カテゴリも更新
                String oldEditValue = editCategory.getValue();

                editCategory.getItems().setAll(
                        updatedCategories
                );

                if (oldName.equals(oldEditValue)) {
                    editCategory.setValue(newName);
                }

                // タグ一覧を再読み込み
                categoryChoices.setAll(updatedCategories);
                categoryList.getSelectionModel().select(newName);
                editCategoryName.setText(newName);

                // メイン画面の商品明細も更新
                refreshItemsForSelectedReceipt();

                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.edited.category")
                );

            } catch (IllegalArgumentException e) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.WARNING,
                        e.getMessage()
                );

            } catch (Exception e) {
                showEditWindowMessage(
                        editStage,
                        Alert.AlertType.ERROR,
                        tr("language.filededit.category")
                                + e.getMessage()
                );
            }
        });


        VBox categoryListPane = new VBox(
                8,
                new Label(tr("language.category.list")),
                categoryList
        );

        VBox categoryEditForm = new VBox(
                12,
                new Label(tr("language.editcategory")),
                editCategoryName,
                saveCategoryButton
        );

        categoryListPane.setPadding(new Insets(10));
        categoryEditForm.setPadding(new Insets(15));

        VBox.setVgrow(categoryList, Priority.ALWAYS);

        HBox categoryEditContent = new HBox(
                10,
                categoryListPane,
                categoryEditForm
        );

        categoryEditContent.setPadding(new Insets(10));


        reloadCategoryList.run();

        Tab categoryTab = new Tab(
                tr("language.categorytab"),
                categoryEditContent
        );

        categoryTab.setClosable(false);


        // ======================================
        // ウィンドウを表示
        // ======================================

        tabPane.getTabs().addAll(
                receiptTab,
                itemTab,
                categoryTab
        );

        tabPane.setTabClosingPolicy(
                TabPane.TabClosingPolicy.UNAVAILABLE
        );

        editStage.setScene(
                new Scene(tabPane, 1000, 520)
        );

        editStage.show();
    }


    // 編集ウィンドウ内のメッセージ表示
    private void showEditWindowMessage(
            Stage owner,
            Alert.AlertType type,
            String message) {

        Alert alert = new Alert(type);

        alert.initOwner(owner);
        alert.setTitle(tr("language.alert.setTitle.edit"));
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }

    // ======================================
// 削除ウィンドウ
// ======================================

    private void openDeleteWindow(
            Stage owner,
            ComboBox<String> searchCategoryBox,
            List<SearchConditionRow> extraSearchConditions) {

        Stage deleteStage = new Stage();
        deleteStage.initOwner(owner);
        deleteStage.initModality(
                javafx.stage.Modality.WINDOW_MODAL
        );
        deleteStage.setTitle(tr("language.alert.setTitle.delete"));

        TabPane tabPane = new TabPane();


        // ======================================
        // ① レシート削除
        // ======================================

        ObservableList<Receipt> receiptChoices =
                FXCollections.observableArrayList();

        ListView<Receipt> receiptList =
                new ListView<>(receiptChoices);

        receiptList.setPrefWidth(280);
        receiptList.setPrefHeight(350);

        receiptList.setCellFactory(listView ->
                new ListCell<Receipt>() {

                    @Override
                    protected void updateItem(
                            Receipt receipt,
                            boolean empty) {

                        super.updateItem(receipt, empty);

                        if (empty || receipt == null) {
                            setText(null);
                        } else {
                            setText(
                                    receipt.getPurchaseDate()
                                            + "  "
                                            + receipt.getStoreName()
                                            + "\n"
                                            + yenFormat.format(
                                            receipt.getTotalYen()
                                    )
                                            + tr("language.currency")
                            );
                        }
                    }
                }
        );

        // レシート一覧を読み込む
        Runnable reloadReceiptList = () -> {

            Receipt previous =
                    receiptList.getSelectionModel().getSelectedItem();

            int previousId =
                    previous == null ? -1 : previous.getId();

            try {
                receiptChoices.setAll(repository.findAll());

                boolean restored = false;

                for (Receipt receipt : receiptChoices) {

                    if (receipt.getId() == previousId) {
                        receiptList.getSelectionModel().select(receipt);
                        restored = true;
                        break;
                    }
                }

                if (!restored && !receiptChoices.isEmpty()) {
                    receiptList.getSelectionModel().selectFirst();
                }

            } catch (Exception e) {
                showError(tr("language.error.receipt"), e);
            }
        };


        Button deleteReceiptButton =
                new Button(tr("language.delete.receipt"));

        deleteReceiptButton.setOnAction(event -> {

            Receipt selected =
                    receiptList.getSelectionModel().getSelectedItem();

            if (selected == null) {
                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.delete.receipt0")
                );
                return;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    tr("language.confirm.delete.receipt") + selected.getStoreName()
                            + tr("language.confirm.delete.receipt0"),
                    ButtonType.YES,
                    ButtonType.NO
            );

            confirm.initOwner(deleteStage);
            confirm.setHeaderText(tr("language.confirm.deleting.receipt"));

            if (confirm.showAndWait().orElse(ButtonType.NO)
                    != ButtonType.YES) {
                return;
            }

            try {
                repository.delete(selected.getId());

                // メイン画面の選択を解除して一覧を更新
                receiptTable.getSelectionModel().clearSelection();
                refreshReceipts();

                if (!receipts.isEmpty()) {
                    receiptTable.getSelectionModel().selectFirst();
                } else {
                    refreshItemsForSelectedReceipt();
                }

                reloadReceiptList.run();

                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.deleted.receipt")
                );

            } catch (Exception e) {
                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.ERROR,
                        tr("language.fileddelete.receipt")
                                + e.getMessage()
                );
            }
        });


        VBox receiptTabContent = new VBox(
                10,
                new Label(tr("language.deletereceiptbutton")),
                receiptList,
                deleteReceiptButton
        );

        receiptTabContent.setPadding(new Insets(12));
        VBox.setVgrow(receiptList, Priority.ALWAYS);

        reloadReceiptList.run();

        Tab receiptTab = new Tab(
                tr("language.item.receipt"),
                receiptTabContent
        );

        receiptTab.setClosable(false);


        // ======================================
        // ② 商品削除
        // ======================================

        ObservableList<Receipt> itemReceiptChoices =
                FXCollections.observableArrayList();

        ListView<Receipt> itemReceiptList =
                new ListView<>(itemReceiptChoices);

        itemReceiptList.setPrefWidth(230);

        itemReceiptList.setCellFactory(listView ->
                new ListCell<Receipt>() {

                    @Override
                    protected void updateItem(
                            Receipt receipt,
                            boolean empty) {

                        super.updateItem(receipt, empty);

                        if (empty || receipt == null) {
                            setText(null);
                        } else {
                            setText(
                                    receipt.getPurchaseDate()
                                            + "\n"
                                            + receipt.getStoreName()
                                            + " / "
                                            + yenFormat.format(
                                            receipt.getTotalYen()
                                    )
                                            + tr("language.currency")
                            );
                        }
                    }
                }
        );


        // 選択レシートの商品一覧

        ObservableList<ReceiptItem> itemChoices =
                FXCollections.observableArrayList();

        ListView<ReceiptItem> itemList =
                new ListView<>(itemChoices);

        itemList.setPrefWidth(250);

        itemList.setCellFactory(listView ->
                new ListCell<ReceiptItem>() {

                    @Override
                    protected void updateItem(
                            ReceiptItem item,
                            boolean empty) {

                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(
                                    item.getItemName()
                                            + tr("language.quantity")
                                            + item.getQuantity()
                                            + tr("language.subtotal")
                                            + yenFormat.format(
                                            item.getSubtotalYen()
                                    )
                                            + tr("language.currency")
                                            + tr("language.category")
                                            + (item.getCategoryName() == null
                                            ? tr("language.uncategorized")
                                            : item.getCategoryName())
                            );
                        }
                    }
                }
        );


        // レシートを選択すると商品一覧を更新

        itemReceiptList.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldReceipt, newReceipt) -> {

                    itemChoices.clear();
                    itemList.getSelectionModel().clearSelection();

                    if (newReceipt == null) {
                        return;
                    }

                    try {
                        itemChoices.setAll(
                                repository.findItemsByReceiptId(
                                        newReceipt.getId()
                                )
                        );

                    } catch (Exception e) {
                        showError(
                                tr("language.error.item"),
                                e
                        );
                    }
                });


        // レシート一覧を再読み込み

        Runnable reloadItemReceiptList = () -> {

            Receipt previous =
                    itemReceiptList.getSelectionModel()
                            .getSelectedItem();

            int previousId =
                    previous == null ? -1 : previous.getId();

            try {
                itemReceiptChoices.setAll(
                        repository.findAll()
                );

                boolean restored = false;

                for (Receipt receipt : itemReceiptChoices) {

                    if (receipt.getId() == previousId) {
                        itemReceiptList.getSelectionModel()
                                .select(receipt);
                        restored = true;
                        break;
                    }
                }

                if (!restored && !itemReceiptChoices.isEmpty()) {
                    itemReceiptList.getSelectionModel()
                            .selectFirst();
                }

            } catch (Exception e) {
                showError(
                        tr("language.error.receipt"),
                        e
                );
            }
        };


        Button deleteItemButton =
                new Button(tr("language.delete.item"));

        deleteItemButton.setOnAction(event -> {

            Receipt selectedReceipt =
                    itemReceiptList.getSelectionModel()
                            .getSelectedItem();

            ReceiptItem selectedItem =
                    itemList.getSelectionModel()
                            .getSelectedItem();

            if (selectedReceipt == null || selectedItem == null) {
                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.delete.receipt")
                );
                return;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    tr("language.confirm.delete.item") + selectedItem.getItemName()
                            + tr("language.confirm.delete.alert"),
                    ButtonType.YES,
                    ButtonType.NO
            );

            confirm.initOwner(deleteStage);
            confirm.setHeaderText(tr("language.confirm.deleting.item"));

            if (confirm.showAndWait().orElse(ButtonType.NO)
                    != ButtonType.YES) {
                return;
            }

            try {
                int receiptId = selectedReceipt.getId();

                // 商品を削除
                repository.deleteItem(selectedItem.getId());

                // レシート合計を再計算
                repository.updateTotalFromItems(receiptId);

                // メイン画面のレシート一覧を更新
                refreshReceipts();

                for (Receipt receipt : receipts) {
                    if (receipt.getId() == receiptId) {
                        receiptTable.getSelectionModel()
                                .select(receipt);
                        break;
                    }
                }

                refreshItemsForSelectedReceipt();

                // 削除ウィンドウの商品一覧も更新
                itemChoices.setAll(
                        repository.findItemsByReceiptId(receiptId)
                );

                itemList.getSelectionModel().clearSelection();

                // 左側のレシート一覧も更新
                reloadItemReceiptList.run();

                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.deleted.item")
                );

            } catch (Exception e) {
                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.ERROR,
                        tr("languadelete.item")
                                + e.getMessage()
                );
            }
        });


        VBox itemReceiptPane = new VBox(
                8,
                new Label(tr("language.receipt.list")),
                itemReceiptList
        );

        VBox itemListPane = new VBox(
                8,
                new Label(tr("language.item.list")),
                itemList
        );

        VBox itemDeletePane = new VBox(
                12,
                new Label(tr("language.delete.item")),
                new Label(tr("language.select.delete.item")),
                deleteItemButton
        );

        itemReceiptPane.setPadding(new Insets(8));
        itemListPane.setPadding(new Insets(8));
        itemDeletePane.setPadding(new Insets(12));

        VBox.setVgrow(itemReceiptList, Priority.ALWAYS);
        VBox.setVgrow(itemList, Priority.ALWAYS);

        HBox itemTabContent = new HBox(
                8,
                itemReceiptPane,
                itemListPane,
                itemDeletePane
        );

        itemTabContent.setPadding(new Insets(8));

        reloadItemReceiptList.run();

        Tab itemTab = new Tab(
                tr("language.item.tab"),
                itemTabContent
        );

        itemTab.setClosable(false);


        // ======================================
        // ③ タグ削除
        // ======================================

        ObservableList<String> categoryChoices =
                FXCollections.observableArrayList();

        ListView<String> categoryList =
                new ListView<>(categoryChoices);

        categoryList.setPrefWidth(280);
        categoryList.setPrefHeight(350);


        Runnable reloadCategoryList = () -> {

            String previous =
                    categoryList.getSelectionModel().getSelectedItem();

            try {
                categoryChoices.setAll(
                        repository.findAllCategories()
                );

                if (previous != null &&
                        categoryChoices.contains(previous)) {

                    categoryList.getSelectionModel().select(previous);

                } else if (!categoryChoices.isEmpty()) {

                    categoryList.getSelectionModel().selectFirst();
                }

            } catch (Exception e) {
                showError(tr("language.error.category"), e);
            }
        };


        Button deleteCategoryButton =
                new Button(tr("language.delete.category"));

        deleteCategoryButton.setOnAction(event -> {

            String selectedCategory =
                    categoryList.getSelectionModel().getSelectedItem();

            if (selectedCategory == null) {
                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.WARNING,
                        tr("language.select.delete.category")
                );
                return;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    tr("language.confirm.delete.category") + selectedCategory + tr("language.confirm.delete.alert"),
                    ButtonType.YES,
                    ButtonType.NO
            );

            confirm.initOwner(deleteStage);
            confirm.setHeaderText(tr("language.confirm.deleting.category"));

            if (confirm.showAndWait().orElse(ButtonType.NO)
                    != ButtonType.YES) {
                return;
            }

            try {
                repository.deleteCategory(selectedCategory);

                // メイン画面のカテゴリ一覧を更新
                loadCategories();

                // 検索用カテゴリ一覧を更新
                List<String> updatedCategories =
                        repository.findAllCategories();

                searchCategoryBox.getItems().setAll(
                        updatedCategories
                );

                if (selectedCategory.equals(searchCategoryBox.getValue())) {
                    searchCategoryBox.setValue(null);
                }

                // 追加検索条件のカテゴリ一覧も更新
                for (SearchConditionRow row :
                        extraSearchConditions) {

                    String previous = row.categoryBox.getValue();

                    row.categoryBox.getItems().setAll(
                            updatedCategories
                    );

                    if (selectedCategory.equals(previous)) {
                        row.categoryBox.setValue(null);
                    }
                }

                // メイン画面の商品明細を更新
                refreshItemsForSelectedReceipt();

                // 削除ウィンドウのタグ一覧を更新
                reloadCategoryList.run();

                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.INFORMATION,
                        tr("language.deleted.category")
                );

            } catch (IllegalArgumentException e) {

                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.WARNING,
                        e.getMessage()
                );

            } catch (Exception e) {

                showDeleteWindowMessage(
                        deleteStage,
                        Alert.AlertType.ERROR,
                        tr("language.fileddelete.category")
                                + e.getMessage()
                );
            }
        });


        VBox categoryTabContent = new VBox(
                10,
                new Label(tr("language.deletecategorybutton")),
                categoryList,
                deleteCategoryButton
        );

        categoryTabContent.setPadding(new Insets(12));
        VBox.setVgrow(categoryList, Priority.ALWAYS);

        reloadCategoryList.run();

        Tab categoryTab = new Tab(
                tr("language.categorytab"),
                categoryTabContent
        );

        categoryTab.setClosable(false);


        // ======================================
        // ウィンドウを表示
        // ======================================

        tabPane.getTabs().addAll(
                receiptTab,
                itemTab,
                categoryTab
        );

        tabPane.setTabClosingPolicy(
                TabPane.TabClosingPolicy.UNAVAILABLE
        );

        deleteStage.setScene(
                new Scene(tabPane, 950, 500)
        );

        deleteStage.show();
    }


    // 削除ウィンドウ内のメッセージ表示
    private void showDeleteWindowMessage(
            Stage owner,
            Alert.AlertType type,
            String message) {

        Alert alert = new Alert(type);

        alert.initOwner(owner);
        alert.setTitle(tr("language.alert.setTitle"));
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }

    // ==============================
// 操作バーの位置を変更
// ==============================

    // ==============================
// 操作バーの位置を変更
// ==============================

    // ==============================
// 操作バーの位置を変更
// ==============================

    // ==============================
// 操作バーの位置を変更
// ==============================

    private void restoreUiSettings(
            BorderPane layout,
            ToolBar bar,
            Map<String, Button> mainScreenButtons,
            Map<String, javafx.scene.Node> mainScreenSections) {

        // 保存されている操作バーの位置を取得
        String savedPosition =
                uiPreferences.get("toolbar.position", "上");

        // 不正な値なら上に戻す
        if (!savedPosition.equals("上")
                && !savedPosition.equals("下")
                && !savedPosition.equals("左")
                && !savedPosition.equals("右")) {

            savedPosition = "上";
        }

        // 操作バーの位置を復元
        setToolbarPosition(layout, bar, savedPosition);

        // ボタンの表示状態を復元
        for (Map.Entry<String, Button> entry :
                mainScreenButtons.entrySet()) {

            String buttonName = entry.getKey();
            Button button = entry.getValue();

            boolean visible = uiPreferences.getBoolean(
                    "button.visible." + buttonName,
                    true
            );

            button.setVisible(visible);
            button.setManaged(visible);
        }

        // 入力欄の表示状態を復元
        for (Map.Entry<String, javafx.scene.Node> entry :
                mainScreenSections.entrySet()) {

            String sectionName = entry.getKey();
            javafx.scene.Node section = entry.getValue();

            boolean visible = uiPreferences.getBoolean(
                    "section.visible." + sectionName,
                    true
            );

            section.setVisible(visible);
            section.setManaged(visible);
        }
    }

    private void setToolbarPosition(
            BorderPane layout,
            ToolBar bar,
            String position) {

        // 現在の配置から操作バーを外す
        if (layout.getTop() == bar) {
            layout.setTop(null);
        }

        if (layout.getBottom() == bar) {
            layout.setBottom(null);
        }

        if (layout.getLeft() == bar) {
            layout.setLeft(null);
        }

        if (layout.getRight() == bar) {
            layout.setRight(null);
        }

        // 左右なら縦向き、上下なら横向き
        if ("左".equals(position) || "右".equals(position)) {

            bar.setOrientation(
                    javafx.geometry.Orientation.VERTICAL
            );

        } else {

            bar.setOrientation(
                    javafx.geometry.Orientation.HORIZONTAL
            );
        }

        // 選択された位置に配置
        switch (position) {

            case "左" ->
                    layout.setLeft(bar);

            case "右" ->
                    layout.setRight(bar);

            case "下" ->
                    layout.setBottom(bar);

            default ->
                    layout.setTop(bar);
        }

        // 操作バーの位置を保存
        saveUiSetting("toolbar.position", position);
    }

    private String getToolbarPosition(
            BorderPane layout,
            ToolBar bar) {

        if (layout.getLeft() == bar) {
            return "左";
        }

        if (layout.getRight() == bar) {
            return "右";
        }

        if (layout.getBottom() == bar) {
            return "下";
        }

        return "上";
    }

    // ==============================
// 操作バー設定ウィンドウ
// ==============================

    private void openToolbarSettings(
            Stage owner,
            BorderPane layout,
            ToolBar bar,
            Map<String, Button> mainScreenButtons,
            Map<String, javafx.scene.Node> mainScreenSections) {

        Stage settingsStage = new Stage();

        // ==============================
// 言語選択
// ==============================

        Label languageLabel =
                new Label(tr("settings.language"));

        ComboBox<String> languageBox =
                new ComboBox<>();

        languageBox.getItems().addAll(
                tr("language.japanese"),
                tr("language.english")
        );

// 現在の言語を選択
        if ("en".equals(currentLocale.getLanguage())) {

            languageBox.setValue(
                    tr("language.english")
            );

        } else {

            languageBox.setValue(
                    tr("language.japanese")
            );
        }

// 言語変更
        languageBox.setOnAction(event -> {

            String selectedLanguage =
                    languageBox.getValue();

            String languageCode =
                    selectedLanguage.equals(
                            tr("language.english")
                    ) ? "en" : "ja";

            // 変更がない場合は何もしない
            if (languageCode.equals(
                    currentLocale.getLanguage())) {
                return;
            }

            // 言語を保存
            saveUiSetting("language", languageCode);

            // 再起動を案内
            Alert alert = new Alert(
                    Alert.AlertType.INFORMATION
            );

            alert.initOwner(settingsStage);

            alert.setTitle(
                    tr("language.restart.title")
            );

            alert.setHeaderText(null);

            alert.setContentText(
                    tr("language.restart.message")
            );

            alert.showAndWait();
        });

        settingsStage.initOwner(owner);

        settingsStage.initModality(
                javafx.stage.Modality.WINDOW_MODAL
        );

        settingsStage.setTitle("settings.title");

        // 位置選択
        Label positionLabel =
                new Label(tr("settings.toolbar.position"));

        ToggleGroup positionGroup =
                new ToggleGroup();

        RadioButton leftButton =
                new RadioButton(tr("position.left"));

        RadioButton rightButton =
                new RadioButton(tr("position.right"));

        RadioButton topButton =
                new RadioButton(tr("position.top"));

        RadioButton bottomButton =
                new RadioButton(tr("position.bottom"));

        leftButton.setToggleGroup(positionGroup);
        rightButton.setToggleGroup(positionGroup);
        topButton.setToggleGroup(positionGroup);
        bottomButton.setToggleGroup(positionGroup);

        // 現在の位置を取得して選択状態にする
        String currentPosition =
                getToolbarPosition(layout, bar);

        switch (currentPosition) {

            case "左" ->
                    leftButton.setSelected(true);

            case "右" ->
                    rightButton.setSelected(true);

            case "下" ->
                    bottomButton.setSelected(true);

            default ->
                    topButton.setSelected(true);
        }

        // 位置を変更する処理
        leftButton.setOnAction(event ->
                setToolbarPosition(layout, bar, "左")
        );

        rightButton.setOnAction(event ->
                setToolbarPosition(layout, bar, "右")
        );

        topButton.setOnAction(event ->
                setToolbarPosition(layout, bar, "上")
        );

        bottomButton.setOnAction(event ->
                setToolbarPosition(layout, bar, "下")
        );

        HBox positionButtons = new HBox(
                15,
                leftButton,
                rightButton,
                topButton,
                bottomButton
        );

        // ==============================
// メイン画面のボタン表示設定
// ==============================

        Label buttonVisibilityLabel =
                new Label(tr("settings.button.visibility"));

        VBox buttonVisibilityBox = new VBox(8);

// 登録されているボタンごとにチェックボックスを作成
        for (Map.Entry<String, Button> entry :
                mainScreenButtons.entrySet()) {

            String buttonName = entry.getKey();
            Button targetButton = entry.getValue();

            CheckBox checkBox = new CheckBox(buttonName);

            // 現在の表示状態を反映
            checkBox.setSelected(targetButton.isVisible());

            // チェック状態が変更されたとき
            checkBox.setOnAction(event -> {

                boolean visible = checkBox.isSelected();

                targetButton.setVisible(visible);
                targetButton.setManaged(visible);

                // 表示状態を保存
                saveUiSetting(
                        "button.visible." + buttonName,
                        visible
                );
            });

            buttonVisibilityBox.getChildren().add(checkBox);
        }

// ボタンが増えてもスクロールできるようにする
        ScrollPane buttonScrollPane =
                new ScrollPane(buttonVisibilityBox);

        // ==============================
// メイン画面の入力欄表示設定
// ==============================

        Label sectionVisibilityLabel =
                new Label(tr("settings.section.visibility"));

        VBox sectionVisibilityBox = new VBox(8);

// 入力欄ごとにチェックボックスを作成
        for (Map.Entry<String, javafx.scene.Node> entry :
                mainScreenSections.entrySet()) {

            String sectionName = entry.getKey();

            javafx.scene.Node targetSection =
                    entry.getValue();

            CheckBox checkBox =
                    new CheckBox(sectionName);

            // 現在の表示状態を反映
            checkBox.setSelected(
                    targetSection.isVisible()
            );

            // チェック状態が変更されたとき
            checkBox.setOnAction(event -> {

                boolean visible = checkBox.isSelected();

                targetSection.setVisible(visible);
                targetSection.setManaged(visible);

                // 入力欄の表示状態を保存
                saveUiSetting(
                        "section.visible." + sectionName,
                        visible
                );
            });

            sectionVisibilityBox.getChildren().add(checkBox);
        }

// スクロールできるようにする
        ScrollPane sectionScrollPane =
                new ScrollPane(sectionVisibilityBox);

        sectionScrollPane.setFitToWidth(true);
        sectionScrollPane.setPrefViewportHeight(120);

        buttonScrollPane.setFitToWidth(true);
        buttonScrollPane.setPrefViewportHeight(260);

        VBox content = new VBox(
                15,

                // 言語設定
                languageLabel,
                languageBox,

                new Separator(),

                // 操作バーの位置
                positionLabel,
                positionButtons,

                new Separator(),

                // ボタンの表示設定
                buttonVisibilityLabel,
                buttonScrollPane,

                new Separator(),

                // 入力欄の表示設定
                sectionVisibilityLabel,
                sectionScrollPane
        );

        content.setPadding(new Insets(15));

        content.setPadding(
                new Insets(20)
        );

        settingsStage.setScene(
                new Scene(content, 460, 650)
        );

        settingsStage.show();
    }

    // ==============================
// 設定保存
// ==============================

    private void saveUiSetting(String key, String value) {

        uiPreferences.put(key, value);

        try {
            uiPreferences.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    private void saveUiSetting(String key, boolean value) {

        uiPreferences.putBoolean(key, value);

        try {
            uiPreferences.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    // SplitPaneの仕切り位置を復元し、変更時に保存する
    private void restoreSplitPaneDivider(
            SplitPane splitPane,
            String preferenceKey,
            double defaultPosition
    ) {
        double savedPosition = defaultPosition;

        String savedValue = uiPreferences.get(
                preferenceKey,
                Double.toString(defaultPosition)
        );

        try {
            savedPosition = Double.parseDouble(savedValue);
        } catch (NumberFormatException e) {
            savedPosition = defaultPosition;
        }

        // 仕切りが端に寄りすぎないようにする
        savedPosition = Math.max(0.05, Math.min(0.95, savedPosition));

        // 保存されていた位置を復元する
        splitPane.setDividerPositions(savedPosition);

        // 仕切りを動かしたときに新しい位置を保存する
        splitPane.getDividers().get(0).positionProperty().addListener(
                (observable, oldValue, newValue) -> {
                    uiPreferences.put(
                            preferenceKey,
                            Double.toString(newValue.doubleValue())
                    );
                }
        );
    }


    // TableViewの列幅を復元し、変更時に保存する
    private void restoreTableColumnWidths(
            TableView<?> table,
            String preferencePrefix
    ) {
        for (int i = 0; i < table.getColumns().size(); i++) {

            TableColumn<?, ?> column = table.getColumns().get(i);

            // 列番号を使って保存先を区別する
            String preferenceKey = preferencePrefix + "." + i;

            // 保存されている列幅を取得する
            String savedWidth = uiPreferences.get(preferenceKey, "");

            if (!savedWidth.isEmpty()) {
                try {
                    double width = Double.parseDouble(savedWidth);

                    if (width >= 30 && width <= 3000) {
                        column.setPrefWidth(width);
                    }

                } catch (NumberFormatException e) {
                    // 保存値が不正な場合は初期幅を使用する
                }
            }

            // 列幅が変更されたら保存する
            column.widthProperty().addListener(
                    (observable, oldValue, newValue) -> {
                        double width = newValue.doubleValue();

                        if (width > 0) {
                            uiPreferences.put(
                                    preferenceKey,
                                    Double.toString(width)
                            );
                        }
                    }
            );
        }
    }

    // 翻訳ファイルの基本名
    private static final String MESSAGE_BASE_NAME =
            "com.bassstarling.receiptmanager.messages";

    // 現在使用している言語
    private Locale currentLocale = Locale.JAPANESE;

    // 翻訳データ
    private ResourceBundle messages;

    // ==============================
// 言語の読み込み
// ==============================

    private void loadLanguage() {

        // 保存された言語を取得
        String language =
                uiPreferences.get("language", "ja");

        currentLocale = Locale.forLanguageTag(language);

        // 対応していない言語なら日本語に戻す
        if (!currentLocale.getLanguage().equals("ja")
                && !currentLocale.getLanguage().equals("en")) {

            currentLocale = Locale.JAPANESE;
        }

        // 翻訳ファイルを読み込む
        messages = ResourceBundle.getBundle(
                MESSAGE_BASE_NAME,
                currentLocale
        );
    }

    // ==============================
// 翻訳文字列の取得
// ==============================

    private String tr(String key) {

        try {
            return messages.getString(key);

        } catch (java.util.MissingResourceException e) {

            // 翻訳キーが存在しない場合
            System.err.println(
                    tr("language.cantfind.key") + key
            );

            return "[" + key + "]";
        }
    }

    @Override
    public void stop() {
        try {
            uiPreferences.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }
}