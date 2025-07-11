package com.example.customerapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.customerapp.R;
import com.example.customerapp.model.ProductItem;

import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClick {
        void onClick(ProductItem product);
    }

    private final List<ProductItem> list;
    private final OnProductClick listener;

    public ProductAdapter(List<ProductItem> list, OnProductClick listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.product_item, parent, false);
        return new ProductViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        ProductItem p = list.get(position);
        holder.tvTitle.setText(p.getProductName());
        holder.tvPrice.setText(String.format("%.2f €", p.getPrice()));
        holder.tvQty.setText("Stock: " + p.getAvailableAmount());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(p);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvPrice, tvQty;
        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.textTitle);
            tvPrice = itemView.findViewById(R.id.textPrice);
            tvQty   = itemView.findViewById(R.id.textQuantity);
        }
    }
}