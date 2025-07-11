package com.example.customerapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.customerapp.R;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_screen);

        EditText etLon = findViewById(R.id.insertLon);
        EditText etLat = findViewById(R.id.insertLat);
        Button   btnNext = findViewById(R.id.next_button);
        btnNext.setOnClickListener(v -> {
            try {
                double lon = Double.parseDouble(etLon.getText().toString());
                double lat = Double.parseDouble(etLat.getText().toString());
                Intent i = new Intent(this, MainActivity.class);
                i.putExtra("lon", lon);
                i.putExtra("lat", lat);
                startActivity(i);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter valid coordinates", Toast.LENGTH_SHORT).show();
            }
        });

    }
}