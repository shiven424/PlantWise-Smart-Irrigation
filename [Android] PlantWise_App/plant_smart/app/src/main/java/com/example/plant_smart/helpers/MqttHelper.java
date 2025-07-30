package com.example.plant_smart.helpers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.plant_smart.activities.ImageViewActivity; // Keep if this import is needed elsewhere

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;
import android.content.Intent; // Keep if this import is needed elsewhere


import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.File; // Import File
import java.io.FileOutputStream; // Import FileOutputStream
import java.io.IOException; // Import IOException
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.UUID; // Import UUID


public class MqttHelper {
    private static final String TAG = "MqttHelper";
    private static final String SERVER_URI = "tcp://192.168.106.42:1883";

    public static final String TOPIC_SENSORS = "plant/sensors";
    public static final String TOPIC_IMAGE = "plant/image";
    // >>> Added: New topic for notifications <<<
    public static final String TOPIC_NOTIFICATIONS = "plant/notifications";

    private MqttAndroidClient client;
    private Context applicationContext;

    private final Handler pingHandler;
    private final Runnable pingRunnable;
    private String lastUsedTopicForSubscribe;

    private final Handler reconnectHandler;
    private final Runnable reconnectRunnable;
    private static final long RECONNECT_DELAY_MS = 10000;

    // Listener for incoming MQTT messages (general)
    public interface MqttMessageListener {
        void onMessageReceived(String topic, MqttMessage message);
    }

    private List<MqttMessageListener> messageListeners = new ArrayList<>();

    public void addMessageListener(MqttMessageListener listener) {
        if (listener != null && !messageListeners.contains(listener)) {
            messageListeners.add(listener);
            Log.d(TAG, "General message listener added. Total listeners: " + messageListeners.size());
        }
    }

    public void removeMessageListener(MqttMessageListener listener) {
        if (listener != null && messageListeners.remove(listener)) {
            Log.d(TAG, "General message listener removed. Total listeners: " + messageListeners.size());
        }
    }

    private void notifyMessageListeners(String topic, MqttMessage message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            List<MqttMessageListener> listenersCopy = new ArrayList<>(messageListeners);
            for (MqttMessageListener listener : listenersCopy) {
                try {
                    listener.onMessageReceived(topic, message);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying general message listener", e);
                }
            }
        });
    }

    // Listener for Image Messages (passing file path)
    public interface MqttImageListener {
        void onImageReceived(String topic, String imageFilePath);
    }

    private List<MqttImageListener> imageListeners = new ArrayList<>();

    public void addImageListener(MqttImageListener listener) {
        if (listener != null && !imageListeners.contains(listener)) {
            imageListeners.add(listener);
            Log.d(TAG, "Image message listener added. Total image listeners: " + imageListeners.size());
        }
    }

    public void removeImageListener(MqttImageListener listener) {
        if (listener != null && imageListeners.remove(listener)) {
            Log.d(TAG, "Image message listener removed. Total image listeners: " + imageListeners.size());
        }
    }

    // Changed signature to pass file path
    private void notifyImageListeners(String topic, String imageFilePath) {
        new Handler(Looper.getMainLooper()).post(() -> {
            List<MqttImageListener> listenersCopy = new ArrayList<>(imageListeners);
            for (MqttImageListener listener : listenersCopy) {
                try {
                    // Pass the file path to the listener
                    listener.onImageReceived(topic, imageFilePath);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying image listener", e);
                }
            }
        });
    }


    public MqttHelper(Context ctx) {
        this.applicationContext = ctx.getApplicationContext();
        initMqttClient();

        this.pingHandler = new Handler(Looper.getMainLooper());
        this.pingRunnable = new Runnable() {
            @Override
            public void run() {
                if (client != null && client.isConnected()) {
                    try {
                        client.publish("plant/ping", "ping".getBytes(), 0, false);
                        Log.d(TAG, "Sent custom ping");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to send ping", e);
                    }
                } else {
                    Log.w(TAG, "Client not connected, skipping ping");
                }
                pingHandler.postDelayed(this, 60_000);
            }
        };

        this.reconnectHandler = new Handler(Looper.getMainLooper());
        this.reconnectRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Attempting to reconnect...");
                attemptConnect();
            }
        };
    }

    private void initMqttClient() {
        String clientId = MqttClient.generateClientId();
        this.client = new MqttAndroidClient(
                applicationContext,
                SERVER_URI,
                clientId,
                Ack.AUTO_ACK
        );

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.d(TAG, "Connection complete (reconnect=" + reconnect + ")");
                stopConnecting();

                // >>> Subscribe to all relevant topics upon connection <<<
                subscribeToTopic(TOPIC_SENSORS);
                subscribeToTopic(TOPIC_IMAGE);
                // >>> Added: Subscribe to the new notification topic <<<
                subscribeToTopic(TOPIC_NOTIFICATIONS);

                startCustomPinging();
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.w(TAG, "Connection lost", cause);
                stopCustomPinging();
                startConnecting(null);
            }

            @Override
            public void messageArrived(
                    String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                Log.d(TAG, "Message Arrived on topic: " + topic + " with payload size: " + payload.length());

                // >>> Distribute messages to appropriate listeners based on topic <<<
                if (TOPIC_SENSORS.equals(topic)) {
                    notifyMessageListeners(topic, message); // Notify general message listeners for sensor data
                } else if (TOPIC_IMAGE.equals(topic)) {
                    // Handle large image payload by saving to file and sending path
                    String imageFilePath = saveImagePayloadToFile(message.getPayload());
                    if (imageFilePath != null) {
                        notifyImageListeners(topic, imageFilePath); // Notify image listeners with file path
                    } else {
                        Log.e(TAG, "Failed to save image payload to file.");
                    }
                } else if (TOPIC_NOTIFICATIONS.equals(topic)) { // Handle messages on the new notification topic
                    // >>> Notify general message listeners for notification messages <<<
                    notifyMessageListeners(topic, message);
                    // The DashboardActivity will handle fetching the image based on this notification message
                }
                // Add more else if blocks for other topics if needed
            }

            @Override
            public void deliveryComplete(
                    org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                Log.d(TAG, "Delivery complete for token: " + token.getMessageId());
            }
        });
    }

    /**
     * Saves the image payload (base64 bytes) to a temporary file.
     * @param payload The byte array containing the base64 image data.
     * @return The absolute path to the saved file, or null if saving failed.
     */
    private String saveImagePayloadToFile(byte[] payload) {
        File tempFile = null;
        FileOutputStream fos = null;
        try {
            String fileName = "plant_image_" + UUID.randomUUID().toString() + ".txt"; // Using .txt for base64 string
            tempFile = new File(applicationContext.getCacheDir(), fileName);

            fos = new FileOutputStream(tempFile);
            fos.write(payload);

            Log.d(TAG, "Image payload saved to temporary file: " + tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Error saving image payload to temporary file", e);
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing FileOutputStream", e);
                }
            }
        }
    }


    /**
     * Initiates the connection process.
     */
    public void startConnecting(String topic) {
        stopConnecting();
        this.lastUsedTopicForSubscribe = topic;
        attemptConnect();
    }

    /**
     * Stops the periodic reconnection attempts.
     */
    public void stopConnecting() {
        Log.d(TAG, "Stopping periodic connection attempts.");
        if (reconnectHandler != null && reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }
    }

    /**
     * Attempts to connect to the broker.
     */
    private void attemptConnect() {
        if (client == null) {
            Log.e(TAG, "Client is null, cannot attempt connection.");
            return;
        }

        if (client.isConnected()) {
            Log.d(TAG, "Client is already connected. No need to attempt connection.");
            // >>> Resubscribe to all relevant topics <<<
            subscribeToTopic(TOPIC_SENSORS);
            subscribeToTopic(TOPIC_IMAGE);
            subscribeToTopic(TOPIC_NOTIFICATIONS); // Resubscribe to notification topic
            startCustomPinging();
            stopConnecting();
            return;
        }

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        options.setKeepAliveInterval(0);

        Log.d(TAG, "Attempting to connect to broker: " + SERVER_URI);
        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Connection attempt successful.");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Connection attempt failed.", exception);
                    scheduleNextConnectAttempt();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception during connection attempt.", e);
            scheduleNextConnectAttempt();
        }
    }

    /**
     * Schedules the next connection attempt after a defined delay.
     */
    private void scheduleNextConnectAttempt() {
        if (reconnectHandler != null && reconnectRunnable != null) {
            Log.d(TAG, "Scheduling next connection attempt in " + RECONNECT_DELAY_MS / 1000 + " seconds.");
            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        }
    }

    /**
     * Connect to broker and subscribe to the given topic instantly upon connection.
     */
    public void connectAndSubscribe(String topic) {
        Log.d(TAG, "Called connectAndSubscribe. Initiating connection process for fixed topics.");
        startConnecting(null);
    }

    /**
     * Subscribe to a given topic.
     */
    private void subscribeToTopic(String topic) {
        if (client == null) {
            Log.e(TAG, "Client is null, cannot subscribe to topic: " + topic);
            return;
        }
        if (!client.isConnected()) {
            Log.w(TAG, "Cannot subscribe to topic " + topic + ": client not connected. Will attempt on connect.");
            return;
        }
        try {
            client.subscribe(topic, 1, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken t) {
                    Log.d(TAG, "Successfully subscribed to topic: " + topic);
                }
                @Override
                public void onFailure(IMqttToken t, Throwable e) {
                    Log.e(TAG, "Failed to subscribe to topic: " + topic, e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to subscribe to topic: " + topic, e);
        }
    }

    /**
     * Start the custom pinging mechanism.
     */
    public void startCustomPinging() {
        stopCustomPinging();
        Log.d(TAG, "Starting custom pinging.");
        if (pingHandler != null && pingRunnable != null) {
            pingHandler.post(pingRunnable);
        }
    }

    /**
     * Stop the custom pinging mechanism.
     */
    public void stopCustomPinging() {
        Log.d(TAG, "Stopping custom pinging.");
        if (pingHandler != null && pingRunnable != null) {
            pingHandler.removeCallbacks(pingRunnable);
        }
    }

    /**
     * Disconnect from the broker.
     */
    public void disconnect() {
        stopConnecting();
        stopCustomPinging();

        if (client != null && client.isConnected()) {
            try {
                client.disconnect(null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "Successfully disconnected from broker");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "Failed to disconnect", exception);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to disconnect", e);
            }
        } else {
            Log.d(TAG, "Client is not connected or is null, no need to disconnect.");
        }
    }

    /**
     * Get the MqttAndroidClient instance.
     */
    public MqttAndroidClient getClient() {
        return client;
    }

    /**
     * Check if the client is currently connected.
     */
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /**
     * Publish a message to a topic.
     */
    public void publish(String topic, String message) {
        if (client == null) {
            Log.e(TAG, "Client is null, cannot publish.");
            return;
        }
        if (!client.isConnected()) {
            Log.w(TAG, "Client not connected, cannot publish to topic: " + topic + ". Message not buffered.");
            return;
        }
        try {
            client.publish(topic, message.getBytes(), 0, false, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Message published successfully to topic: " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Failed to publish message to topic: " + topic, exception);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to publish message to topic: " + topic, e);
        }
    }


    /**
     * Destroy the client and release resources.
     */
    public void destroy() {
        stopConnecting();
        stopCustomPinging();

        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnectForcibly(0);
                }
                client.unregisterResources();
                client.close();
                client = null;
                Log.d(TAG, "MQTT client destroyed and resources unregistered.");
            } catch (Exception e) {
                Log.e(TAG, "Error during client destruction", e);
            }
        }
        if (pingHandler != null && pingRunnable != null) {
            pingHandler.removeCallbacks(pingRunnable);
        }
        if (reconnectHandler != null && reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }
        lastUsedTopicForSubscribe = null;
        messageListeners.clear();
        imageListeners.clear();
    }
}


//package com.example.plant_smart.helpers;
//
//import android.content.Context;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//
//import com.example.plant_smart.activities.ImageViewActivity;
//
//import info.mqtt.android.service.Ack;
//import info.mqtt.android.service.MqttAndroidClient;
//import android.content.Intent;
//
//
//import org.eclipse.paho.client.mqttv3.IMqttActionListener;
//import org.eclipse.paho.client.mqttv3.IMqttToken;
//import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
//import org.eclipse.paho.client.mqttv3.MqttClient;
//import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
//import org.eclipse.paho.client.mqttv3.MqttException;
//import org.eclipse.paho.client.mqttv3.MqttMessage;
//
//import java.io.File; // Import File
//import java.io.FileOutputStream; // Import FileOutputStream
//import java.io.IOException; // Import IOException
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Locale;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.LinkedList;
//import java.util.UUID; // Import UUID
//
//
//public class MqttHelper {
//    private static final String TAG = "MqttHelper";
//    private static final String SERVER_URI = "tcp://192.168.106.42:1883";
//
//    public static final String TOPIC_SENSORS = "plant/sensors";
//    public static final String TOPIC_IMAGE = "plant/image";
//
//    private MqttAndroidClient client;
//    private Context applicationContext;
//
//    private final Handler pingHandler;
//    private final Runnable pingRunnable;
//    private String lastUsedTopicForSubscribe;
//
//    private final Handler reconnectHandler;
//    private final Runnable reconnectRunnable;
//    private static final long RECONNECT_DELAY_MS = 10000;
//
//    // Listener for incoming MQTT messages (general)
//    public interface MqttMessageListener {
//        void onMessageReceived(String topic, MqttMessage message);
//    }
//
//    private List<MqttMessageListener> messageListeners = new ArrayList<>();
//
//    public void addMessageListener(MqttMessageListener listener) {
//        if (listener != null && !messageListeners.contains(listener)) {
//            messageListeners.add(listener);
//            Log.d(TAG, "General message listener added. Total listeners: " + messageListeners.size());
//        }
//    }
//
//    public void removeMessageListener(MqttMessageListener listener) {
//        if (listener != null && messageListeners.remove(listener)) {
//            Log.d(TAG, "General message listener removed. Total listeners: " + messageListeners.size());
//        }
//    }
//
//    private void notifyMessageListeners(String topic, MqttMessage message) {
//        new Handler(Looper.getMainLooper()).post(() -> {
//            List<MqttMessageListener> listenersCopy = new ArrayList<>(messageListeners);
//            for (MqttMessageListener listener : listenersCopy) {
//                try {
//                    listener.onMessageReceived(topic, message);
//                } catch (Exception e) {
//                    Log.e(TAG, "Error notifying general message listener", e);
//                }
//            }
//        });
//    }
//
//    // >>> New Listener for Image Messages (passing file path) <<<
//    public interface MqttImageListener {
//        // Changed signature to pass file path instead of MqttMessage
//        void onImageReceived(String topic, String imageFilePath);
//    }
//
//    private List<MqttImageListener> imageListeners = new ArrayList<>();
//
//    public void addImageListener(MqttImageListener listener) {
//        if (listener != null && !imageListeners.contains(listener)) {
//            imageListeners.add(listener);
//            Log.d(TAG, "Image message listener added. Total image listeners: " + imageListeners.size());
//        }
//    }
//
//    public void removeImageListener(MqttImageListener listener) {
//        if (listener != null && imageListeners.remove(listener)) {
//            Log.d(TAG, "Image message listener removed. Total image listeners: " + imageListeners.size());
//        }
//    }
//
//    // Changed signature to pass file path
//    private void notifyImageListeners(String topic, String imageFilePath) {
//        new Handler(Looper.getMainLooper()).post(() -> {
//            List<MqttImageListener> listenersCopy = new ArrayList<>(imageListeners);
//            for (MqttImageListener listener : listenersCopy) {
//                try {
//                    // Pass the file path to the listener
//                    listener.onImageReceived(topic, imageFilePath);
//                } catch (Exception e) {
//                    Log.e(TAG, "Error notifying image listener", e);
//                }
//            }
//        });
//    }
//
//
//    public MqttHelper(Context ctx) {
//        this.applicationContext = ctx.getApplicationContext();
//        initMqttClient();
//
//        this.pingHandler = new Handler(Looper.getMainLooper());
//        this.pingRunnable = new Runnable() {
//            @Override
//            public void run() {
//                if (client != null && client.isConnected()) {
//                    try {
//                        client.publish("plant/ping", "ping".getBytes(), 0, false);
//                        Log.d(TAG, "Sent custom ping");
//                    } catch (Exception e) {
//                        Log.e(TAG, "Failed to send ping", e);
//                    }
//                } else {
//                    Log.w(TAG, "Client not connected, skipping ping");
//                }
//                pingHandler.postDelayed(this, 60_000);
//            }
//        };
//
//        this.reconnectHandler = new Handler(Looper.getMainLooper());
//        this.reconnectRunnable = new Runnable() {
//            @Override
//            public void run() {
//                Log.d(TAG, "Attempting to reconnect...");
//                attemptConnect();
//            }
//        };
//    }
//
//    private void initMqttClient() {
//        String clientId = MqttClient.generateClientId();
//        this.client = new MqttAndroidClient(
//                applicationContext,
//                SERVER_URI,
//                clientId,
//                Ack.AUTO_ACK
//        );
//
//        client.setCallback(new MqttCallbackExtended() {
//            @Override
//            public void connectComplete(boolean reconnect, String serverURI) {
//                Log.d(TAG, "Connection complete (reconnect=" + reconnect + ")");
//                stopConnecting();
//
//                subscribeToTopic(TOPIC_SENSORS);
//                subscribeToTopic(TOPIC_IMAGE);
//
//                startCustomPinging();
//            }
//
//            @Override
//            public void connectionLost(Throwable cause) {
//                Log.w(TAG, "Connection lost", cause);
//                stopCustomPinging();
//                startConnecting(null);
//            }
//
//            @Override
//            public void messageArrived(
//                    String topic, MqttMessage message) throws Exception {
//                String payload = new String(message.getPayload());
//                Log.d(TAG, "Message Arrived on topic: " + topic + " with payload size: " + payload.length());
//
//                if (TOPIC_SENSORS.equals(topic)) {
//                    notifyMessageListeners(topic, message);
//                } else if (TOPIC_IMAGE.equals(topic)) {
//                    String imageFilePath = saveImagePayloadToFile(message.getPayload());
//                    if (imageFilePath != null) {
//                        notifyImageListeners(topic, imageFilePath);
//
//                        // >>> Launch full-screen image activity <<<
//                        Intent intent = new Intent(applicationContext, ImageViewActivity.class);
//                        intent.putExtra("imagePath", imageFilePath);
//                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Needed if launching from non-activity context
//                        applicationContext.startActivity(intent);
//
//                    } else {
//                        Log.e(TAG, "Failed to save image payload to file.");
//                    }
//                }
//
//            }
//
//            @Override
//            public void deliveryComplete(
//                    org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
//                Log.d(TAG, "Delivery complete for token: " + token.getMessageId());
//            }
//        });
//    }
//
//    /**
//     * Saves the image payload (base64 bytes) to a temporary file.
//     * @param payload The byte array containing the base64 image data.
//     * @return The absolute path to the saved file, or null if saving failed.
//     */
//    private String saveImagePayloadToFile(byte[] payload) {
//        File tempFile = null;
//        FileOutputStream fos = null;
//        try {
//            // Create a unique temporary file name
//            String fileName = "plant_image_" + UUID.randomUUID().toString() + ".txt"; // Using .txt for base64 string
//            tempFile = new File(applicationContext.getCacheDir(), fileName); // Save to app's cache directory
//
//            fos = new FileOutputStream(tempFile);
//            fos.write(payload); // Write the base64 bytes to the file
//
//            Log.d(TAG, "Image payload saved to temporary file: " + tempFile.getAbsolutePath());
//            return tempFile.getAbsolutePath(); // Return the absolute path
//
//        } catch (IOException e) {
//            Log.e(TAG, "Error saving image payload to temporary file", e);
//            // Clean up the file if it was created but writing failed
//            if (tempFile != null && tempFile.exists()) {
//                tempFile.delete();
//            }
//            return null;
//        } finally {
//            if (fos != null) {
//                try {
//                    fos.close();
//                } catch (IOException e) {
//                    Log.e(TAG, "Error closing FileOutputStream", e);
//                }
//            }
//        }
//    }
//
//
//    /**
//     * Initiates the connection process.
//     */
//    public void startConnecting(String topic) {
//        stopConnecting();
//        this.lastUsedTopicForSubscribe = topic;
//        attemptConnect();
//    }
//
//    /**
//     * Stops the periodic reconnection attempts.
//     */
//    public void stopConnecting() {
//        Log.d(TAG, "Stopping periodic connection attempts.");
//        if (reconnectHandler != null && reconnectRunnable != null) {
//            reconnectHandler.removeCallbacks(reconnectRunnable);
//        }
//    }
//
//    /**
//     * Attempts to connect to the broker.
//     */
//    private void attemptConnect() {
//        if (client == null) {
//            Log.e(TAG, "Client is null, cannot attempt connection.");
//            return;
//        }
//
//        if (client.isConnected()) {
//            Log.d(TAG, "Client is already connected. No need to attempt connection.");
//            subscribeToTopic(TOPIC_SENSORS);
//            subscribeToTopic(TOPIC_IMAGE);
//            startCustomPinging();
//            stopConnecting();
//            return;
//        }
//
//        MqttConnectOptions options = new MqttConnectOptions();
//        options.setAutomaticReconnect(false);
//        options.setCleanSession(true);
//        options.setKeepAliveInterval(0);
//
//        Log.d(TAG, "Attempting to connect to broker: " + SERVER_URI);
//        try {
//            client.connect(options, null, new IMqttActionListener() {
//                @Override
//                public void onSuccess(IMqttToken asyncActionToken) {
//                    Log.d(TAG, "Connection attempt successful.");
//                }
//
//                @Override
//                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//                    Log.e(TAG, "Connection attempt failed.", exception);
//                    scheduleNextConnectAttempt();
//                }
//            });
//        } catch (Exception e) {
//            Log.e(TAG, "Exception during connection attempt.", e);
//            scheduleNextConnectAttempt();
//        }
//    }
//
//    /**
//     * Schedules the next connection attempt after a defined delay.
//     */
//    private void scheduleNextConnectAttempt() {
//        if (reconnectHandler != null && reconnectRunnable != null) {
//            Log.d(TAG, "Scheduling next connection attempt in " + RECONNECT_DELAY_MS / 1000 + " seconds.");
//            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
//        }
//    }
//
//    /**
//     * Connect to broker and subscribe to the given topic instantly upon connection.
//     */
//    public void connectAndSubscribe(String topic) {
//        Log.d(TAG, "Called connectAndSubscribe. Initiating connection process for fixed topics.");
//        startConnecting(null);
//    }
//
//    /**
//     * Subscribe to a given topic.
//     */
//    private void subscribeToTopic(String topic) {
//        if (client == null) {
//            Log.e(TAG, "Client is null, cannot subscribe to topic: " + topic);
//            return;
//        }
//        if (!client.isConnected()) {
//            Log.w(TAG, "Cannot subscribe to topic " + topic + ": client not connected. Will attempt on connect.");
//            return;
//        }
//        try {
//            client.subscribe(topic, 1, null, new IMqttActionListener() {
//                @Override
//                public void onSuccess(IMqttToken t) {
//                    Log.d(TAG, "Successfully subscribed to topic: " + topic);
//                }
//                @Override
//                public void onFailure(IMqttToken t, Throwable e) {
//                    Log.e(TAG, "Failed to subscribe to topic: " + topic, e);
//                }
//            });
//        } catch (Exception e) {
//            Log.e(TAG, "Failed to subscribe to topic: " + topic, e);
//        }
//    }
//
//    /**
//     * Start the custom pinging mechanism.
//     */
//    public void startCustomPinging() {
//        stopCustomPinging();
//        Log.d(TAG, "Starting custom pinging.");
//        if (pingHandler != null && pingRunnable != null) {
//            pingHandler.post(pingRunnable);
//        }
//    }
//
//    /**
//     * Stop the custom pinging mechanism.
//     */
//    public void stopCustomPinging() {
//        Log.d(TAG, "Stopping custom pinging.");
//        if (pingHandler != null && pingRunnable != null) {
//            pingHandler.removeCallbacks(pingRunnable);
//        }
//    }
//
//    /**
//     * Disconnect from the broker.
//     */
//    public void disconnect() {
//        stopConnecting();
//        stopCustomPinging();
//
//        if (client != null && client.isConnected()) {
//            try {
//                client.disconnect(null, new IMqttActionListener() {
//                    @Override
//                    public void onSuccess(IMqttToken asyncActionToken) {
//                        Log.d(TAG, "Successfully disconnected from broker");
//                    }
//
//                    @Override
//                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//                        Log.e(TAG, "Failed to disconnect", exception);
//                    }
//                });
//            } catch (Exception e) {
//                Log.e(TAG, "Failed to disconnect", e);
//            }
//        } else {
//            Log.d(TAG, "Client is not connected or is null, no need to disconnect.");
//        }
//    }
//
//    /**
//     * Get the MqttAndroidClient instance.
//     */
//    public MqttAndroidClient getClient() {
//        return client;
//    }
//
//    /**
//     * Check if the client is currently connected.
//     */
//    public boolean isConnected() {
//        return client != null && client.isConnected();
//    }
//
//    /**
//     * Publish a message to a topic.
//     */
//    public void publish(String topic, String message) {
//        if (client == null) {
//            Log.e(TAG, "Client is null, cannot publish.");
//            return;
//        }
//        if (!client.isConnected()) {
//            Log.w(TAG, "Client not connected, cannot publish to topic: " + topic + ". Message not buffered.");
//            return;
//        }
//        try {
//            client.publish(topic, message.getBytes(), 0, false, null, new IMqttActionListener() {
//                @Override
//                public void onSuccess(IMqttToken asyncActionToken) {
//                    Log.d(TAG, "Message published successfully to topic: " + topic);
//                }
//
//                @Override
//                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//                    Log.e(TAG, "Failed to publish message to topic: " + topic, exception);
//                }
//            });
//        } catch (Exception e) {
//            Log.e(TAG, "Failed to publish message to topic: " + topic, e);
//        }
//    }
//
//
//    /**
//     * Destroy the client and release resources.
//     */
//    public void destroy() {
//        stopConnecting();
//        stopCustomPinging();
//
//        if (client != null) {
//            try {
//                if (client.isConnected()) {
//                    client.disconnectForcibly(0);
//                }
//                client.unregisterResources();
//                client.close();
//                client = null;
//                Log.d(TAG, "MQTT client destroyed and resources unregistered.");
//            } catch (Exception e) {
//                Log.e(TAG, "Error during client destruction", e);
//            }
//        }
//        if (pingHandler != null && pingRunnable != null) {
//            pingHandler.removeCallbacks(pingRunnable);
//        }
//        if (reconnectHandler != null && reconnectRunnable != null) {
//            reconnectHandler.removeCallbacks(reconnectRunnable);
//        }
//        lastUsedTopicForSubscribe = null;
//        messageListeners.clear();
//        imageListeners.clear(); // Clear image listeners
//    }
//}
//
////package com.example.plant_smart.helpers;
////
////import android.content.Context;
////import android.os.Handler;
////import android.os.Looper;
////import android.util.Log;
////
////import info.mqtt.android.service.Ack;
////import info.mqtt.android.service.MqttAndroidClient;
////
////import org.eclipse.paho.client.mqttv3.IMqttActionListener;
////import org.eclipse.paho.client.mqttv3.IMqttToken;
////import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
////import org.eclipse.paho.client.mqttv3.MqttClient;
////import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
////import org.eclipse.paho.client.mqttv3.MqttException;
////
////public class MqttHelper {
////    private static final String TAG = "MqttHelper";
////    // Added common MQTT port 1883, confirm your broker uses this port
////    private static final String SERVER_URI = "tcp://192.168.106.239:1883";
////
////    private MqttAndroidClient client; // Changed to non-final as it can be nullified in destroy()
////    private Context applicationContext; // Store application context
////
////    private final Handler pingHandler;
////    private final Runnable pingRunnable;
////    private String lastUsedTopicForSubscribe; // To hold the topic for subscription in connectComplete
////
////    // Handler and Runnable for periodic connection attempts
////    private final Handler reconnectHandler;
////    private final Runnable reconnectRunnable;
////    private static final long RECONNECT_DELAY_MS = 10000; // 10 seconds
////
////    // >>> IMPORTANT: The 'Invalid ClientHandle' error you encountered previously is a runtime issue
////    // likely caused by the MqttHelper/MqttAndroidClient instance's lifecycle.
////    // To reliably fix this, manage this MqttHelper instance as a Singleton or within a dedicated Android Service
////    // that lives as long as your application process needs the MQTT connection.
////    // Creating a new MqttHelper in an Activity's onCreate can lead to issues with the background service.
////
////    public MqttHelper(Context ctx) {
////        this.applicationContext = ctx.getApplicationContext();
////        initMqttClient(); // Initialize the client
////
////        // Your custom ping handler (fires periodically)
////        this.pingHandler = new Handler(Looper.getMainLooper());
////        this.pingRunnable = new Runnable() {
////            @Override
////            public void run() {
////                if (client != null && client.isConnected()) { // Added null check for client
////                    try {
////                        // Publish ping with QoS 0, not retained
////                        client.publish("plant/ping", "ping".getBytes(), 0, false);
////                        Log.d(TAG, "Sent custom ping");
////                    } catch (Exception e) { // Using general Exception catch as in your original style
////                        Log.e(TAG, "Failed to send ping", e);
////                        // Consider attempting to reconnect here if sending ping fails
////                    }
////                } else {
////                    Log.w(TAG, "Client not connected, skipping ping");
////                }
////                // schedule next ping (e.g., every 60 seconds)
////                pingHandler.postDelayed(this, 60_000); // Schedule next ping
////            }
////        };
////
////        // Handler and Runnable for periodic reconnection attempts
////        this.reconnectHandler = new Handler(Looper.getMainLooper());
////        this.reconnectRunnable = new Runnable() {
////            @Override
////            public void run() {
////                Log.d(TAG, "Attempting to reconnect...");
////                // Call the connect logic
////                // We'll use the same logic as connectAndSubscribe but without re-setting callbacks
////                attemptConnect();
////            }
////        };
////    }
////
////    private void initMqttClient() {
////        String clientId = MqttClient.generateClientId();
////        this.client = new MqttAndroidClient(
////                applicationContext,
////                SERVER_URI,
////                clientId,
////                Ack.AUTO_ACK // Using AUTO_ACK
////        );
////
////        // Set callback before connecting
////        client.setCallback(new MqttCallbackExtended() {
////            @Override
////            public void connectComplete(boolean reconnect, String serverURI) {
////                Log.d(TAG, "Connection complete (reconnect=" + reconnect + ")");
////                // Stop scheduled reconnection attempts on successful connect
////                stopConnecting();
////
////                // >>> Subscribing instantly as requested, using the last topic from connectAndSubscribe <<<
////                // The 'Invalid ClientHandle' error might still occur here if the MqttHelper instance is short-lived.
////                if (lastUsedTopicForSubscribe != null) {
////                    subscribeToTopic(lastUsedTopicForSubscribe);
////                } else {
////                    Log.w(TAG, "No topic specified for subscription after connect.");
////                }
////
////                startCustomPinging(); // Start your custom ping loop after connection
////            }
////
////            @Override
////            public void connectionLost(Throwable cause) {
////                Log.w(TAG, "Connection lost", cause);
////                stopCustomPinging(); // Stop pinging if connection is lost
////                // Since setAutomaticReconnect(false), we initiate manual reconnection attempts here
////                startConnecting("plant/sensors"); // Start periodic connection attempts
////            }
////
////            @Override
////            public void messageArrived(
////                    String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) throws Exception { // Keep Exception as messageArrived can throw
////                String payload = new String(message.getPayload());
////                Log.d(TAG, "Message Arrived on topic: " + topic + " with payload: " + payload);
////                // Process the received message here. You'll likely need to
////                // pass this message data back to your Activity or Service using a listener or similar.
////            }
////
////            @Override
////            public void deliveryComplete(
////                    org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
////                Log.d(TAG, "Delivery complete for token: " + token.getMessageId());
////            }
////        });
////    }
////
////    /**
////     * Initiates the connection process and starts periodic reconnection attempts if needed.
////     * This should be called when you want the MQTT connection to be active.
////     */
////    public void startConnecting(String topic) {
////        // Stop any existing reconnection attempts
////        stopConnecting();
////
////        // Store the topic to subscribe to once connected
////        this.lastUsedTopicForSubscribe = topic;
////
////        // Immediately attempt the first connection
////        attemptConnect();
////
////        // Schedule periodic attempts in case the initial one fails
////        // The runnable itself will schedule the next one if needed
////    }
////
////    /**
////     * Stops the periodic reconnection attempts.
////     * Call this when the MQTT connection is no longer needed (e.g., app goes to background, component destroyed).
////     */
////    public void stopConnecting() {
////        Log.d(TAG, "Stopping periodic connection attempts.");
////        if (reconnectHandler != null && reconnectRunnable != null) {
////            reconnectHandler.removeCallbacks(reconnectRunnable);
////        }
////    }
////
////
////    /**
////     * Attempts to connect to the broker. Used internally for initial and periodic attempts.
////     */
////    private void attemptConnect() {
////        if (client == null) {
////            Log.e(TAG, "Client is null, cannot attempt connection.");
////            // Cannot proceed without a client. This indicates a problem with initMqttClient or destroy/re-initialization.
////            return;
////        }
////
////        if (client.isConnected()) {
////            Log.d(TAG, "Client is already connected. No need to attempt connection.");
////            // If already connected, ensure subscription and pinging are active
////            if (lastUsedTopicForSubscribe != null) {
////                subscribeToTopic(lastUsedTopicForSubscribe);
////            }
////            startCustomPinging();
////            stopConnecting(); // Stop scheduled attempts if we are now connected
////            return;
////        }
////
////        MqttConnectOptions options = new MqttConnectOptions();
////        options.setAutomaticReconnect(false); // Still managing reconnect manually/periodically
////        options.setCleanSession(true);
////        options.setKeepAliveInterval(0);   // Using custom ping
////        // options.setConnectionTimeout(30); // Optional
////
////        Log.d(TAG, "Attempting to connect to broker: " + SERVER_URI);
////        try {
////            client.connect(options, null, new IMqttActionListener() {
////                @Override
////                public void onSuccess(IMqttToken asyncActionToken) {
////                    Log.d(TAG, "Connection attempt successful.");
////                    // ConnectionComplete callback will handle subscribing and starting pinging
////                }
////
////                @Override
////                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
////                    Log.e(TAG, "Connection attempt failed.", exception);
////                    // Schedule the next connection attempt after a delay
////                    scheduleNextConnectAttempt();
////                }
////            });
////        } catch (Exception e) { // Using general Exception catch as in your original style
////            Log.e(TAG, "Exception during connection attempt.", e);
////            // Schedule the next connection attempt after a delay
////            scheduleNextConnectAttempt();
////        }
////    }
////
////    /**
////     * Schedules the next connection attempt after a defined delay.
////     */
////    private void scheduleNextConnectAttempt() {
////        if (reconnectHandler != null && reconnectRunnable != null) {
////            Log.d(TAG, "Scheduling next connection attempt in " + RECONNECT_DELAY_MS + " ms.");
////            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
////        }
////    }
////
////
////    /**
////     * Connect to broker and subscribe to the given topic instantly upon connection.
////     * Disables Paho’s own keep-alive and automatic reconnect.
////     *
////     * NOTE: Replaced by startConnecting() for continuous attempts. This method now
////     * primarily serves to store the topic and initiate the connection process.
////     * If you only want a single connection attempt, use startConnecting() and
////     * manage subsequent attempts manually if needed.
////     */
////    public void connectAndSubscribe(String topic) {
////        // This method now primarily directs to the new connection attempt logic
////        Log.d(TAG, "Called connectAndSubscribe. Initiating connection process for topic: " + topic);
////        startConnecting(topic);
////    }
////
////    /**
////     * Subscribe to a given topic. Should be called after successful connection or if already connected.
////     */
////    private void subscribeToTopic(String topic) {
////        if (client == null) { // Added null check for client
////            Log.e(TAG, "Client is null, cannot subscribe to topic: " + topic);
////            return;
////        }
////        if (!client.isConnected()) {
////            Log.w(TAG, "Cannot subscribe to topic " + topic + ": client not connected.");
////            // With AutomaticReconnect(false), this subscription won't be retried automatically on reconnect.
////            // The periodic reconnect attempts will eventually lead to connectComplete, which calls this method.
////            return;
////        }
////        try {
////            // QoS 1, retain false
////            client.subscribe(topic, 1, null, new IMqttActionListener() {
////                @Override
////                public void onSuccess(IMqttToken t) {
////                    Log.d(TAG, "Successfully subscribed to topic: " + topic);
////                }
////                @Override
////                public void onFailure(IMqttToken t, Throwable e) {
////                    Log.e(TAG, "Failed to subscribe to topic: " + topic, e);
////                    // Handle subscription failure
////                }
////            });
////        } catch (Exception e) { // Using general Exception catch as in your original style
////            Log.e(TAG, "Failed to subscribe to topic: " + topic, e);
////            // Handle subscription failure
////        }
////    }
////
////    /**
////     * Start the custom pinging mechanism.
////     */
////    public void startCustomPinging() {
////        // Ensure previous callbacks are removed to avoid duplicate pings
////        stopCustomPinging();
////        Log.d(TAG, "Starting custom pinging.");
////        if (pingHandler != null && pingRunnable != null) { // Added null checks
////            pingHandler.post(pingRunnable);
////        }
////    }
////
////    /**
////     * Stop the custom pinging mechanism.
////     */
////    public void stopCustomPinging() {
////        Log.d(TAG, "Stopping custom pinging.");
////        if (pingHandler != null && pingRunnable != null) { // Added null checks
////            pingHandler.removeCallbacks(pingRunnable);
////        }
////    }
////
////    /**
////     * Disconnect from the broker.
////     *
////     * >>> IMPORTANT: The log "Client is not connected or is null, no need to disconnect."
////     * is printed here. If you are seeing this log unexpectedly, it means the disconnect()
////     * method is being called from somewhere in your application code when the client
////     * is not in a connected state. Review where disconnect() is called in your other classes.
////     */
////    public void disconnect() {
////        // Stop scheduled connection attempts and pinging when disconnecting
////        stopConnecting();
////        stopCustomPinging();
////
////        if (client != null && client.isConnected()) { // Added null check for client
////            try {
////                client.disconnect(null, new IMqttActionListener() {
////                    @Override
////                    public void onSuccess(IMqttToken asyncActionToken) {
////                        Log.d(TAG, "Successfully disconnected from broker");
////                    }
////
////                    @Override
////                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
////                        Log.e(TAG, "Failed to disconnect", exception);
////                        // Handle disconnect failure
////                    }
////                });
////            } catch (Exception e) { // Using general Exception catch as in your original style
////                Log.e(TAG, "Failed to disconnect", e);
////                // Handle disconnect failure
////            }
////        } else {
////            Log.d(TAG, "Client is not connected or is null, no need to disconnect.");
////        }
////    }
////
////    /**
////     * Get the MqttAndroidClient instance. Use with caution and be mindful of its lifecycle.
////     */
////    public MqttAndroidClient getClient() {
////        return client;
////    }
////
////    /**
////     * Check if the client is currently connected.
////     */
////    public boolean isConnected() {
////        return client != null && client.isConnected(); // Added null check for client
////    }
////
////    /**
////     * Publish a message to a topic with QoS 0, not retained.
////     */
////    public void publish(String topic, String message) {
////        if (client == null) { // Added null check for client
////            Log.e(TAG, "Client is null, cannot publish.");
////            return;
////        }
////        if (!client.isConnected()) {
////            Log.w(TAG, "Client not connected, cannot publish to topic: " + topic + ". Message not buffered.");
////            // If you need offline publishing, configure DisconnectedBufferOptions in initMqttClient
////            return;
////        }
////        try {
////            client.publish(topic, message.getBytes(), 0, false, null, new IMqttActionListener() {
////                @Override
////                public void onSuccess(IMqttToken asyncActionToken) {
////                    Log.d(TAG, "Message published successfully to topic: " + topic);
////                }
////
////                @Override
////                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
////                    Log.e(TAG, "Failed to publish message to topic: " + topic, exception);
////                    // Handle publish failure
////                }
////            });
////        } catch (Exception e) { // Using general Exception catch as in your original style
////            Log.e(TAG, "Failed to publish message to topic: " + topic, e);
////            // Handle publish failure
////        }
////    }
////
////
////    /**
////     * Destroy the client and release resources. Call this when the MqttHelper is no longer needed (e.g., when the Application is exiting).
////     * This is important for cleaning up the background service resources associated with the client handle.
////     */
////    public void destroy() {
////        stopConnecting(); // Stop periodic connection attempts
////        stopCustomPinging(); // Ensure pings are stopped
////
////        if (client != null) {
////            try {
////                if (client.isConnected()) {
////                    // Use disconnect for a clean disconnect, disconnectForcibly if needed quickly
////                    client.disconnectForcibly(0); // 0ms timeout
////                }
////                client.unregisterResources(); // Important for releasing resources associated with the service
////                client.close(); // Close the client
////                client = null; // Explicitly nullify the client reference to prevent using a stale object
////                Log.d(TAG, "MQTT client destroyed and resources unregistered.");
////            } catch (Exception e) { // Using general Exception catch as in your original style
////                Log.e(TAG, "Error during client destruction", e);
////                // Handle destruction failure
////            }
////        }
////        // Ensure any pending Handler callbacks are removed
////        if (pingHandler != null && pingRunnable != null) {
////            pingHandler.removeCallbacks(pingRunnable);
////        }
////        if (reconnectHandler != null && reconnectRunnable != null) {
////            reconnectHandler.removeCallbacks(reconnectRunnable);
////        }
////        lastUsedTopicForSubscribe = null; // Clear the stored topic
////    }
////}
