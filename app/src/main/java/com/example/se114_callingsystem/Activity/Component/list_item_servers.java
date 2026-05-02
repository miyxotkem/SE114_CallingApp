package com.example.se114_callingsystem.Activity.Component;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.se114_callingsystem.Activity.Page.ChatDetailActivity;
import com.example.se114_callingsystem.R;
import com.google.android.material.card.MaterialCardView;

public class list_item_servers extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_list_item_servers);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        MaterialCardView cardServerCreate = findViewById(R.id.main);
        cardServerCreate.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatDetailActivity.class);
            startActivity(intent);
        });


    }
}