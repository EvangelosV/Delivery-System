package com.example.customerapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.customerapp.R;
import com.example.customerapp.adapter.StoreAdapter;
import com.example.customerapp.model.StoreItem;
import com.example.customerapp.network.CustomerClient;

import java.util.ArrayList;
import java.util.List;

public class ResultsActivity extends AppCompatActivity {

    private final List<StoreItem> stores = new ArrayList<>();
    private StoreAdapter adapter;
    private ProgressBar   progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_results);

        RecyclerView rv = findViewById(R.id.rvStores);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StoreAdapter(stores, store -> openProducts(store.getTitle()));
        rv.setAdapter(adapter);

        progress = new ProgressBar(this);
        ((android.widget.LinearLayout)rv.getParent()).addView(progress);

        double lon = getIntent().getDoubleExtra("lon", 23.7275);
        double lat = getIntent().getDoubleExtra("lat", 37.9838);
        String category = getIntent().getStringExtra("category");
        int stars  = getIntent().getIntExtra("stars", 0);
        int price  = getIntent().getIntExtra("price", 3);

        String filterType = "none";
        if (!category.isEmpty()) filterType = "category";
        else if (stars>0)        filterType = "stars";
        else if (price<3)       filterType = "price";

        progress.setVisibility(View.VISIBLE);
        CustomerClient.getInstance().findStores(lat, lon, filterType, category, stars, price,
                new CustomerClient.Callback<List<StoreItem>>() {
                    @Override public void onSuccess(List<StoreItem> data) {
                        progress.setVisibility(View.GONE);
                        stores.clear(); stores.addAll(data); adapter.notifyDataSetChanged();
                        if (data.isEmpty()) Toast.makeText(ResultsActivity.this,"No stores found",Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(Exception e) {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(ResultsActivity.this, "Error: "+e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openProducts(String storeName) {
        Intent i = new Intent(this, ProductActivity.class);
        i.putExtra("store", storeName);
        startActivity(i);
    }
}