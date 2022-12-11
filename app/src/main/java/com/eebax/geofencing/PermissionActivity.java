package com.eebax.geofencing;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.eebax.geofencing.DialogueFragment;
import com.eebax.geofencing.R;

public class PermissionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        showDialog();
    }

    public void showDialog() {
        DialogueFragment dialog = new DialogueFragment();
        dialog.show(getSupportFragmentManager(), "custom");
    }
}