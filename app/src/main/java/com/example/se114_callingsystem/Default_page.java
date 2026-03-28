package com.example.se114_callingsystem;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class Default_page extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default_page);

        DatabaseReference usersRef = Firebase.getUsersRef();

//        HashMap<String, Object> user = new HashMap<>();
//        user.put("username", "JohnDoe");
//        user.put("role", "student");
//        user.put("email", "john@example.com");
//        usersRef.push().setValue(user);

//        usersRef.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                // This runs immediately AND every time data changes in the cloud
//                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
//                    String name = snapshot.child("username").getValue(String.class);
//                    Log.d("FirebaseData", "User found: " + name);
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError databaseError) {
//                Log.w("FirebaseData", "loadPost:onCancelled", databaseError.toException());
//            }
//        });


        MaterialCardView cardServerItem = findViewById(R.id.cardServerItem);
        cardServerItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Default_page.this, Server_on_click.class);
                startActivity(intent);
            }
        });

        MaterialCardView cardServerCreate = findViewById(R.id.mcvServerCreate);
        cardServerCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Server_on_create dialog = new Server_on_create();
                dialog.show(getSupportFragmentManager(), "Server_on_create");
            }
        });
    }
}