package com.example.customerapp.manager;

import com.example.customerapp.model.CartItem;
import java.util.ArrayList;
import java.util.List;

public class CartManager {
    private static CartManager instance;
    private final List<CartItem> cartItems = new ArrayList<>();

    private CartManager() {}

    public static synchronized CartManager getInstance() {
        if (instance == null) instance = new CartManager();
        return instance;
    }

    public void addItem(CartItem item) {
        for (CartItem ci : cartItems) {
            if (ci.storeName.equals(item.storeName)
                    && ci.productName.equals(item.productName)) {
                ci.quantity += item.quantity;
                return;
            }
        }
        cartItems.add(item);
    }

    public List<CartItem> getCartItems() {
        return new ArrayList<>(cartItems);
    }

    public void clearCart() {
        cartItems.clear();
    }
}
