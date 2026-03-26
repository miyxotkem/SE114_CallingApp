package com.example.se114_callingsystem;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.view.View;
import com.google.android.material.card.MaterialCardView;

public class Default_page extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default_page);
        MaterialCardView cardServerItem = findViewById(R.id.cardServerItem);
        cardServerItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Default_page.this, Server_on_click.class);
                startActivity(intent);
            }
        });
    }
}