package com.example.plant_smart.activities;

import android.content.Context;
import android.content.Intent; // Needed if you want to navigate after saving
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log; // Import Log
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter; // Import ArrayAdapter
import android.widget.Button;
import android.widget.SeekBar; // Keep import if needed elsewhere, otherwise remove
import android.widget.Spinner;
import android.widget.TextView; // Keep import if needed elsewhere, otherwise remove
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import com.example.plant_smart.R;

import java.util.HashMap; // Import HashMap
import java.util.Map; // Import Map


public class PlantProfileActivity extends AppCompatActivity {

    private static final String TAG = "PlantProfileActivity"; // Tag for logging
    private static final String PREFS_NAME = "PlantSmartPrefs"; // SharedPreferences file name
    private static final String KEY_PLANT_TYPE = "plantType"; // Key for saving plant type
    private static final String KEY_AREA_PROFILED = "areaProfiled"; // Key for saving area profiled
    private static final String KEY_WATERING_THRESHOLD = "wateringThreshold"; // Key for saving watering threshold


    private Spinner spinnerCategory, spinnerEnvironment;
    // Removed: private SeekBar seekBarThreshold; // Removed SeekBar
    // Removed: private TextView textThresholdValue; // Removed TextView for SeekBar value
    private Button btnSaveProfile;

    // Map to store threshold values based on Category and Environment
    private Map<String, Map<String, Integer>> thresholdMap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plant_profile); // Ensure this matches your layout file name

        Log.d(TAG, "PlantProfileActivity onCreate"); // Log onCreate

        // Set up the toolbar (assuming your layout includes a Toolbar with ID 'toolbar')
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button (back arrow)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Plant Profile"); // Set toolbar title
        }


        // Find UI elements
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerEnvironment = findViewById(R.id.spinnerEnvironment);
        // Removed: seekBarThreshold = findViewById(R.id.seekBarThreshold);
        // Removed: textThresholdValue = findViewById(R.id.textThresholdValue);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);

        // >>> Initialize the threshold map <<<
        initializeThresholdMap();

        // >>> Populate Spinners (You need to define these in your resources/arrays.xml) <<<
        // Example using dummy arrays (Replace with your actual arrays.xml references)
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.plant_categories, // Define this array in res/values/arrays.xml
                android.R.layout.simple_spinner_item
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        ArrayAdapter<CharSequence> environmentAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.plant_environments, // Define this array in res/values/arrays.xml
                android.R.layout.simple_spinner_item
        );
        environmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnvironment.setAdapter(environmentAdapter);


        // Removed: Update threshold text as SeekBar changes
        // seekBarThreshold.setOnSeekBarChangeListener(...)


        btnSaveProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String category = spinnerCategory.getSelectedItem().toString();
                String environment = spinnerEnvironment.getSelectedItem().toString();

                // >>> Determine threshold based on selection <<<
                int determinedThreshold = getThresholdForSelection(category, environment);

                // Basic validation for selections (optional, but good practice)
                if (determinedThreshold == -1) { // Assuming -1 indicates an unhandled combination
                    Toast.makeText(PlantProfileActivity.this, "Could not determine threshold for selection.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to determine threshold for Category: " + category + ", Environment: " + environment);
                    return;
                }


                // Save plant profile settings to SharedPreferences
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_PLANT_TYPE, category); // Save selected category as plant type
                editor.putString(KEY_AREA_PROFILED, environment); // Save selected environment as area profiled (adjust if you have a separate area input)
                editor.putInt(KEY_WATERING_THRESHOLD, determinedThreshold); // Save the determined threshold
                editor.apply(); // Use apply() for asynchronous saving

                Log.d(TAG, "Plant Profile saved: Category=" + category + ", Environment=" + environment + ", Threshold=" + determinedThreshold); // Log saving

                Toast.makeText(PlantProfileActivity.this, "Profile saved", Toast.LENGTH_SHORT).show();

                // >>> Navigate to DashboardActivity after saving <<<
                Log.d(TAG, "Starting DashboardActivity after saving profile."); // Log navigation
                Intent intent = new Intent(PlantProfileActivity.this, DashboardActivity.class);
                startActivity(intent);

                // Finish PlantProfileActivity so the user cannot go back to it
                finish();
                // ---------------------------------------------------
            }
        });

        // Optional: Load previously saved selections and set Spinners
        loadSavedProfile();
    }

    // Handle the Up button (back arrow) click in the Toolbar
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Go back when the Up button is clicked
        return true;
    }

    // Method to initialize the threshold map
    private void initializeThresholdMap() {
        thresholdMap = new HashMap<>();

        // >>> Define your threshold values here <<<
        // Example mapping:
        Map<String, Integer> floweringThresholds = new HashMap<>();
        floweringThresholds.put("Indoor", 60); // Example: Flowering Indoor needs 60% moisture
        floweringThresholds.put("Outdoor", 50); // Example: Flowering Outdoor needs 50% moisture
        thresholdMap.put("Flowering Plant", floweringThresholds); // Use the exact string from your categories array

        Map<String, Integer> vegetableThresholds = new HashMap<>();
        vegetableThresholds.put("Indoor", 70); // Example: Vegetable Indoor needs 70% moisture
        vegetableThresholds.put("Outdoor", 60); // Example: Vegetable Outdoor needs 60% moisture
        thresholdMap.put("Vegetable Plant", vegetableThresholds); // Use the exact string from your categories array

        Map<String, Integer> succulentThresholds = new HashMap<>();
        succulentThresholds.put("Indoor", 30); // Example: Succulent Indoor needs 30% moisture
        succulentThresholds.put("Outdoor", 20); // Example: Succulent Outdoor needs 20% moisture
        thresholdMap.put("Succulent", succulentThresholds); // Use the exact string from your categories array

        // Add mappings for other categories as needed
    }

    // Method to determine the threshold based on selected category and environment
    private int getThresholdForSelection(String category, String environment) {
        if (thresholdMap.containsKey(category)) {
            Map<String, Integer> environmentMap = thresholdMap.get(category);
            if (environmentMap != null && environmentMap.containsKey(environment)) {
                return environmentMap.get(environment);
            }
        }
        // Return a default or indicator of not found
        return -1; // Indicate that the threshold could not be determined
    }

    // Optional: Method to load previously saved profile settings and set Spinners
    private void loadSavedProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedCategory = prefs.getString(KEY_PLANT_TYPE, null);
        String savedEnvironment = prefs.getString(KEY_AREA_PROFILED, null);

        if (savedCategory != null) {
            // Find the position of the saved category in the spinner
            ArrayAdapter adapter = (ArrayAdapter) spinnerCategory.getAdapter();
            int categoryPosition = adapter.getPosition(savedCategory);
            if (categoryPosition >= 0) {
                spinnerCategory.setSelection(categoryPosition);
            }
        }

        if (savedEnvironment != null) {
            // Find the position of the saved environment in the spinner
            ArrayAdapter adapter = (ArrayAdapter) spinnerEnvironment.getAdapter();
            int environmentPosition = adapter.getPosition(savedEnvironment);
            if (environmentPosition >= 0) {
                spinnerEnvironment.setSelection(environmentPosition);
            }
        }
        // The threshold will be determined automatically when saving
    }

    // You might add methods here to handle navigation to DashboardActivity
    // after saving the profile.
}



//package com.example.plant_smart.activities;
//
//import android.content.Context;
//import android.content.SharedPreferences;
//import android.os.Bundle;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.Button;
//import android.widget.SeekBar;
//import android.widget.Spinner;
//import android.widget.TextView;
//import android.widget.Toast;
//import androidx.appcompat.app.AppCompatActivity;
//import com.example.plant_smart.R;
//
//
//public class PlantProfileActivity extends AppCompatActivity {
//    private Spinner spinnerCategory, spinnerEnvironment;
//    private SeekBar seekBarThreshold;
//    private TextView textThresholdValue;
//    private Button btnSaveProfile;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_plant_profile);
//
//        spinnerCategory = findViewById(R.id.spinnerCategory);
//        spinnerEnvironment = findViewById(R.id.spinnerEnvironment);
//        seekBarThreshold = findViewById(R.id.seekBarThreshold);
//        textThresholdValue = findViewById(R.id.textThresholdValue);
//        btnSaveProfile = findViewById(R.id.btnSaveProfile);
//
//        // Update threshold text as SeekBar changes
//        seekBarThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                textThresholdValue.setText("Threshold: " + progress + "%");
//            }
//            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
//            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
//        });
//
//        btnSaveProfile.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                String category = spinnerCategory.getSelectedItem().toString();
//                String environment = spinnerEnvironment.getSelectedItem().toString();
//                int threshold = seekBarThreshold.getProgress();
//
//                // Save plant profile settings
//                SharedPreferences prefs = getSharedPreferences("PlantSmartPrefs", Context.MODE_PRIVATE);
//                SharedPreferences.Editor editor = prefs.edit();
//                editor.putString("plantCategory", category);
//                editor.putString("plantEnvironment", environment);
//                editor.putInt("wateringThreshold", threshold);
//                editor.apply();
//
//                Toast.makeText(PlantProfileActivity.this, "Profile saved", Toast.LENGTH_SHORT).show();
//            }
//        });
//    }
//}
