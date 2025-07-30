package com.example.plant_smart.activities;

import android.content.Context;
import android.content.Intent; // Needed for starting new activities
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType; // Needed for password toggle logic if added later
import android.util.Log; // Import Log for logging
import android.view.View;
import android.widget.Button; // Keep for btnCreateAccount (MaterialButton is a subclass)
import android.widget.EditText;
import android.widget.ImageButton; // Added for Back button
import android.widget.TextView; // Added for Login link
// import android.widget.ImageView; // Only needed if you add manual password toggle logic
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.plant_smart.R;


public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity"; // Tag for logging

    // --- UI Elements ---
    private EditText editNewUsername, editNewPassword;
    private Button btnCreateAccount; // Keep as Button (MaterialButton is compatible)
    private ImageButton buttonBackSignup; // Added
    private TextView textLoginLink; // Added
    // Optional: private ImageView passwordToggleIcon;
    // -----------------


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure this matches the XML layout file name
        setContentView(R.layout.activity_signup);

        Log.d(TAG, "SignupActivity onCreate"); // Log onCreate

        // --- Find Views By ID ---
        // Core elements (IDs kept the same as original XML)
        editNewUsername = findViewById(R.id.editNewUsername);
        editNewPassword = findViewById(R.id.editNewPassword);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);

        // New elements from the redesigned layout (assuming these IDs exist in your activity_signup.xml)
        buttonBackSignup = findViewById(R.id.buttonBackSignup);
        textLoginLink = findViewById(R.id.textLoginLink);
        // ----------------------


        // --- Set OnClick Listeners ---

        // Listener for the Create Account Button
        btnCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String newUsername = editNewUsername.getText().toString().trim(); // Use trim()
                String newPassword = editNewPassword.getText().toString().trim(); // Use trim()

                Log.d(TAG, "Create Account button clicked. Username: " + newUsername); // Log button click

                // Basic Validation
                if (newUsername.isEmpty() || newPassword.isEmpty()) {
                    Toast.makeText(SignupActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Signup failed: Fields are empty."); // Log validation failure
                    return;
                }

                // >>> Implement more robust validation here if needed (e.g., password strength) <<<

                // Save credentials in SharedPreferences
                // Using "PlantSmartPrefs" as the preference file name (consistent with LoginActivity)
                SharedPreferences prefs = getSharedPreferences("PlantSmartPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                // IMPORTANT: Ensure the key "username" matches what LoginActivity uses for validation
                editor.putString("username", newUsername);
                editor.putString("password", newPassword);
                editor.apply(); // Use apply() for asynchronous saving
                Log.d(TAG, "Credentials saved to SharedPreferences."); // Log saving

                Toast.makeText(SignupActivity.this, "Account created.", Toast.LENGTH_SHORT).show(); // Shorter duration might be fine now

                // >>> Change the flow: Navigate to PlantProfileActivity instead of finishing <<<
                Log.d(TAG, "Starting PlantProfileActivity after successful signup."); // Log navigation
                Intent intent = new Intent(SignupActivity.this, PlantProfileActivity.class);
                startActivity(intent);

                // Finish SignupActivity so the user cannot go back to it using the back button
                finish();
                // ---------------------------------------------------------------------------
            }
        });

        // Listener for the new Back Button
        buttonBackSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Back button clicked. Finishing SignupActivity."); // Log button click
                finish(); // Closes the current activity, returning to the previous one (likely Login)
            }
        });

        // Listener for the new "Login" link TextView
        textLoginLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Login link clicked. Finishing SignupActivity."); // Log link click
                // Since we want to go back to LoginActivity, and LoginActivity is
                // likely the activity that started SignupActivity, simply finishing
                // SignupActivity will return to LoginActivity.
                finish();
            }
        });

        // Optional: Add logic for password visibility toggle if you implement manual control
        // ImageView passwordToggleIcon = findViewById(R.id.your_password_toggle_icon_id); // If needed

        // ---------------------------
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "SignupActivity onDestroy"); // Log onDestroy
    }
}



//package com.example.plant_smart.activities;


//
//import android.content.Context;
//import android.content.Intent; // Needed for Login link
//import android.content.SharedPreferences;
//import android.os.Bundle;
//import android.text.InputType; // Needed for password toggle logic if added later
//import android.view.View;
//import android.widget.Button; // Keep for btnCreateAccount (MaterialButton is a subclass)
//import android.widget.EditText;
//import android.widget.ImageButton; // Added for Back button
//import android.widget.TextView; // Added for Login link
//// import android.widget.ImageView; // Only needed if you add manual password toggle logic
//import android.widget.Toast;
//import androidx.appcompat.app.AppCompatActivity;
//import com.example.plant_smart.R;
//
//
//public class SignupActivity extends AppCompatActivity {
//
//    // --- UI Elements ---
//    private EditText editNewUsername, editNewPassword;
//    private Button btnCreateAccount; // Keep as Button (MaterialButton is compatible)
//    private ImageButton buttonBackSignup; // Added
//    private TextView textLoginLink; // Added
//    // Optional: private ImageView passwordToggleIcon;
//    // -----------------
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        // Ensure this matches the XML layout file name
//        setContentView(R.layout.activity_signup);
//
//        // --- Find Views By ID ---
//        // Core elements (IDs kept the same as original XML)
//        editNewUsername = findViewById(R.id.editNewUsername);
//        editNewPassword = findViewById(R.id.editNewPassword);
//        btnCreateAccount = findViewById(R.id.btnCreateAccount);
//
//        // New elements from the redesigned layout
//        buttonBackSignup = findViewById(R.id.buttonBackSignup);
//        textLoginLink = findViewById(R.id.textLoginLink);
//        // ----------------------
//
//
//        // --- Set OnClick Listeners ---
//
//        // Listener for the Create Account Button (Existing Logic)
//        btnCreateAccount.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                String newUsername = editNewUsername.getText().toString().trim(); // Use trim()
//                String newPassword = editNewPassword.getText().toString().trim(); // Use trim()
//
//                // Basic Validation
//                if (newUsername.isEmpty() || newPassword.isEmpty()) {
//                    Toast.makeText(SignupActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//
//                // Save credentials in SharedPreferences
//                // Using "PlantSmartPrefs" as the preference file name (consistent with LoginActivity)
//                SharedPreferences prefs = getSharedPreferences("PlantSmartPrefs", Context.MODE_PRIVATE);
//                SharedPreferences.Editor editor = prefs.edit();
//                // IMPORTANT: Ensure the key "username" matches what LoginActivity uses for validation
//                editor.putString("username", newUsername);
//                editor.putString("password", newPassword);
//                editor.apply(); // Use apply() for asynchronous saving
//
//                Toast.makeText(SignupActivity.this, "Account created. Please log in.", Toast.LENGTH_LONG).show(); // Longer duration might be better
//                finish(); // Close SignupActivity and return to LoginActivity
//            }
//        });
//
//        // Listener for the new Back Button
//        buttonBackSignup.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                finish(); // Closes the current activity, returning to the previous one (likely Login)
//            }
//        });
//
//        // Listener for the new "Login" link TextView
//        textLoginLink.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // Since we usually finish() SignupActivity after creation,
//                // just finishing might be enough. Or explicitly navigate if needed.
//                finish();
//                // Alternatively, if Signup might be launched from elsewhere:
//                // Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
//                // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK); // Clear back stack
//                // startActivity(intent);
//                // finish();
//            }
//        });
//
//        // Optional: Add logic for password visibility toggle if you implement manual control
//        // ImageView passwordToggleIcon = findViewById(R.id.your_password_toggle_icon_id); // If needed
//
//        // ---------------------------
//    }
//}