package com.example.customerapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.customerapp.R;
import com.example.customerapp.model.CartItem;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private final List<CartItem> items;

    public CartAdapter(List<CartItem> items) {
        this.items = items;
    }

    @NonNull @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cart_item, parent, false);
        return new CartViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem ci = items.get(position);
        holder.tvName.setText(ci.productName);
        holder.tvQty.setText("Qty: " + ci.quantity);
        double total = ci.priceAtAddTime * ci.quantity;
        holder.tvPrice.setText(String.format("%.2f €", total));
    }

    @Override public int getItemCount() { return items.size(); }

    static class CartViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvQty, tvPrice;
        CartViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName  = itemView.findViewById(R.id.textCartProduct);
            tvQty   = itemView.findViewById(R.id.textCartQty);
            tvPrice = itemView.findViewById(R.id.textCartPrice);
        }
    }
}
