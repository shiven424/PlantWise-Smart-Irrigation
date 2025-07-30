
package com.example.plant_smart.activities;

import android.Manifest;
import android.app.NotificationChannel; // Import NotificationChannel
import android.app.NotificationManager; // Import NotificationManager
import android.app.PendingIntent; // Import PendingIntent
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager; // Import PackageManager
import android.graphics.Bitmap; // Import Bitmap
import android.graphics.BitmapFactory; // Import BitmapFactory
import android.os.Build; // Import Build
import android.os.Bundle;
import android.text.Editable; // Keep if needed for TextWatcher elsewhere
import android.text.TextWatcher; // Keep if needed elsewhere
import android.util.Base64; // Import Base64
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView; // Keep if needed elsewhere
import android.widget.Switch; // Keep if needed elsewhere
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat; // Import NotificationCompat
import androidx.core.app.NotificationManagerCompat; // Import NotificationManagerCompat
import androidx.core.content.ContextCompat; // Import ContextCompat
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.navigation.NavigationView;

import com.example.plant_smart.MyApp;
import com.example.plant_smart.R;
import com.example.plant_smart.helpers.MqttHelper;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import android.app.Notification;
import java.lang.SecurityException; // Import SecurityException


// Implement both MqttHelper.MqttMessageListener (for sensor data) and MqttHelper.MqttImageListener
public class DashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, MqttHelper.MqttMessageListener, MqttHelper.MqttImageListener {

    private static final String TAG = "DashboardActivity";
    private static final String PREFS_NAME = "PlantSmartPrefs"; // SharedPreferences file name
    private static final String THRESHOLD_KEY = "wateringThreshold"; // Key for saving threshold

    // MQTT Topics for sending commands and config
    private static final String TOPIC_CONTROL_APP = "plant/control/app";
    private static final String TOPIC_CONFIG = "plant/config";

    // Notification Channel details for image notifications
    private static final String CHANNEL_ID = "image_notification_channel";
    private static final CharSequence CHANNEL_NAME = "Plant Images";
    private static final String CHANNEL_DESCRIPTION = "Notifications for incoming plant images";

    // Permission Request Code (can be any unique integer)
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;


    private DrawerLayout drawer;
    private NavigationView navigationView;

    // TextViews to display sensor data
    private TextView textTemperature;
    private TextView textHumidity;
    private TextView textMoisture;
    private TextView textLight;

    // UI elements for watering control and other controls
    private EditText editThreshold;
    private Button btnSaveThreshold;
    private Button btnPump;
    private Button btnMode;

    private MqttHelper mqttHelper;
    private SharedPreferences preferences;

    // Store the file path of the received image bitmap temporarily if permission needs to be requested
    private String pendingNotificationImageFilePath = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        Log.d(TAG, "DashboardActivity onCreate");

        // Create the Notification Channel (required for Android 8.0 and above)
        createNotificationChannel();

        // Set up toolbar and navigation drawer
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // Find UI elements
        textTemperature = findViewById(R.id.textTemperature);
        textHumidity = findViewById(R.id.textHumidity);
        textMoisture = findViewById(R.id.textMoisture);
        textLight = findViewById(R.id.textLight);

        editThreshold = findViewById(R.id.editThreshold);
        btnSaveThreshold = findViewById(R.id.btnSaveThreshold);
        btnPump = findViewById(R.id.btnPump);
        btnMode = findViewById(R.id.btnMode);

        // Get the Singleton MqttHelper instance
        mqttHelper = MyApp.getInstance().getMqttHelper();
        Log.d(TAG, "Got MqttHelper instance from Application.");

        // Initialize SharedPreferences
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load saved threshold
        int savedThreshold = preferences.getInt(THRESHOLD_KEY, 50);
        editThreshold.setText(String.valueOf(savedThreshold));

        btnSaveThreshold.setOnClickListener(v -> {
            String thresholdStr = editThreshold.getText().toString().trim();
            if (!thresholdStr.isEmpty()) {
                try {
                    int threshold = Integer.parseInt(thresholdStr);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt(THRESHOLD_KEY, threshold);
                    editor.apply();
                    Log.d(TAG, "Threshold saved: " + threshold);
                    Toast.makeText(this, "Threshold saved!", Toast.LENGTH_SHORT).show();

                    if (mqttHelper != null && mqttHelper.isConnected()) {
                        JSONObject configJson = new JSONObject();
                        configJson.put("threshold", threshold);
                        mqttHelper.publish(TOPIC_CONFIG, configJson.toString());
                        Log.d(TAG, "Published config (threshold) to topic: " + TOPIC_CONFIG);
                    } else {
                        Log.w(TAG, "MQTT not connected, cannot publish threshold config.");
                        Toast.makeText(this, "MQTT not connected, threshold saved locally.", Toast.LENGTH_SHORT).show();
                    }

                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid threshold format, not saving.", e);
                    Toast.makeText(this, "Invalid threshold format", Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Log.e(TAG, "Error creating JSON for threshold config.", e);
                    Toast.makeText(this, "Error publishing threshold", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG, "Threshold input is empty, not saving or publishing.");
                Toast.makeText(this, "Threshold cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        // >>> Modified Pump Button Click Listener <<<
        btnPump.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String currentText = btnPump.getText().toString();
                String newText;
                String command;

                if (currentText.equals("Pump Off")) {
                    newText = "Pump On";
                    command = "ON";
                    Toast.makeText(DashboardActivity.this, "Pump turned ON!", Toast.LENGTH_SHORT).show();
                } else { // Current text is "Pump On"
                    newText = "Pump Off";
                    command = "OFF";
                    Toast.makeText(DashboardActivity.this, "Pump turned OFF!", Toast.LENGTH_SHORT).show();
                }

                // Update button text immediately
                btnPump.setText(newText);

                // Publish the command via MQTT
                if (mqttHelper != null && mqttHelper.isConnected()) {
                    JSONObject controlJson = new JSONObject();
                    try {
                        controlJson.put("pump", command); // Key "pump", value "ON" or "OFF"
                        mqttHelper.publish(TOPIC_CONTROL_APP, controlJson.toString());
                        Log.d(TAG, "Published pump command '" + command + "' to topic: " + TOPIC_CONTROL_APP);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error creating JSON for pump control.", e);
                        Toast.makeText(DashboardActivity.this, "Error publishing pump command", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w(TAG, "MQTT not connected, cannot send pump command.");
                    Toast.makeText(DashboardActivity.this, "MQTT not connected, pump state not updated remotely.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        // >>> End of Modified Pump Button Click Listener <<<


        btnMode.setOnClickListener(v -> {
            String currentMode = btnMode.getText().toString();
            String newMode;
            if (currentMode.equals("Automatic")) {
                newMode = "Manual";
                btnPump.setVisibility(View.VISIBLE);
            } else {
                newMode = "Automatic";
                btnPump.setVisibility(View.GONE);
            }
            btnMode.setText(newMode);

            if (mqttHelper != null && mqttHelper.isConnected()) {
                JSONObject configJson = new JSONObject();
                try {
                    configJson.put("mode", newMode.toUpperCase());
                    mqttHelper.publish(TOPIC_CONFIG, configJson.toString());
                    Log.d(TAG, "Published config (mode) to topic: " + TOPIC_CONFIG);
                } catch (JSONException e) {
                    Log.e(TAG, "Error creating JSON for mode config.", e);
                    Toast.makeText(this, "Error publishing mode change", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "MQTT not connected, cannot change mode", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "MQTT not connected, cannot publish mode change.");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "DashboardActivity onResume: Adding MQTT message and image listeners.");
        if (mqttHelper != null) {
            mqttHelper.addMessageListener(this);
            mqttHelper.addImageListener(this); // Add listener for image data
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "DashboardActivity onPause: Removing MQTT message and image listeners.");
        if (mqttHelper != null) {
            mqttHelper.removeMessageListener(this);
            mqttHelper.removeImageListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "DashboardActivity onDestroy");
        // Clean up the pending image file if it exists
        if (pendingNotificationImageFilePath != null) {
            File pendingFile = new File(pendingNotificationImageFilePath);
            if (pendingFile.exists()) {
                pendingFile.delete();
                Log.d(TAG, "Deleted pending image file on destroy.");
            }
        }
    }

    // >>> Implementation of MqttHelper.MqttMessageListener interface (for sensor data) <<<
    @Override
    public void onMessageReceived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        Log.d(TAG, "Received message in Activity: Topic=" + topic + ", Payload=" + payload);

        if (MqttHelper.TOPIC_SENSORS.equals(topic)) {
            // >>> Check if the payload looks like JSON before attempting to parse <<<
            if (payload.trim().startsWith("{") && payload.trim().endsWith("}")) {
                try {
                    JSONObject json = new JSONObject(payload);
                    double moisture = json.optDouble("moisture", Double.NaN);
                    double light = json.optDouble("light", Double.NaN);
                    double temp = json.optDouble("temp", Double.NaN);
                    double hum = json.optDouble("hum", Double.NaN);

                    if (!Double.isNaN(moisture)) {
                        textMoisture.setText(String.format("%.1f%%", moisture));
                    } else {
                        textMoisture.setText("--%");
                    }
                    if (!Double.isNaN(light)) {
                        textLight.setText(String.format("%.1f%%", light));
                    } else {
                        textLight.setText("--%");
                    }
                    if (!Double.isNaN(temp)) {
                        textTemperature.setText(String.format("%.1f°C", temp));
                    } else {
                        textTemperature.setText("--°C");
                    }
                    if (!Double.isNaN(hum)) {
                        textHumidity.setText(String.format("%.1f%%", hum));
                    } else {
                        textHumidity.setText("--%");
                    }

                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse sensor JSON payload: " + payload, e);
                } catch (Exception e) {
                    Log.e(TAG, "An unexpected error occurred while processing sensor message", e);
                }
            } else {
                // >>> Handle the debug string format <<<
                // Example parsing for "DBG: thresh=50 moist=64.1% RAW: soil=1685 light=3"
                // This is a very basic example and assumes the format is consistent.
                try {
                    String[] parts = payload.split(" ");
                    double moisture = Double.NaN;
                    double light = Double.NaN;
                    double temp = Double.NaN;
                    double hum = Double.NaN;

                    for (String part : parts) {
                        if (part.startsWith("moist=")) {
                            try {
                                moisture = Double.parseDouble(part.substring("moist=".length()).replace("%", ""));
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Failed to parse moisture from debug string: " + part, e);
                            }
                        } else if (part.startsWith("light=")) {
                            try {
                                light = Double.parseDouble(part.substring("light=".length()).replace("%", ""));
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Failed to parse light from debug string: " + part, e);
                            }
                        }
                        else if (part.startsWith("temp=")) {
                            try {
                                temp = Double.parseDouble(part.substring("temp=".length()).replace("°C", ""));
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Failed to parse temperature from debug string: " + part, e);
                            }
                        }
                        else if (part.startsWith("hum=")) {
                            try {
                                hum = Double.parseDouble(part.substring("hum=".length()).replace("%", ""));
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Failed to parse humidity from debug string: " + part, e);
                            }
                        }
                    }

                    if (!Double.isNaN(moisture)) {
                        textMoisture.setText(String.format("%.1f%%", moisture));
                    } else {
                        textMoisture.setText("--%");
                    }
                    if (!Double.isNaN(light)) {
                        textLight.setText(String.format("%.1f%%", light));
                    } else {
                        textLight.setText("--%");
                    }
                    if (!Double.isNaN(temp)) {
                        textTemperature.setText(String.format("%.1f°C", temp));
                    } else {
                        textTemperature.setText("--°C");
                    }
                    if (!Double.isNaN(hum)) {
                        textHumidity.setText(String.format("%.1f%%", hum));
                    } else {
                        textHumidity.setText("--%");
                    }

                    Log.w(TAG, "Parsed debug string payload.");

                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse debug string payload: " + payload, e);
                }
            }
        }
    }

    // >>> Implementation of MqttHelper.MqttImageListener interface (handling file path) <<<
    @Override
    public void onImageReceived(String topic, String imageFilePath) {
        Log.d(TAG, "Image file path received on topic: " + topic + ", Path: " + imageFilePath);
        if (MqttHelper.TOPIC_IMAGE.equals(topic)) {
            // Store the file path temporarily in case permission needs to be requested
            pendingNotificationImageFilePath = imageFilePath;

            // >>> Check for POST_NOTIFICATIONS permission on Android 13+ <<<
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    // Permission is granted, show the notification
                    showImageNotificationFromFile(imageFilePath);
                } else {
                    // Permission is not granted, request it from the user
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_PERMISSION_REQUEST_CODE);
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Requesting permission.");
                }
            } else {
                // Android version is below 13, permission is not required at runtime
                showImageNotificationFromFile(imageFilePath);
            }
        }
    }

    // Handle the result of the permission request
    @Override

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted by user.");
                Toast.makeText(this, "Notification permission granted. You will now receive image notifications.", Toast.LENGTH_SHORT).show();

                if (pendingNotificationImageFilePath != null) {
                    showImageNotificationFromFile(pendingNotificationImageFilePath);
                    pendingNotificationImageFilePath = null;
                }

            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied by user.");
                Toast.makeText(this, "Notification permission denied. Image notifications will not be shown.", Toast.LENGTH_LONG).show();
                if (pendingNotificationImageFilePath != null) {
                    File pendingFile = new File(pendingNotificationImageFilePath);
                    if (pendingFile.exists()) {
                        pendingFile.delete();
                        Log.d(TAG, "Deleted pending image file after permission denied.");
                    }
                    pendingNotificationImageFilePath = null;
                }
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created.");
            }
        }
    }

    private void showImageNotificationFromFile(String imageFilePath) {
        Bitmap imageBitmap = null;
        String b64 = null;
        BufferedReader reader = null;
        File imageFile = new File(imageFilePath);

        if (!imageFile.exists()) {
            Log.e(TAG, "Image file does not exist at path: " + imageFilePath);
            Toast.makeText(this, "Error loading image file", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            reader = new BufferedReader(new FileReader(imageFile));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            b64 = stringBuilder.toString();
            Log.d(TAG, "Read base64 string from file. Length: " + b64.length());

            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            if (imageBitmap != null) {
                Log.d(TAG, "Image decoded successfully from file.");
            } else {
                Log.e(TAG, "Failed to decode image data from file.");
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                imageFile.delete();
                return;
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading image file", e);
            Toast.makeText(this, "Error reading image data", Toast.LENGTH_SHORT).show();
            imageFile.delete();
            return;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error decoding base64 string from file", e);
            Toast.makeText(this, "Error decoding image data", Toast.LENGTH_SHORT).show();
            imageFile.delete();
            return;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file reader", e);
                }
            }
        }

        Intent intent = new Intent(this, ImageViewActivity.class); // Correctly targets ImageViewActivity
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Log.d(TAG, "Image intent wala decoded successfully from file."+ imageFilePath);
        intent.putExtra(ImageViewActivity.EXTRA_IMAGE_FILE_PATH, imageFilePath); // Pass the file path

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Your plant is Smiling")
                .setContentText("Tap to view the latest image.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (imageBitmap != null) {
            builder.setLargeIcon(imageBitmap);
            NotificationCompat.BigPictureStyle bigPictureStyle = new NotificationCompat.BigPictureStyle()
                    .bigPicture(imageBitmap)
                    .bigLargeIcon((Bitmap)null); // Hide the large icon in the expanded view
            builder.setStyle(bigPictureStyle);
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        int notificationId = 1;

        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Image notification shown from file.");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when showing notification. Permission likely denied.", e);
        }

        // >>> Clean up the temporary file after showing the notification <<<
        //imageFile.delete();
        Log.d(TAG, "Deleted temporary image file: " + imageFilePath);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (drawer != null) {
            drawer.closeDrawer(GravityCompat.START);
        }
        int id = item.getItemId();
        if (id == R.id.nav_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
        } else if (id == R.id.nav_plant_profile) {
            startActivity(new Intent(this, PlantProfileActivity.class));
        } else if (id == R.id.nav_history) {
            startActivity(new Intent(this, HistoryActivity.class));
        } else if (id == R.id.nav_logout) {
            if (mqttHelper != null) {
                mqttHelper.stopConnecting();
            }
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}


