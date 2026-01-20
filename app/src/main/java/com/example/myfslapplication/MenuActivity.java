package com.example.myfslapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ExperimentalGetImage;
import androidx.cardview.widget.CardView;

public class MenuActivity extends AppCompatActivity {

    private CardView cardGestureToAudio;
    private CardView cardAlphabetToAudio;
    private LinearLayout btnCredits;
    private LinearLayout btnSettings;
    private LinearLayout btnGuide;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        // Initialize views
        cardGestureToAudio = findViewById(R.id.cardGestureToAudio);
        cardAlphabetToAudio = findViewById(R.id.cardAlphabetToAudio);
        btnCredits = findViewById(R.id.btnCredits);
        btnSettings = findViewById(R.id.btnSettings);
        btnGuide = findViewById(R.id.btnGuide);

        // Gesture-To-Audio button
        cardGestureToAudio.setOnClickListener(new View.OnClickListener() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MenuActivity.this, GestureActivity.class);
                startActivity(intent);
            }
        });

        // Alphabet-To-Audio button
        cardAlphabetToAudio.setOnClickListener(new View.OnClickListener() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MenuActivity.this, AlphabetActivity.class);
                startActivity(intent);
            }
        });

        // Credits button
        btnCredits.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, CreditsActivity.class);
            startActivity(intent);
        });

        // Settings button
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Guide button
        btnGuide.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, GuideActivity.class);
            startActivity(intent);
        });
    }
}
