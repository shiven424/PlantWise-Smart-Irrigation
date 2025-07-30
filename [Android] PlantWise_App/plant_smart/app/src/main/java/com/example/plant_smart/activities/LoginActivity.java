package com.example.plant_smart.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.plant_smart.R;
import com.example.plant_smart.MyApp; // Import your custom Application class
import com.example.plant_smart.helpers.MqttHelper;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText editUsername;
    private EditText editPassword;
    private Button btnLogin;
    private TextView textSignup;
    private ImageButton btnBack;
    private CheckBox checkRemember;
    private TextView textForgotPassword;
    private ImageView btnTogglePassword;
    private boolean isPasswordVisible = false;

    // We still keep a reference to the MqttHelper singleton, but don't manage its lifecycle here.
    private MqttHelper mqttHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "LoginActivity onCreate");

        editUsername = findViewById(R.id.editFullName);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        textSignup = findViewById(R.id.textSignup);
        btnBack = findViewById(R.id.btnBack);
        checkRemember = findViewById(R.id.checkRemember);
        textForgotPassword = findViewById(R.id.textForgotPassword);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);

        // >>> Get the Singleton instance from the Application class <<<
        // The connection process is managed by the Application now.
        mqttHelper = MyApp.getInstance().getMqttHelper();
        Log.d(TAG, "Got MqttHelper instance from Application.");

        btnLogin.setOnClickListener(view -> performLogin());

        textSignup.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        btnBack.setOnClickListener(v -> finish());

        textForgotPassword.setOnClickListener(v ->
                Toast.makeText(LoginActivity.this, "Forgot Password Clicked!", Toast.LENGTH_SHORT).show()
        );

        checkRemember.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Handle remember me logic if needed
        });

        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
    }

    private void performLogin() {
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        String dummyUsername = "Vaibhav";
        String dummyPassword = "123";

        if (username.equals(dummyUsername) && password.equals(dummyPassword)) {
            Log.d(TAG, "Login successful. Starting DashboardActivity.");
            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish(); // Finish LoginActivity after successful login

            // >>> Do NOT call connectAndSubscribe or startConnecting here anymore <<<
            // The connection is managed by the Application class.
            // If you need to *change* the subscription after login, you could call
            // mqttHelper.subscribeToTopic("new/topic"); here, but the initial
            // connection and subscription attempts are handled by the Application.

        } else {
            Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Login failed: Invalid credentials.");
        }
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            // Optionally change the eye icon here
        } else {
            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            // Optionally change the eye icon here
        }
        isPasswordVisible = !isPasswordVisible;
        editPassword.setSelection(editPassword.length()); // Keep cursor at the end
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "LoginActivity onDestroy");
        // >>> Do NOT call disconnect() or stopConnecting() here <<<
        // The MqttHelper singleton instance and its connection lifecycle
        // are managed by the Application class.
    }
}




//package com.example.plant_smart.activities;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.text.InputType;
//import android.widget.Button;
//import android.widget.CheckBox;
//import android.widget.EditText;
//import android.widget.ImageButton;
//import android.widget.ImageView;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.example.plant_smart.R;
//import com.example.plant_smart.helpers.MqttHelper;
//
//public class LoginActivity extends AppCompatActivity {
//
//    private EditText editUsername;
//    private EditText editPassword;
//    private Button btnLogin;
//    private TextView textSignup;
//    private ImageButton btnBack;
//    private CheckBox checkRemember;
//    private TextView textForgotPassword;
//    private ImageView btnTogglePassword;
//    private boolean isPasswordVisible = false;
//
//    private MqttHelper mqttHelper;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_login);
//
//        editUsername = findViewById(R.id.editFullName);
//        editPassword = findViewById(R.id.editPassword);
//        btnLogin = findViewById(R.id.btnLogin);
//        textSignup = findViewById(R.id.textSignup);
//        btnBack = findViewById(R.id.btnBack);
//        checkRemember = findViewById(R.id.checkRemember);
//        textForgotPassword = findViewById(R.id.textForgotPassword);
//        btnTogglePassword = findViewById(R.id.btnTogglePassword);
//
//        mqttHelper = new MqttHelper(getApplicationContext());
//
//        btnLogin.setOnClickListener(view -> performLogin());
//
//        textSignup.setOnClickListener(view -> {
//            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
//            startActivity(intent);
//        });
//
//        btnBack.setOnClickListener(v -> finish());
//
//        textForgotPassword.setOnClickListener(v ->
//                Toast.makeText(LoginActivity.this, "Forgot Password Clicked!", Toast.LENGTH_SHORT).show()
//        );
//
//        checkRemember.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            // Handle remember me logic if needed
//        });
//
//        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
//    }
//
//    private void performLogin() {
//        String username = editUsername.getText().toString().trim();
//        String password = editPassword.getText().toString().trim();
//
//        String dummyUsername = "testuser";
//        String dummyPassword = "password123";
//
//        if (username.equals(dummyUsername) && password.equals(dummyPassword)) {
//            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
//            startActivity(intent);
//            finish();
//
//            // Connect to MQTT and subscribe after successful login
//            mqttHelper.connectAndSubscribe("plant/sensors");
//        } else {
//            Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private void togglePasswordVisibility() {
//        if (isPasswordVisible) {
//            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//        } else {
//            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
//        }
//        isPasswordVisible = !isPasswordVisible;
//        editPassword.setSelection(editPassword.length());
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (mqttHelper != null) {
//            mqttHelper.disconnect();
//        }
//    }
//}
