package Objects;

import java.io.Serializable;

public class Product implements Serializable {
    private static final long serialVersionUID = 1L;
    private String productName;
    private String productType;
    private int availableAmount;
    private double price;
    private Store store;
    private Boolean visible = true;

    // Constructors
    public Product() {
    }

    public Product(String productName, String productType, int availableAmount, double price) {
        this.productName = productName;
        this.productType = productType;
        this.availableAmount = availableAmount;
        this.price = price;
    }

    // Getters and Setters
    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public int getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(int availableAmount) {
        this.availableAmount = availableAmount;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    // Utility methods
    public boolean reduceStock(int quantity) {
        if (quantity <= 0) return false;
        if (availableAmount >= quantity) {
            availableAmount -= quantity;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s - %s - Price: %.2f - Available: %d",
                productName, productType, price, availableAmount);
    }
}