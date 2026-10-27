package model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import util.Formats;

public class InventoryItem {

    private int itemId;
    private String itemName;
    private String category;
    private double unitPrice;
    private double unitCost;
    private int stockQuantity;
    private int reorderLevel;
    private LocalDate expiryDate;
    private LocalDateTime updatedAt;

    public InventoryItem() {
    }

    public InventoryItem(int itemId, String itemName, String category, double unitPrice, int stockQuantity,
                         int reorderLevel, LocalDate expiryDate, LocalDateTime updatedAt) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.category = category;
        this.unitPrice = unitPrice;
        this.stockQuantity = stockQuantity;
        this.reorderLevel = reorderLevel;
        this.expiryDate = expiryDate;
        this.updatedAt = updatedAt;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public double getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(double unitCost) {
        this.unitCost = unitCost;
    }

    public String getCostText() {
        return Formats.money(unitCost);
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isLowStock() {
        return stockQuantity <= reorderLevel;
    }

    public String getPriceText() {
        return Formats.money(unitPrice);
    }

    public String getStockText() {
        return isLowStock() ? stockQuantity + " " + "(low)" : String.valueOf(stockQuantity);
    }

    @Override
    public String toString() {
        return itemName + "  (stock " + stockQuantity + ", " + Formats.money(unitPrice) + ")";
    }
}
