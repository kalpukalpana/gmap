package org.meicode.gmap;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.gms.maps.model.CameraPosition;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int AUTOCOMPLETE_REQUEST_CODE = 1;
    private GoogleMap map;
    private List<Polyline> polylines = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Button startButton;
    private Button directionsButton;
    private LatLng destinationLatLng;
    private View destinationInfoCard;
    private TextView destinationAddressText;
    private TextView durationDistanceText;
    private TextView etaText;
    private long startNavigationTime;
    private static final int ETA_UPDATE_INTERVAL = 10000; // 10 seconds

    // Add these constants for travel modes
    private static final String MODE_DRIVING = "driving";
    private static final String MODE_WALKING = "walking";
    private static final String MODE_BICYCLING = "bicycling";
    private static final String MODE_TRANSIT = "transit";

    // Add variable to track current travel mode
    private String currentTravelMode = MODE_DRIVING;

    // Add these class variables
    private boolean isNavigating = false;
    private LocationCallback locationCallback;
    private static final int LOCATION_UPDATE_INTERVAL = 3000; // 3 seconds
    private Polyline currentRoutePolyline;
    private static final int UPDATE_INTERVAL = 5000; // 5 seconds
    private static final float MIN_DISTANCE_TO_UPDATE = 10; // 10 meters
    private Location lastLocation;
    private Handler updateHandler = new Handler();
    private static final int LIVE_UPDATE_INTERVAL = 30000; // 30 seconds
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // Initialize Google Places API
            if (!Places.isInitialized()) {
                Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
            }

            // Set up the Google Map
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.id_map);
            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            }

            // Set up the FusedLocationProviderClient
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

            // Initialize views
            destinationInfoCard = findViewById(R.id.destination_info_card);
            destinationAddressText = findViewById(R.id.destination_address);
            durationDistanceText = findViewById(R.id.duration_distance);
            etaText = findViewById(R.id.eta_text);
            if (etaText != null) {
                etaText.setVisibility(View.GONE);
            }

            // Initially hide the info card
            if (destinationInfoCard != null) {
                destinationInfoCard.setVisibility(View.GONE);
            }

            // Set up buttons
            setupButtons();

        } catch (Exception e) {
            Log.e("MainActivity", "Error in onCreate: " + e.getMessage());
            Toast.makeText(this, "Error initializing app", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupButtons() {
        // Set up the Search FAB
        FloatingActionButton searchButton = findViewById(R.id.btn_search_places);
        searchButton.setOnClickListener(v -> openAutocompleteActivity());

        // Set up navigation buttons
        startButton = findViewById(R.id.start);
        startButton.setOnClickListener(v -> startNavigation());

        directionsButton = findViewById(R.id.directions);
        directionsButton.setOnClickListener(v -> showDirections());

        // Set up travel mode buttons
        ImageButton drivingButton = findViewById(R.id.btn_mode_driving);
        ImageButton walkingButton = findViewById(R.id.btn_mode_walking);
        ImageButton bicyclingButton = findViewById(R.id.btn_mode_bicycling);
        ImageButton transitButton = findViewById(R.id.btn_mode_transit);

        drivingButton.setOnClickListener(v -> {
            changeTravelMode(MODE_DRIVING);
            updateTravelModeUI(drivingButton);
        });

        walkingButton.setOnClickListener(v -> {
            changeTravelMode(MODE_WALKING);
            updateTravelModeUI(walkingButton);
        });

        bicyclingButton.setOnClickListener(v -> {
            changeTravelMode(MODE_BICYCLING);
            updateTravelModeUI(bicyclingButton);
        });

        transitButton.setOnClickListener(v -> {
            changeTravelMode(MODE_TRANSIT);
            updateTravelModeUI(transitButton);
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        
        try {
            // Enable my location if permission is granted
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED) {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
                
                // Get current location and move camera there
                fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                    }
                });
            } else {
                // Request location permission
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }

            // Set up map UI settings
            map.getUiSettings().setZoomControlsEnabled(true);
            map.getUiSettings().setCompassEnabled(true);
            map.getUiSettings().setMapToolbarEnabled(true);

        } catch (Exception e) {
            Log.e("Map", "Error setting up map: " + e.getMessage());
            Toast.makeText(this, "Error setting up map", Toast.LENGTH_SHORT).show();
        }
    }

    private void startNavigation() {
        if (destinationLatLng == null) {
            Toast.makeText(this, "Please select a destination first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNavigating) {
            // Start navigation
            startRealTimeNavigation();
            startButton.setText("Stop");
            isNavigating = true;
        } else {
            // Stop navigation
            stopRealTimeNavigation();
            startButton.setText("Start");
            isNavigating = false;
        }
    }

    private void showDirections() {
        if (destinationLatLng == null) {
            Toast.makeText(this, "Please select a destination first", Toast.LENGTH_SHORT).show();
            return;
        }
        // Add directions logic here
        fetchRouteToDestination(destinationLatLng);
    }

    private void openAutocompleteActivity() {
        try {
            List<Place.Field> fields = Arrays.asList(
                Place.Field.ID, 
                Place.Field.NAME, 
                Place.Field.LAT_LNG, 
                Place.Field.ADDRESS
            );

            Intent intent = new Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY, fields)
                .build(this);
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);
        } catch (Exception e) {
            Log.e("Places", "Error initializing Places: " + e.getMessage());
            Toast.makeText(this, "Error initializing Places", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Place place = Autocomplete.getPlaceFromIntent(data);
            if (place.getLatLng() != null) {
                destinationLatLng = place.getLatLng();
                
                // Clear previous markers and routes
                clearExistingPolylines();
                
                // Add marker for destination
                map.addMarker(new MarkerOptions()
                    .position(destinationLatLng)
                    .title(place.getName())
                    .snippet(place.getAddress()));
                
                // Fetch and draw route
                fetchRouteToDestination(destinationLatLng);
            }
        }
    }

    private void clearExistingPolylines() {
        // Clear existing polylines
        for (Polyline polyline : polylines) {
            polyline.remove();
        }
        polylines.clear();

        // Clear existing markers
        if (map != null) {
            map.clear();
        }

        // Hide and clear info card
        if (destinationInfoCard != null) {
            destinationInfoCard.setVisibility(View.GONE);
        }
        if (destinationAddressText != null) {
            destinationAddressText.setText("");
        }
        if (durationDistanceText != null) {
            durationDistanceText.setText("");
        }
    }

    // Add this method to fetch and draw the route
    private void fetchRouteToDestination(LatLng destination) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng origin = new LatLng(location.getLatitude(), location.getLongitude());
                String url = getDirectionsUrl(origin, destination);
                new FetchDirectionsTask(url).execute();
            } else {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Add method to create directions URL
    private String getDirectionsUrl(LatLng origin, LatLng destination) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + destination.latitude + "," + destination.longitude;
        String sensor = "sensor=false";
        String mode = "mode=" + currentTravelMode;
        String key = "key=" + getString(R.string.google_maps_key);
        
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode + "&" + key;
        String output = "json";
        
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
    }

    // Add AsyncTask to fetch directions
    private class FetchDirectionsTask extends AsyncTask<Void, Void, String> {
        private final String url;

        FetchDirectionsTask(String url) {
            this.url = url;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL(this.url);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                
                reader.close();
                return result.toString();
                
            } catch (Exception e) {
                Log.e("DirectionsAPI", "Error fetching directions: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String response) {
            if (response == null) {
                Toast.makeText(MainActivity.this, "Error fetching directions", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject jsonResponse = new JSONObject(response);
                JSONArray routes = jsonResponse.getJSONArray("routes");
                
                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);
                    JSONArray legs = route.getJSONArray("legs");
                    JSONObject leg = legs.getJSONObject(0);
                    
                    // Get route details
                    String distance = leg.getJSONObject("distance").getString("text");
                    String duration = leg.getJSONObject("duration").getString("text");
                    String endAddress = leg.getString("end_address");
                    
                    // Get start and end locations
                    JSONObject startLocation = leg.getJSONObject("start_location");
                    JSONObject endLocation = leg.getJSONObject("end_location");
                    
                    LatLng origin = new LatLng(
                        startLocation.getDouble("lat"),
                        startLocation.getDouble("lng")
                    );
                    
                    LatLng destination = new LatLng(
                        endLocation.getDouble("lat"),
                        endLocation.getDouble("lng")
                    );

                    // Clear previous routes and markers
                    clearExistingPolylines();

                    // Add markers for origin and destination
                    map.addMarker(new MarkerOptions()
                        .position(origin)
                        .title("Current Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

                    map.addMarker(new MarkerOptions()
                        .position(destination)
                        .title("Destination")
                        .snippet(endAddress)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                    // Draw route polyline
                    JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                    String encodedPolyline = overviewPolyline.getString("points");
                    List<LatLng> polylinePoints = decodePolyline(encodedPolyline);

                    // Store the new polyline
                    currentRoutePolyline = map.addPolyline(new PolylineOptions()
                        .addAll(polylinePoints)
                        .color(Color.BLUE)
                        .width(12)
                        .geodesic(true));
                    polylines.add(currentRoutePolyline);

                    // Only zoom to bounds if not in navigation mode
                    if (!isNavigating) {
                        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                        bounds.include(origin);
                        bounds.include(destination);
                        for (LatLng point : polylinePoints) {
                            bounds.include(point);
                        }
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));
                    }

                    // Update info card
                    if (destinationInfoCard != null) {
                        destinationInfoCard.setVisibility(View.VISIBLE);
                    }
                    if (destinationAddressText != null) {
                        destinationAddressText.setText(endAddress);
                    }
                    if (durationDistanceText != null) {
                        durationDistanceText.setText(String.format("%s • %s", duration, distance));
                    }

                    // Update ETA if navigating
                    if (isNavigating) {
                        updateETA(duration);
                    }

                } else {
                    Toast.makeText(MainActivity.this, "No routes found", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("DirectionsAPI", "Error: " + e.getMessage());
                Toast.makeText(MainActivity.this, "Error getting directions", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Add method to decode polyline points
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            double latitude = lat / 1E5;
            double longitude = lng / 1E5;
            poly.add(new LatLng(latitude, longitude));
        }
        return poly;
    }

    // Add method to change travel mode
    private void changeTravelMode(String mode) {
        currentTravelMode = mode;
        if (destinationLatLng != null) {
            // Refetch route with new travel mode
            fetchRouteToDestination(destinationLatLng);
        }
    }

    // Add method to update UI for selected travel mode
    private void updateTravelModeUI(ImageButton selectedButton) {
        // Reset all buttons to default state
        int defaultColor = Color.GRAY;
        ((ImageButton) findViewById(R.id.btn_mode_driving)).setColorFilter(defaultColor);
        ((ImageButton) findViewById(R.id.btn_mode_walking)).setColorFilter(defaultColor);
        ((ImageButton) findViewById(R.id.btn_mode_bicycling)).setColorFilter(defaultColor);
        ((ImageButton) findViewById(R.id.btn_mode_transit)).setColorFilter(defaultColor);

        // Highlight selected button
        selectedButton.setColorFilter(getResources().getColor(R.color.primary));
    }

    // Add these methods for real-time navigation
    private void startRealTimeNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        try {
            startNavigationTime = System.currentTimeMillis();
            // Create location request with more frequent updates
            LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(UPDATE_INTERVAL / 2)
                .setSmallestDisplacement(5); // Minimum 5 meters

            // Reset last location
            lastLocation = null;

            // Create location callback
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && isNavigating) {
                        Location location = locationResult.getLastLocation();
                        updateNavigation(location);
                    }
                }
            };

            // Request location updates
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);

            // Initial navigation setup
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    
                    // Clear and add markers
                    clearExistingPolylines();
                    map.addMarker(new MarkerOptions()
                        .position(currentLatLng)
                        .title("Your Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                    map.addMarker(new MarkerOptions()
                        .position(destinationLatLng)
                        .title("Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                    // Enable navigation UI
                    map.getUiSettings().setRotateGesturesEnabled(true);
                    map.getUiSettings().setCompassEnabled(true);
                    map.getUiSettings().setTiltGesturesEnabled(true);

                    // Update route
                    fetchRouteToDestination(destinationLatLng);

                    // Set up navigation camera
                    CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(currentLatLng)
                        .zoom(18)
                        .tilt(45)
                        .bearing(getBearing(currentLatLng, destinationLatLng))
                        .build();
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }
            });

            // Start live updates
            startLiveUpdates();

        } catch (Exception e) {
            Log.e("Navigation", "Error starting navigation: " + e.getMessage());
            Toast.makeText(this, "Error starting navigation", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRealTimeNavigation() {
        try {
            // Stop live updates
            stopLiveUpdates();

            // Remove location updates
            if (locationCallback != null) {
                fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            }

            // Reset map view
            map.getUiSettings().setRotateGesturesEnabled(false);
            map.getUiSettings().setTiltGesturesEnabled(false);
            
            // Clear route and markers
            clearExistingPolylines();

            // Reset camera
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                    }
                });
            }

        } catch (Exception e) {
            Log.e("Navigation", "Error stopping navigation: " + e.getMessage());
            Toast.makeText(this, "Error stopping navigation", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNavigation(Location location) {
        if (location != null && isNavigating) {
            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

            // Check if we need to update the route (based on distance moved)
            boolean shouldUpdateRoute = lastLocation == null || 
                location.distanceTo(lastLocation) > MIN_DISTANCE_TO_UPDATE;

            if (shouldUpdateRoute) {
                lastLocation = location;
                updateRouteAndUI(currentLatLng);
            }

            // Update camera position for navigation
            updateNavigationCamera(location);

            // Check if destination reached
            checkDestinationReached(currentLatLng);
        }
    }

    private void updateRouteAndUI(LatLng currentLatLng) {
        // Clear previous route but keep markers
        if (currentRoutePolyline != null) {
            currentRoutePolyline.remove();
        }

        // Fetch new route from current location
        fetchRouteToDestination(destinationLatLng);
    }

    private void updateNavigationCamera(Location location) {
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        
        CameraPosition cameraPosition = new CameraPosition.Builder()
            .target(currentLatLng)
            .zoom(18)  // Close zoom for navigation
            .tilt(45)  // Tilt for 3D view
            .bearing(location.getBearing())  // Rotate based on movement
            .build();

        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 
            1000, null);  // Smooth 1-second animation
    }

    private void checkDestinationReached(LatLng currentLatLng) {
        float[] results = new float[1];
        Location.distanceBetween(
            currentLatLng.latitude, currentLatLng.longitude,
            destinationLatLng.latitude, destinationLatLng.longitude,
            results);

        if (results[0] < 50) {  // Within 50 meters of destination
            // Destination reached
            Toast.makeText(this, "You have reached your destination!", Toast.LENGTH_LONG).show();
            stopRealTimeNavigation();
            startButton.setText("Start");
            isNavigating = false;
        }
    }

    // Helper method to calculate bearing between two points
    private float getBearing(LatLng start, LatLng end) {
        double lat1 = Math.toRadians(start.latitude);
        double lat2 = Math.toRadians(end.latitude);
        double lng1 = Math.toRadians(start.longitude);
        double lng2 = Math.toRadians(end.longitude);

        double y = Math.sin(lng2 - lng1) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(lng2 - lng1);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360) % 360);
    }

    // Add method to calculate and update ETA
    private void updateETA(String duration) {
        try {
            int minutes = parseDurationToMinutes(duration);
            long etaMillis = System.currentTimeMillis() + (minutes * 60 * 1000);
            String etaTime = formatETA(etaMillis);
            
            // Calculate and format remaining time
            long remainingMillis = etaMillis - System.currentTimeMillis();
            String remainingTime = formatRemainingTime(remainingMillis);
            
            // Update UI
            runOnUiThread(() -> {
                if (etaText != null) {
                     etaText.setVisibility(View.VISIBLE);
                     etaText.setText(String.format("ETA: %s (%s)", etaTime, remainingTime));
                }
            });
        } catch (Exception e) {
            Log.e("ETA", "Error calculating ETA: " + e.getMessage());
        }
    }

    // Add helper methods for ETA calculation
    private int parseDurationToMinutes(String duration) {
        int minutes = 0;
        try {
            String[] parts = duration.toLowerCase().split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].contains("hour")) {
                    minutes += 60 * Integer.parseInt(parts[i-1]);
                } else if (parts[i].contains("min")) {
                    minutes += Integer.parseInt(parts[i-1]);
                }
            }
        } catch (Exception e) {
            Log.e("Duration", "Error parsing duration: " + e.getMessage());
        }
        return minutes;
    }

    private String formatETA(long etaMillis) {
        android.text.format.DateFormat df = new android.text.format.DateFormat();
        return df.format("hh:mm a", new java.util.Date(etaMillis)).toString();
    }

    // Add this method to start live updates
    private void startLiveUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isNavigating && destinationLatLng != null) {
                    // Update route and ETA
                    fetchRouteToDestination(destinationLatLng);
                    
                    // Calculate remaining distance
                    if (lastLocation != null) {
                        float[] results = new float[1];
                        Location.distanceBetween(
                            lastLocation.getLatitude(), lastLocation.getLongitude(),
                            destinationLatLng.latitude, destinationLatLng.longitude,
                            results
                        );
                        
                        // Update progress
                        updateProgress(results[0]);
                    }
                }
                // Schedule next update
                updateHandler.postDelayed(this, LIVE_UPDATE_INTERVAL);
            }
        };

        
        // Start updates
        updateHandler.post(updateRunnable);
    }

    // Add method to stop live updates
    private void stopLiveUpdates() {
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    // Add method to update progress
    private void updateProgress(float remainingDistance) {
        try {
            // Convert distance to appropriate unit
            String distanceText;
            if (remainingDistance > 1000) {
                distanceText = String.format("%.1f km remaining", remainingDistance / 1000);
            } else {
                distanceText = String.format("%.0f m remaining", remainingDistance);
            }

            // Update UI
            runOnUiThread(() -> {
                if (durationDistanceText != null) {
                    String currentText = durationDistanceText.getText().toString();
                    // Keep the duration part and update distance
                    String[] parts = currentText.split("•");
                    if (parts.length > 0) {
                        durationDistanceText.setText(parts[0].trim() + " • " + distanceText);
                    }
                }
            });
        } catch (Exception e) {
            Log.e("Progress", "Error updating progress: " + e.getMessage());
        }
    }

    // Add helper method for remaining time format
    private String formatRemainingTime(long millis) {
        long minutes = millis / (60 * 1000);
        if (minutes < 60) {
            return minutes + " min";
        } else {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%dh %dm", hours, minutes);
        }
    }

    // Update onDestroy to clean up
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLiveUpdates();
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
    }

    // ... rest of your existing code ...
}