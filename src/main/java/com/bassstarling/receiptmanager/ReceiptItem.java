package com.bassstarling.receiptmanager;

public class ReceiptItem {

    private final int id;
    private final int receiptId;
    private final String itemName;
    private final double quantity;
    private final long unitPriceYen;
    private final String categoryName;

    public ReceiptItem(
            int id,
            int receiptId,
            String itemName,
            double quantity,
            long unitPriceYen,
            String categoryName
    ) {
        this.id = id;
        this.receiptId = receiptId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPriceYen = unitPriceYen;
        this.categoryName = categoryName;
    }

    public int getId() {
        return id;
    }

    public int getReceiptId() {
        return receiptId;
    }

    public String getItemName() {
        return itemName;
    }

    public double getQuantity() {
        return quantity;
    }

    public long getUnitPriceYen() {
        return unitPriceYen;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public long getSubtotalYen() {
        return Math.round(quantity * unitPriceYen);
    }
}