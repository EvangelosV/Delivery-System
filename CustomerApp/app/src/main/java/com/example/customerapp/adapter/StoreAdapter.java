package com.example.customerapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.customerapp.R;
import com.example.customerapp.model.StoreItem;

import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.StoreViewHolder> {

    public interface OnStoreClick {
        void onClick(StoreItem store);
    }

    private final List<StoreItem> storeList;
    private final OnStoreClick listener;

    public StoreAdapter(List<StoreItem> storeList, OnStoreClick listener) {
        this.storeList = storeList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.store_item, parent, false);
        return new StoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
        StoreItem s = storeList.get(position);

        // Set existing store information
        holder.tvTitle.setText(s.getTitle());
        holder.tvCategory.setText(s.getCategory());
        holder.tvPriceRange.setText(s.getPriceRange());
        holder.tvStars.setText(starsString(s.getStars()));

        // Load store logo using Glide
        String logoPath = s.getLogoPath();
        if (logoPath != null && !logoPath.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(logoPath)
                    .placeholder(R.drawable.placeholder_logo)
                    .error(R.drawable.error_logo)
                    .into(holder.ivStoreLogo);
        } else {
            holder.ivStoreLogo.setImageResource(R.drawable.default_logo);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(s);
        });
    }

    @Override
    public int getItemCount() {
        return storeList.size();
    }

    static class StoreViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle, tvCategory, tvPriceRange, tvStars;
        final ImageView ivStoreLogo;

        StoreViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvPriceRange = itemView.findViewById(R.id.tvPriceRange);
            tvStars = itemView.findViewById(R.id.tvStars);
            ivStoreLogo = itemView.findViewById(R.id.ivStoreLogo);
        }
    }

    private String starsString(int stars) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<stars; i++) sb.append("★ ");
        for (int i=stars; i<5; i++) sb.append(" ");
        return sb.toString();
    }
}