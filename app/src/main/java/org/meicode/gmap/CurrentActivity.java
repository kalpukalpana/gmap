package org.meicode.gmap;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.model.LatLng;

public class CurrentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_current);

        ImageView imageIcon = findViewById(R.id.ic_map);
        imageIcon.setOnClickListener(v -> {
            LatLng destinationLatLng = new LatLng(37.7749, -122.4194); // Replace with actual location
            Intent intent = new Intent(CurrentActivity.this, MainActivity.class);
            intent.putExtra("destinationLatLng", new double[]{destinationLatLng.latitude, destinationLatLng.longitude});
            startActivity(intent);
        });
    }
}
