package com.example.plant_smart.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log; // Import Log
import android.view.MenuItem; // Import MenuItem
import android.view.View; // Import View
import android.widget.Button; // Import Button
import android.widget.EditText; // Import EditText
import android.widget.TextView;
import android.widget.Toast; // Import Toast
import androidx.annotation.NonNull; // Keep if needed for other annotations, otherwise remove
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
// Removed: import androidx.core.view.GravityCompat;
// Removed: import androidx.drawerlayout.widget.DrawerLayout;
// Removed: import com.google.android.material.navigation.NavigationView;
import com.example.plant_smart.R;


// Removed: implements NavigationView.OnNavigationItemSelectedListener
public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private static final String PREFS_NAME = "PlantSmartPrefs"; // SharedPreferences file name

    // TextViews to display user data (matching IDs from redesigned layout)
    private TextView textUsername;
    private TextView textPlantType; // Matches textPlantType in redesigned layout
    private TextView textAreaProfiled; // Matches textAreaProfiled in redesigned layout

    // UI elements for changing password (matching IDs from redesigned layout)
    private EditText editCurrentPassword;
    private EditText editNewPassword;
    private EditText editConfirmNewPassword;
    private Button btnChangePassword;

    // Removed: private DrawerLayout drawer;
    // Removed: private NavigationView navigationView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile); // Set the layout for this activity

        Log.d(TAG, "ProfileActivity onCreate");

        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button (back arrow)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Find UI elements from the layout (matching IDs from redesigned layout)
        textUsername = findViewById(R.id.textUsername);
        textPlantType = findViewById(R.id.textPlantType); // Initialize textPlantType
        textAreaProfiled = findViewById(R.id.textAreaProfiled); // Initialize textAreaProfiled

        editCurrentPassword = findViewById(R.id.editCurrentPassword);
        editNewPassword = findViewById(R.id.editNewPassword);
        editConfirmNewPassword = findViewById(R.id.editConfirmNewPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);

        // Removed: drawer = findViewById(R.id.drawer_layout);
        // Removed: navigationView = findViewById(R.id.nav_view);
        // Removed: navigationView.setNavigationItemSelectedListener(this);
        // Removed: ActionBarDrawerToggle toggle = ...; drawer.addDrawerListener(toggle); toggle.syncState();


        // Load user data from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Assuming you save these keys during signup/login
        String username = prefs.getString("username", "N/A"); // Default to N/A if not found
        String plantType = prefs.getString("plantType", "Not set"); // Use "plantType" key
        String areaProfiled = prefs.getString("areaProfiled", "Not set"); // Use "areaProfiled" key

        // Display user data in the TextViews
        // Set ONLY the value to the TextViews, as labels are in the layout XML
        textUsername.setText(username);
        textPlantType.setText(plantType);
        textAreaProfiled.setText(areaProfiled);


        // Set click listener for the Change Password button
        btnChangePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleChangePassword();
            }
        });
    }

    // Handle the Up button (back arrow) click in the Toolbar
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Go back when the Up button is clicked
        return true;
    }

    // Method to handle the change password logic
    private void handleChangePassword() {
        String currentPassword = editCurrentPassword.getText().toString().trim();
        String newPassword = editNewPassword.getText().toString().trim();
        String confirmNewPassword = editConfirmNewPassword.getText().toString().trim();

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all password fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        // >>> Implement your password change logic here <<<
        // This would typically involve:
        // 1. Verifying the current password (might require sending to your backend/broker)
        // 2. Sending the new password securely to your backend/broker for update.
        // 3. Handling the response (success or failure).
        // You might use your MqttHelper to publish a password change request message.

        Log.d(TAG, "Attempting to change password. Current: [HIDDEN], New: [HIDDEN]"); // Log securely

        // --- Example: Simulate a password change attempt ---
        // In a real app, you would send this data via MQTT or an API call
        boolean passwordChangeSuccessful = true; // Simulate success for now

        if (passwordChangeSuccessful) {
            Toast.makeText(this, "Password changed successfully!", Toast.LENGTH_SHORT).show();
            // Clear the password fields on success
            editCurrentPassword.setText("");
            editNewPassword.setText("");
            editConfirmNewPassword.setText("");
        } else {
            Toast.makeText(this, "Failed to change password. Please check current password.", Toast.LENGTH_SHORT).show();
            // Optionally clear only the new password fields on failure
            editNewPassword.setText("");
            editConfirmNewPassword.setText("");
        }
        // --- End of example simulation ---
    }

    // Removed: onNavigationItemSelected method
    // Removed: onBackPressed method related to DrawerLayout
    // The default onBackPressed will now be used, which works with the Up button.
}
