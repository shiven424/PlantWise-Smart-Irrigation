package com.example.plant_smart; // Replace with your actual package name

import android.app.Application; // Import the Application class
import android.util.Log;
import com.example.plant_smart.helpers.MqttHelper;

// >>> Corrected: Extends android.app.Application <<<
public class MyApp extends Application {

    private static final String TAG = "MyApp";
    private MqttHelper mqttHelper;
    private static MyApp instance;

    public static MyApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.d(TAG, "Application onCreate: Initializing MqttHelper and starting connection process.");
        // Initialize the MqttHelper here. It will live as long as the application process.
        mqttHelper = new MqttHelper(this);

        // Start the periodic connection attempts as soon as the application launches
        // This will attempt to connect immediately and then every 10 seconds if needed,
        // independently of which Activity is currently visible.
        mqttHelper.startConnecting("plant/sensors"); // Start connecting and subscribing to the default topic

        // Note: If your topic changes later in the app, you'll need a way to update
        // the subscription via the MqttHelper instance.
    }

    public MqttHelper getMqttHelper() {
        // Provide access to the single MqttHelper instance
        return mqttHelper;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "Application onTerminate: Destroying MqttHelper");
        // Clean up MQTT resources when the application process is terminated
        if (mqttHelper != null) {
            mqttHelper.destroy();
        }
    }
}
