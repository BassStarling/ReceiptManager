package com.bassstarling.receiptmanager;

import java.time.LocalDate;

public class Receipt {

    private final int id;
    private final LocalDate purchaseDate;
    private final String storeName;
    private final long totalYen;

    public Receipt(
            int id,
            LocalDate purchaseDate,
            String storeName,
            long totalYen
    ) {
        this.id = id;
        this.purchaseDate = purchaseDate;
        this.storeName = storeName;
        this.totalYen = totalYen;
    }

    public int getId() {
        return id;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public String getStoreName() {
        return storeName;
    }

    public long getTotalYen() {
        return totalYen;
    }
}