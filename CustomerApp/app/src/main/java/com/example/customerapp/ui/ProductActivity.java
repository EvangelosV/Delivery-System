package com.example.customerapp.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.customerapp.R;
import com.example.customerapp.adapter.CartAdapter;
import com.example.customerapp.adapter.ProductAdapter;
import com.example.customerapp.manager.CartManager;
import com.example.customerapp.model.CartItem;
import com.example.customerapp.model.ProductItem;
import com.example.customerapp.network.CustomerClient;

import java.util.ArrayList;
import java.util.List;

public class ProductActivity extends AppCompatActivity {

    private final List<ProductItem> products = new ArrayList<>();
    private ProductAdapter adapter;
    private ProgressBar progress;
    private String currentStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.product_results);

        currentStore = getIntent().getStringExtra("store");
        setTitle(currentStore);

        RecyclerView rv = findViewById(R.id.rvProducts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter(products, this::showAddToCartDialog);
        rv.setAdapter(adapter);

        // show loading spinner
        progress = new ProgressBar(this);
        ((LinearLayout) rv.getParent()).addView(progress);
        progress.setVisibility(View.VISIBLE);

        // load products from server
        CustomerClient.getInstance().getProducts(currentStore, new CustomerClient.Callback<List<ProductItem>>() {
            @Override
            public void onSuccess(List<ProductItem> data) {
                progress.setVisibility(View.GONE);
                products.clear();
                products.addAll(data);
                adapter.notifyDataSetChanged();
            }
            @Override
            public void onError(Exception e) {
                progress.setVisibility(View.GONE);
                Toast.makeText(ProductActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.cart_button).setOnClickListener(v -> showCartDialog());
    }

    private void showAddToCartDialog(ProductItem p) {
        View pop = LayoutInflater.from(this)
                .inflate(R.layout.popup_add_to_cart, null);
        TextView tvName   = pop.findViewById(R.id.tv_product_name);
        TextView tvStock  = pop.findViewById(R.id.tv_stock_quantity);
        TextView tvQty    = pop.findViewById(R.id.tv_quantity);
        Button  btnDec    = pop.findViewById(R.id.btn_decrease);
        Button  btnInc    = pop.findViewById(R.id.btn_increase);
        Button  btnAdd    = pop.findViewById(R.id.btn_add_to_cart);
        ImageButton btnClose = pop.findViewById(R.id.btn_close_popup);

        tvName.setText(p.getProductName());
        tvStock.setText("Stock: " + p.getAvailableAmount());
        final int[] qty = {1};
        tvQty.setText(String.valueOf(qty[0]));

        btnDec.setOnClickListener(v -> {
            if (qty[0] > 1) {
                qty[0]--;
                tvQty.setText(String.valueOf(qty[0]));
            }
        });
        btnInc.setOnClickListener(v -> {
            if (qty[0] < p.getAvailableAmount()) {
                qty[0]++;
                tvQty.setText(String.valueOf(qty[0]));
            }
        });

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(pop)
                .create();

        btnClose.setOnClickListener(v -> dlg.dismiss());
        btnAdd.setOnClickListener(v -> {
            CartManager.getInstance().addItem(
                    new CartItem(currentStore, p.getProductName(), qty[0], p.getPrice())
            );
            Toast.makeText(this,
                    "Added " + qty[0] + "× " + p.getProductName() + " to cart",
                    Toast.LENGTH_SHORT).show();
            dlg.dismiss();
        });

        dlg.show();
    }

    private void showCartDialog() {
        View pop = LayoutInflater.from(this)
                .inflate(R.layout.popup_cart, null);
        RecyclerView rvCart     = pop.findViewById(R.id.rv_cart_items);
        Button      btnCheckout = pop.findViewById(R.id.btn_checkout);
        ImageButton btnClose    = pop.findViewById(R.id.btn_close_cart);
        Button      btnEmpty    = pop.findViewById(R.id.btn_empty_cart);

        List<CartItem> items = CartManager.getInstance().getCartItems();
        rvCart.setLayoutManager(new LinearLayoutManager(this));
        rvCart.setAdapter(new CartAdapter(items));

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(pop)
                .create();

        btnClose.setOnClickListener(v -> dlg.dismiss());
        btnCheckout.setOnClickListener(v -> {
            CustomerClient.getInstance().checkout(
                    items,
                    new CustomerClient.Callback<String>() {
                        @Override public void onSuccess(String resp) {
                            runOnUiThread(() -> {
                                adapter.notifyDataSetChanged();// refresh the RecyclerView
                                Toast.makeText(ProductActivity.this, resp, Toast.LENGTH_LONG).show();
                                CartManager.getInstance().clearCart();
                                dlg.dismiss();
                            });
                        }
                        @Override public void onError(Exception e) {
                            runOnUiThread(() -> Toast.makeText(ProductActivity.this,
                                    "Checkout failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show()
                            );
                        }
                    }
            );
        });

        btnEmpty.setOnClickListener(v -> {
            CartManager.getInstance().clearCart();
            items.clear();                // clear the local list too
            adapter.notifyDataSetChanged();// refresh the RecyclerView
            btnCheckout.setEnabled(false);// disable checkout
            Toast.makeText(this,
                    "Cart emptied",
                    Toast.LENGTH_SHORT).show();
        });

        if (items.isEmpty()) {
            btnCheckout.setEnabled(false);
            TextView tvEmpty = pop.findViewById(R.id.btn_empty_cart);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            btnCheckout.setEnabled(true);
        }
        dlg.show();
    }
}
