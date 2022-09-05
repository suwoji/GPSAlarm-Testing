package com.eebax.geofencing;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RelativeLayout;

public class MainActivity extends AppCompatActivity {

    RelativeLayout button;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        button = (RelativeLayout) findViewById(R.id.startButton);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.getDefaultNightMode());

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.startButton: {
                        Intent intent = new Intent(getApplicationContext(), MapsActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        (getApplicationContext()).startActivity(intent);
                    }
                    case R.id.modeButton: {
                        Intent intent = new Intent(getApplicationContext(), GeneralActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        (getApplicationContext()).startActivity(intent);
                    }
                }
            }
        });
        //fetchDevice();
    }
}