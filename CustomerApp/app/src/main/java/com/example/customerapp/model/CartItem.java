package com.example.customerapp.model;

import java.io.Serializable;

public class CartItem implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String storeName;
    public final String productName;
    public int quantity;
    public final double priceAtAddTime;

    public CartItem(String storeName, String productName, int quantity, double priceAtAddTime) {
        this.storeName       = storeName;
        this.productName     = productName;
        this.quantity        = quantity;
        this.priceAtAddTime  = priceAtAddTime;
    }
}
