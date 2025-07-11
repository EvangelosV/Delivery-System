package com.example.customerapp.model;

public class ProductItem {
    private final String productName;
    private final int    availableAmount;
    private final double price;

    public ProductItem(String productName, int availableAmount, double price) {
        this.productName     = productName;
        this.availableAmount = availableAmount;
        this.price           = price;
    }

    public String getProductName()   { return productName;    }
    public int    getAvailableAmount(){ return availableAmount; }
    public double getPrice()         { return price;          }
}
