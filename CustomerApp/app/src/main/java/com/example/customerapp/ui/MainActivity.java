
package com.example.customerapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;
import com.example.customerapp.R;

public class MainActivity extends AppCompatActivity {

    private String chosenCategory = "";
    private int    chosenStars    = 0;
    private int    chosenPriceMax = 3; // 1‑3

    private double userLon, userLat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.homescreen);

        userLon = getIntent().getDoubleExtra("lon", 23.7275);
        userLat = getIntent().getDoubleExtra("lat", 37.9838);

        RatingBar ratingBar = findViewById(R.id.ratingBar);
        ratingBar.setOnRatingBarChangeListener((rb, rating, fromUser) -> {
            chosenStars = (int) rating;
        });

        SeekBar priceBar = findViewById(R.id.seekBar2);
        priceBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                chosenPriceMax = prog + 1; // range 1‑3
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { /* no‑op */ }
            @Override public void onStopTrackingTouch(SeekBar seekBar)  { /* no‑op */ }
        });

        // Category buttons
        int[] btnIds = { R.id.button_pizza, R.id.button_souvlaki, R.id.button_fastfood,
                R.id.button_icecream, R.id.button_bakery, R.id.button_coffee,
                R.id.button_sandwich };
        for (int id : btnIds) {
            Button b = findViewById(id);
            b.setOnClickListener(v -> chosenCategory = b.getText().toString().toLowerCase());
        }

        Button searchAll = findViewById(R.id.buttonAll); // search all stores
        searchAll.setOnClickListener(v -> {
            Intent i = new Intent(this, ResultsActivity.class);
            i.putExtra("lon", userLon);
            i.putExtra("lat", userLat);
            i.putExtra("category", ""); // no category
            i.putExtra("stars", 0);      // no stars
            i.putExtra("price", 3);      // no price limit
            startActivity(i);
        });

        Button search = findViewById(R.id.button); // big search button
        search.setOnClickListener(v -> {
            Intent i = new Intent(this, ResultsActivity.class);
            i.putExtra("lon", userLon);
            i.putExtra("lat", userLat);
            i.putExtra("category", chosenCategory);
            i.putExtra("stars", chosenStars);
            i.putExtra("price", chosenPriceMax);
            startActivity(i);
        });
    }
}
