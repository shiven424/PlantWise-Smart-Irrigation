package com.example.plant_smart.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler; // Import Handler
import android.os.Looper; // Import Looper
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;

import com.example.plant_smart.MyApp;
import com.example.plant_smart.R;
import com.example.plant_smart.helpers.MqttHelper;
// Removed BarChart import
// import com.github.mikephil.charting.charts.BarChart;
// Added LineChart import
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
// Removed BarData import
// import com.github.mikephil.charting.data.BarData;
// Removed BarDataSet import
// import com.github.mikephil.charting.data.BarDataSet;
// Removed BarEntry import
// import com.github.mikephil.charting.data.BarEntry;
// Added Entry import for Line Chart
import com.github.mikephil.charting.data.Entry;
// Added LineData import
import com.github.mikephil.charting.data.LineData;
// Added LineDataSet import
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter; // Import ValueFormatter for X-axis timestamps
import com.github.mikephil.charting.utils.ColorTemplate;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;


// Implement the MqttHelper.MqttMessageListener interface to receive messages
// Keep NavigationView.OnNavigationItemSelectedListener if your layout has a Drawer/Nav View
public class HistoryActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, MqttHelper.MqttMessageListener {

    private static final String TAG = "HistoryActivity";
    private static final int HISTORY_LIST_SIZE = 10; // Number of data points to store in the list view
    private static final int CHART_DATA_POINTS = 10; // Number of data points to show on the chart

    // Data structure to hold sensor readings (using a simple class)
    private static class SensorReading {
        String timestamp;
        double moisture;
        double light;
        double temperature;
        double humidity;

        // Constructor
        SensorReading(String timestamp, double moisture, double light, double temperature, double humidity) {
            this.timestamp = timestamp;
            this.moisture = moisture;
            this.light = light;
            this.temperature = temperature;
            this.humidity = humidity;
        }
    }

    // List to store the history of sensor readings for the RecyclerView (updates on every message)
    private List<SensorReading> sensorHistoryList = new LinkedList<>();

    // List to store the history of sensor readings for the Line Chart (updates every 10 seconds)
    private List<SensorReading> chartDataHistory = new LinkedList<>();

    // Buffer to temporarily store readings received within a chart update interval
    private List<SensorReading> chartUpdateBuffer = new ArrayList<>();


    // UI elements
    private Toolbar toolbar;
    // Keep DrawerLayout and NavigationView if your layout includes them for navigation
    private DrawerLayout drawer;
    private NavigationView navigationView;

    private LineChart lineChart; // Changed to Line Chart
    private RecyclerView recyclerHistory;
    private HistoryAdapter historyAdapter;

    private MqttHelper mqttHelper;

    // Handler and Runnable for periodic chart updates
    private Handler chartUpdateHandler;
    private Runnable chartUpdateRunnable;
    private static final long CHART_UPDATE_INTERVAL_MS = 10000; // 10 seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history); // Ensure this matches your layout file name

        Log.d(TAG, "HistoryActivity onCreate");

        // Set up the toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button (back arrow)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Sensor History"); // Set toolbar title
        }

        // Find UI elements
        // Keep DrawerLayout and NavigationView if your layout includes them
        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        lineChart = findViewById(R.id.lineChart); // Find the LineChart
        recyclerHistory = findViewById(R.id.recyclerHistory);

        // Get the Singleton MqttHelper instance from the Application class
        mqttHelper = MyApp.getInstance().getMqttHelper();
        Log.d(TAG, "Got MqttHelper instance from Application.");

        // >>> Setup RecyclerView <<<
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter(sensorHistoryList); // Use sensorHistoryList for RecyclerView
        recyclerHistory.setAdapter(historyAdapter);

        // >>> Setup Line Chart (Basic Configuration) <<<
        configureLineChart();

        // >>> Setup periodic chart update task <<<
        chartUpdateHandler = new Handler(Looper.getMainLooper());
        chartUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Chart update runnable triggered."); // Log: Runnable started
                // Process the buffer and update the chart
                processChartUpdateBuffer();
                // Schedule the next update
                chartUpdateHandler.postDelayed(this, CHART_UPDATE_INTERVAL_MS);
            }
        };


        // Load any previously saved history here if you implement persistence beyond app lifecycle
        // For now, history is only stored in memory for the current app session.
    }

    // Handle the Up button (back arrow) click in the Toolbar
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "HistoryActivity onResume: Adding MQTT message listener and starting chart updates.");
        if (mqttHelper != null) {
            mqttHelper.addMessageListener(this);
        }
        // Update UI with current data when resuming
        historyAdapter.notifyDataSetChanged();
        updateLineChart(); // Initial chart update on resume
        startChartUpdates(); // Start the periodic chart update task
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "HistoryActivity onPause: Removing MQTT message listener and stopping chart updates.");
        if (mqttHelper != null) {
            mqttHelper.removeMessageListener(this);
        }
        stopChartUpdates(); // Stop the periodic chart update task
        // You might save the current history to persistent storage here
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "HistoryActivity onDestroy"); // Log onDestroy
        // Ensure chart update task is removed if activity is destroyed
        stopChartUpdates();
    }

    // Method to start the periodic chart updates
    private void startChartUpdates() {
        if (chartUpdateHandler != null && chartUpdateRunnable != null) {
            // Clear any pending callbacks before posting
            chartUpdateHandler.removeCallbacks(chartUpdateRunnable);
            chartUpdateHandler.postDelayed(chartUpdateRunnable, CHART_UPDATE_INTERVAL_MS);
            Log.d(TAG, "Chart updates started."); // Log: Updates started
        }
    }

    // Method to stop the periodic chart updates
    private void stopChartUpdates() {
        if (chartUpdateHandler != null && chartUpdateRunnable != null) {
            chartUpdateHandler.removeCallbacks(chartUpdateRunnable);
            Log.d(TAG, "Chart updates stopped."); // Log: Updates stopped
        }
    }

    // Method to process the chart update buffer and update the chart history
    private void processChartUpdateBuffer() {
        Log.d(TAG, "Processing chart update buffer. Buffer size: " + chartUpdateBuffer.size()); // Log: Buffer processing started
        if (!chartUpdateBuffer.isEmpty()) {
            // Take the latest reading from the buffer
            SensorReading latestReading = chartUpdateBuffer.get(chartUpdateBuffer.size() - 1);

            // Add the latest reading to the chart history
            if (chartDataHistory.size() >= CHART_DATA_POINTS) {
                ((LinkedList<SensorReading>) chartDataHistory).removeFirst(); // Remove the oldest
            }
            chartDataHistory.add(latestReading); // Add the new one

            Log.d(TAG, "Processed chart buffer. Added latest reading to chart history. Current chart history size: " + chartDataHistory.size());
            Log.d(TAG, "Latest reading added to chart history: Timestamp=" + latestReading.timestamp + ", Temp=" + latestReading.temperature + ", Hum=" + latestReading.humidity); // Log the actual values

            // Clear the buffer for the next interval
            chartUpdateBuffer.clear();

            // Update the line chart with the new chart history data
            updateLineChart();
        } else {
            Log.d(TAG, "Chart update buffer is empty. No new data in the last " + CHART_UPDATE_INTERVAL_MS/1000 + " seconds.");
        }
    }


    // >>> Implementation of MqttHelper.MqttMessageListener interface <<<
    @Override
    public void onMessageReceived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());

        if ("plant/sensors".equals(topic)) {
            try {
                JSONObject json = new JSONObject(payload);
                double moisture = json.optDouble("moisture", Double.NaN);
                double light = json.optDouble("light", Double.NaN);
                double temp = json.optDouble("temp", Double.NaN);
                double hum = json.optDouble("hum", Double.NaN);

                // Get current timestamp
                String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                SensorReading newReading = new SensorReading(timestamp, moisture, light, temp, hum);

                // Add the new reading to the history list for the RecyclerView
                if (sensorHistoryList.size() >= HISTORY_LIST_SIZE) {
                    ((LinkedList<SensorReading>) sensorHistoryList).removeFirst();
                }
                sensorHistoryList.add(newReading);

                // Add the new reading to the buffer for chart updates
                chartUpdateBuffer.add(newReading);

                Log.d(TAG, "Received message. Added to list view history and chart buffer. List size: " + sensorHistoryList.size() + ", Buffer size: " + chartUpdateBuffer.size());


                // >>> Update RecyclerView on every message arrival <<<
                historyAdapter.notifyDataSetChanged();
                recyclerHistory.scrollToPosition(sensorHistoryList.size() - 1);

                // >>> Removed: updateLineChart() call from here <<<
                // The chart is now updated by the periodic task using the buffer.


            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse JSON payload in HistoryActivity: " + payload, e);
            } catch (Exception e) {
                Log.e(TAG, "An unexpected error occurred while processing MQTT message in HistoryActivity", e);
            }
        }
    }

    // >>> RecyclerView Adapter and ViewHolder <<<

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryViewHolder> {

        private List<SensorReading> data;

        HistoryAdapter(List<SensorReading> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_sensor_reading, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            SensorReading reading = data.get(position);
            holder.bind(reading);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView textTimestamp;
        TextView textItemMoisture;
        TextView textItemLight;
        TextView textItemTemperature;
        TextView textItemHumidity;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);
            textItemMoisture = itemView.findViewById(R.id.textItemMoisture);
            textItemLight = itemView.findViewById(R.id.textItemLight);
            textItemTemperature = itemView.findViewById(R.id.textItemTemperature);
            textItemHumidity = itemView.findViewById(R.id.textItemHumidity);
        }

        void bind(SensorReading reading) {
            textTimestamp.setText(reading.timestamp);
            textItemMoisture.setText(String.format(Locale.getDefault(), "%.1f%%", reading.moisture));
            textItemLight.setText(String.format(Locale.getDefault(), "%.1f%%", reading.light));
            textItemTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", reading.temperature));
            textItemHumidity.setText(String.format(Locale.getDefault(), "%.1f%%", reading.humidity));
        }
    }

    // >>> Line Chart Configuration and Update <<<

    // Method to configure the basic appearance of the Line Chart
    private void configureLineChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setDrawGridBackground(false);

        // Configure X-axis (for timestamps)
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f); // Set granularity
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // Use the index (value) to get the timestamp from the chartDataHistory list
                int index = (int) value;
                if (index >= 0 && index < chartDataHistory.size()) {
                    return chartDataHistory.get(index).timestamp;
                }
                return "";
            }
        });
        xAxis.setLabelRotationAngle(45); // Rotate labels if they overlap
        xAxis.setLabelCount(CHART_DATA_POINTS); // Show a label for each data point on the chart

        // Configure Y-axis (left)
        lineChart.getAxisLeft().setDrawGridLines(true); // Draw grid lines for better readability
        lineChart.getAxisLeft().setAxisMinimum(0f); // Start Y-axis at 0
        // >>> Adjusted max Y-axis value to accommodate values over 100 <<<
        lineChart.getAxisLeft().setAxisMaximum(200f); // Set a higher max value (adjust as needed)

        // Configure Y-axis (right) - disable it
        lineChart.getAxisRight().setEnabled(false);

        // Configure legend
        lineChart.getLegend().setEnabled(true); // Enable legend to show different lines

        lineChart.animateX(1000); // Animate chart appearance
    }

    // Method to update the Line Chart with the latest sensor data history
    private void updateLineChart() {
        if (chartDataHistory.isEmpty()) {
            lineChart.clear();
            lineChart.invalidate();
            Log.d(TAG, "Chart data history is empty. Clearing chart."); // Add this log
            return;
        }

        Log.d(TAG, "Updating line chart with " + chartDataHistory.size() + " data points."); // Add this log

        // Create Entry lists for each sensor type from the chartDataHistory
        ArrayList<Entry> moistureEntries = new ArrayList<>();
        ArrayList<Entry> lightEntries = new ArrayList<>();
        ArrayList<Entry> temperatureEntries = new ArrayList<>();
        ArrayList<Entry> humidityEntries = new ArrayList<>();

        for (int i = 0; i < chartDataHistory.size(); i++) {
            SensorReading reading = chartDataHistory.get(i);
            // Log the data points being added to chart entries
            Log.d(TAG, "Chart Entry " + i + ": Timestamp=" + reading.timestamp + ", Temp=" + reading.temperature + ", Hum=" + reading.humidity);
            // Use the index 'i' as the x-value for the chart
            moistureEntries.add(new Entry(i, (float) reading.moisture));
            lightEntries.add(new Entry(i, (float) reading.light));
            temperatureEntries.add(new Entry(i, (float) reading.temperature));
            humidityEntries.add(new Entry(i, (float) reading.humidity));
        }

        // Create LineDataSet for each sensor
        LineDataSet moistureDataSet = new LineDataSet(moistureEntries, "Moisture");
        moistureDataSet.setColor(Color.parseColor("#F57C00")); // Orangish
        moistureDataSet.setCircleColor(Color.parseColor("#F57C00"));
        moistureDataSet.setDrawValues(false); // Hide value labels on the line

        LineDataSet lightDataSet = new LineDataSet(lightEntries, "Light");
        lightDataSet.setColor(Color.parseColor("#0288D1")); // Cyanish
        lightDataSet.setCircleColor(Color.parseColor("#0288D1"));
        lightDataSet.setDrawValues(false);

        LineDataSet temperatureDataSet = new LineDataSet(temperatureEntries, "Temperature");
        temperatureDataSet.setColor(Color.parseColor("#D32F2F")); // Reddish
        temperatureDataSet.setCircleColor(Color.parseColor("#D32F2F"));
        temperatureDataSet.setDrawValues(false);

        LineDataSet humidityDataSet = new LineDataSet(humidityEntries, "Humidity");
        humidityDataSet.setColor(Color.parseColor("#1976D2")); // Bluish
        humidityDataSet.setCircleColor(Color.parseColor("#1976D2"));
        humidityDataSet.setDrawValues(false);


        // Combine DataSets into LineData
        LineData lineData = new LineData(moistureDataSet, lightDataSet, temperatureDataSet, humidityDataSet);

        lineChart.setData(lineData);
        lineChart.invalidate(); // Refresh the chart
    }

    // >>> End of Line Chart Configuration and Update <<<


    // Handle navigation menu item clicks (Keep if your layout has a Drawer/Nav View)
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Assuming your layout has a DrawerLayout with ID 'drawer_layout'
        // and NavigationView with ID 'nav_view'
        if (drawer != null) {
            drawer.closeDrawer(GravityCompat.START);
        }
        int id = item.getItemId();
        if (id == R.id.nav_dashboard) {
            // Navigate to Dashboard (assuming it's the main activity)
            // Use FLAG_ACTIVITY_CLEAR_TOP to avoid creating multiple instances
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish(); // Finish this activity
        } else if (id == R.id.nav_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            // Don't finish here if you want to be able to go back to History
        } else if (id == R.id.nav_plant_profile) {
            startActivity(new Intent(this, PlantProfileActivity.class));
            // Don't finish here if you want to be able to go back to History
        } else if (id == R.id.nav_logout) {
            // Handle logout - you might want to disconnect MQTT here if it's only needed when logged in
            // Get the MqttHelper singleton
            MqttHelper mqttHelper = MyApp.getInstance().getMqttHelper();
            if (mqttHelper != null) {
                // Option 1: Stop periodic connection attempts (connection might stay alive until broker disconnects)
                mqttHelper.stopConnecting();
                // Option 2: Explicitly disconnect the client (cleaner)
                // mqttHelper.disconnect();
                // Option 3: Destroy the client (if MQTT is truly not needed anymore in the app lifecycle)
                // mqttHelper.destroy();
            }
            // Navigate to Login and clear the back stack
            Intent intent = new Intent(this, LoginActivity.class);
            // Corrected flag name
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish(); // Finish this activity
        }
        return true;
    }

    // Handle back button press (Keep if using DrawerLayout)
    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}



//package com.example.plant_smart.activities;
//
//import android.content.Context;
//import android.content.Intent;
//import android.graphics.Color;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.LayoutInflater;
//import android.view.MenuItem;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ArrayAdapter;
//import android.widget.ListView;
//import android.widget.TextView;
//import android.widget.Toast;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.Toolbar;
//import androidx.cardview.widget.CardView;
//import androidx.core.view.GravityCompat;
//import androidx.drawerlayout.widget.DrawerLayout;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//import com.google.android.material.navigation.NavigationView;
//
//import com.example.plant_smart.MyApp;
//import com.example.plant_smart.R;
//import com.example.plant_smart.helpers.MqttHelper;
//// Removed BarChart import
//// import com.github.mikephil.charting.charts.BarChart;
//// Added LineChart import
//import com.github.mikephil.charting.charts.LineChart;
//import com.github.mikephil.charting.components.XAxis;
//// Removed BarData import
//// import com.github.mikephil.charting.data.BarData;
//// Removed BarDataSet import
//// import com.github.mikephil.charting.data.BarDataSet;
//// Removed BarEntry import
//// import com.github.mikephil.charting.data.BarEntry;
//// Added Entry import for Line Chart
//import com.github.mikephil.charting.data.Entry;
//// Added LineData import
//import com.github.mikephil.charting.data.LineData;
//// Added LineDataSet import
//import com.github.mikephil.charting.data.LineDataSet;
//import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
//import com.github.mikephil.charting.formatter.ValueFormatter; // Import ValueFormatter for X-axis timestamps
//import com.github.mikephil.charting.utils.ColorTemplate;
//
//import org.eclipse.paho.client.mqttv3.MqttMessage;
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Locale;
//
//
//// Implement the MqttHelper.MqttMessageListener interface to receive messages
//// Keep NavigationView.OnNavigationItemSelectedListener if your layout has a Drawer/Nav View
//public class HistoryActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, MqttHelper.MqttMessageListener {
//
//    private static final String TAG = "HistoryActivity";
//    private static final int HISTORY_SIZE = 10; // Number of data points to store
//
//    // Data structure to hold sensor readings (using a simple class)
//    private static class SensorReading {
//        String timestamp;
//        double moisture;
//        double light;
//        double temperature;
//        double humidity;
//
//        // Constructor
//        SensorReading(String timestamp, double moisture, double light, double temperature, double humidity) {
//            this.timestamp = timestamp;
//            this.moisture = moisture;
//            this.light = light;
//            this.temperature = temperature;
//            this.humidity = humidity;
//        }
//    }
//
//    // List to store the history of sensor readings (using LinkedList for easy management of size)
//    private List<SensorReading> sensorHistory = new LinkedList<>();
//
//    // UI elements
//    private Toolbar toolbar;
//    // Keep DrawerLayout and NavigationView if your layout includes them for navigation
//    private DrawerLayout drawer;
//    private NavigationView navigationView;
//
//    private LineChart lineChart; // Changed to Line Chart
//    private RecyclerView recyclerHistory;
//    private HistoryAdapter historyAdapter;
//
//    private MqttHelper mqttHelper;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_history); // Ensure this matches your layout file name
//
//        Log.d(TAG, "HistoryActivity onCreate");
//
//        // Set up the toolbar
//        toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        // Enable the Up button (back arrow)
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//            getSupportActionBar().setDisplayShowHomeEnabled(true);
//            getSupportActionBar().setTitle("Sensor History"); // Set toolbar title
//        }
//
//        // Find UI elements
//        // Keep DrawerLayout and NavigationView if your layout includes them
//        drawer = findViewById(R.id.drawer_layout);
//        navigationView = findViewById(R.id.nav_view);
//        navigationView.setNavigationItemSelectedListener(this);
//
//        lineChart = findViewById(R.id.lineChart); // Find the LineChart
//        recyclerHistory = findViewById(R.id.recyclerHistory);
//
//        // Get the Singleton MqttHelper instance from the Application class
//        mqttHelper = MyApp.getInstance().getMqttHelper();
//        Log.d(TAG, "Got MqttHelper instance from Application.");
//
//        // >>> Setup RecyclerView <<<
//        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
//        historyAdapter = new HistoryAdapter(sensorHistory);
//        recyclerHistory.setAdapter(historyAdapter);
//
//        // >>> Setup Line Chart (Basic Configuration) <<<
//        configureLineChart();
//
//
//        // Load any previously saved history here if you implement persistence beyond app lifecycle
//        // For now, history is only stored in memory for the current app session.
//    }
//
//    // Handle the Up button (back arrow) click in the Toolbar
//    @Override
//    public boolean onSupportNavigateUp() {
//        onBackPressed();
//        return true;
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        Log.d(TAG, "HistoryActivity onResume: Adding MQTT message listener.");
//        if (mqttHelper != null) {
//            mqttHelper.addMessageListener(this);
//        }
//        // Update UI with current data when resuming
//        historyAdapter.notifyDataSetChanged();
//        updateLineChart();
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        Log.d(TAG, "HistoryActivity onPause: Removing MQTT message listener.");
//        if (mqttHelper != null) {
//            mqttHelper.removeMessageListener(this);
//        }
//        // You might save the current history to persistent storage here
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        Log.d(TAG, "HistoryActivity onDestroy");
//    }
//
//    // >>> Implementation of MqttHelper.MqttMessageListener interface <<<
//    @Override
//    public void onMessageReceived(String topic, MqttMessage message) {
//        String payload = new String(message.getPayload());
//
//        if ("plant/sensors".equals(topic)) {
//            try {
//                JSONObject json = new JSONObject(payload);
//                double moisture = json.optDouble("moisture", Double.NaN);
//                double light = json.optDouble("light", Double.NaN);
//                double temp = json.optDouble("temp", Double.NaN);
//                double hum = json.optDouble("hum", Double.NaN);
//
//                String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
//
//                SensorReading newReading = new SensorReading(timestamp, moisture, light, temp, hum);
//
//                if (sensorHistory.size() >= HISTORY_SIZE) {
//                    ((LinkedList<SensorReading>) sensorHistory).removeFirst();
//                }
//                sensorHistory.add(newReading);
//
//                Log.d(TAG, "Added reading to history. Current size: " + sensorHistory.size());
//
//                // >>> Update RecyclerView and Line Chart <<<
//                historyAdapter.notifyDataSetChanged();
//                recyclerHistory.scrollToPosition(sensorHistory.size() - 1);
//                updateLineChart(); // Update the line chart
//
//
//            } catch (JSONException e) {
//                Log.e(TAG, "Failed to parse JSON payload in HistoryActivity: " + payload, e);
//            } catch (Exception e) {
//                Log.e(TAG, "An unexpected error occurred while processing MQTT message in HistoryActivity", e);
//            }
//        }
//    }
//
//    // >>> RecyclerView Adapter and ViewHolder <<<
//
//    private class HistoryAdapter extends RecyclerView.Adapter<HistoryViewHolder> {
//
//        private List<SensorReading> data;
//
//        HistoryAdapter(List<SensorReading> data) {
//            this.data = data;
//        }
//
//        @NonNull
//        @Override
//        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//            View view = LayoutInflater.from(parent.getContext())
//                    .inflate(R.layout.list_item_sensor_reading, parent, false);
//            return new HistoryViewHolder(view);
//        }
//
//        @Override
//        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
//            SensorReading reading = data.get(position);
//            holder.bind(reading);
//        }
//
//        @Override
//        public int getItemCount() {
//            return data.size();
//        }
//    }
//
//    private static class HistoryViewHolder extends RecyclerView.ViewHolder {
//        TextView textTimestamp;
//        TextView textItemMoisture;
//        TextView textItemLight;
//        TextView textItemTemperature;
//        TextView textItemHumidity;
//
//        HistoryViewHolder(@NonNull View itemView) {
//            super(itemView);
//            textTimestamp = itemView.findViewById(R.id.textTimestamp);
//            textItemMoisture = itemView.findViewById(R.id.textItemMoisture);
//            textItemLight = itemView.findViewById(R.id.textItemLight);
//            textItemTemperature = itemView.findViewById(R.id.textItemTemperature);
//            textItemHumidity = itemView.findViewById(R.id.textItemHumidity);
//        }
//
//        void bind(SensorReading reading) {
//            textTimestamp.setText(reading.timestamp);
//            textItemMoisture.setText(String.format(Locale.getDefault(), "%.1f%%", reading.moisture));
//            textItemLight.setText(String.format(Locale.getDefault(), "%.1f%%", reading.light));
//            textItemTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", reading.temperature));
//            textItemHumidity.setText(String.format(Locale.getDefault(), "%.1f%%", reading.humidity));
//        }
//    }
//
//    // >>> Line Chart Configuration and Update <<<
//
//    // Method to configure the basic appearance of the Line Chart
//    private void configureLineChart() {
//        lineChart.getDescription().setEnabled(false);
//        lineChart.setPinchZoom(false);
//        lineChart.setDrawGridBackground(false);
//
//        // Configure X-axis (for timestamps)
//        XAxis xAxis = lineChart.getXAxis();
//        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
//        xAxis.setDrawGridLines(false);
//        xAxis.setGranularity(1f); // Set granularity
//        xAxis.setValueFormatter(new ValueFormatter() {
//            @Override
//            public String getFormattedValue(float value) {
//                // Use the index (value) to get the timestamp from the history list
//                int index = (int) value;
//                if (index >= 0 && index < sensorHistory.size()) {
//                    return sensorHistory.get(index).timestamp;
//                }
//                return "";
//            }
//        });
//        xAxis.setLabelRotationAngle(45); // Rotate labels if they overlap
//
//        // Configure Y-axis (left)
//        lineChart.getAxisLeft().setDrawGridLines(true); // Draw grid lines for better readability
//        lineChart.getAxisLeft().setAxisMinimum(0f); // Start Y-axis at 0
//        lineChart.getAxisLeft().setAxisMaximum(100f); // Set max Y-axis value (adjust if sensor values exceed 100)
//
//        // Configure Y-axis (right) - disable it
//        lineChart.getAxisRight().setEnabled(false);
//
//        // Configure legend
//        lineChart.getLegend().setEnabled(true); // Enable legend to show different lines
//
//        lineChart.animateX(1000); // Animate chart appearance
//    }
//
//    // Method to update the Line Chart with the latest sensor data history
//    private void updateLineChart() {
//        if (sensorHistory.isEmpty()) {
//            lineChart.clear();
//            lineChart.invalidate();
//            return;
//        }
//
//        // Create Entry lists for each sensor type from the history
//        ArrayList<Entry> moistureEntries = new ArrayList<>();
//        ArrayList<Entry> lightEntries = new ArrayList<>();
//        ArrayList<Entry> temperatureEntries = new ArrayList<>();
//        ArrayList<Entry> humidityEntries = new ArrayList<>();
//
//        for (int i = 0; i < sensorHistory.size(); i++) {
//            SensorReading reading = sensorHistory.get(i);
//            // Use the index 'i' as the x-value for the chart
//            moistureEntries.add(new Entry(i, (float) reading.moisture));
//            lightEntries.add(new Entry(i, (float) reading.light));
//            temperatureEntries.add(new Entry(i, (float) reading.temperature));
//            humidityEntries.add(new Entry(i, (float) reading.humidity));
//        }
//
//        // Create LineDataSet for each sensor
//        LineDataSet moistureDataSet = new LineDataSet(moistureEntries, "Moisture");
//        moistureDataSet.setColor(Color.parseColor("#F57C00")); // Orangish
//        moistureDataSet.setCircleColor(Color.parseColor("#F57C00"));
//        moistureDataSet.setDrawValues(false); // Hide value labels on the line
//
//        LineDataSet lightDataSet = new LineDataSet(lightEntries, "Light");
//        lightDataSet.setColor(Color.parseColor("#0288D1")); // Cyanish
//        lightDataSet.setCircleColor(Color.parseColor("#0288D1"));
//        lightDataSet.setDrawValues(false);
//
//        LineDataSet temperatureDataSet = new LineDataSet(temperatureEntries, "Temperature");
//        temperatureDataSet.setColor(Color.parseColor("#D32F2F")); // Reddish
//        temperatureDataSet.setCircleColor(Color.parseColor("#D32F2F"));
//        temperatureDataSet.setDrawValues(false);
//
//        LineDataSet humidityDataSet = new LineDataSet(humidityEntries, "Humidity");
//        humidityDataSet.setColor(Color.parseColor("#1976D2")); // Bluish
//        humidityDataSet.setCircleColor(Color.parseColor("#1976D2"));
//        humidityDataSet.setDrawValues(false);
//
//
//        // Combine DataSets into LineData
//        LineData lineData = new LineData(moistureDataSet, lightDataSet, temperatureDataSet, humidityDataSet);
//
//        lineChart.setData(lineData);
//        lineChart.invalidate(); // Refresh the chart
//    }
//
//    // >>> End of Line Chart Configuration and Update <<<
//
//
//    // Handle navigation menu item clicks (Keep if your layout has a Drawer/Nav View)
//    @Override
//    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
//        // Assuming your layout has a DrawerLayout with ID 'drawer_layout'
//        // and NavigationView with ID 'nav_view'
//        if (drawer != null) {
//            drawer.closeDrawer(GravityCompat.START);
//        }
//        int id = item.getItemId();
//        if (id == R.id.nav_dashboard) {
//            // Navigate to Dashboard (assuming it's the main activity)
//            // Use FLAG_ACTIVITY_CLEAR_TOP to avoid creating multiple instances
//            Intent intent = new Intent(this, DashboardActivity.class);
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(intent);
//            finish(); // Finish this activity
//        } else if (id == R.id.nav_profile) {
//            startActivity(new Intent(this, ProfileActivity.class));
//            // Don't finish here if you want to be able to go back to History
//        } else if (id == R.id.nav_plant_profile) {
//            startActivity(new Intent(this, PlantProfileActivity.class));
//            // Don't finish here if you want to be able to go back to History
//        } else if (id == R.id.nav_logout) {
//            // Handle logout - you might want to disconnect MQTT here if it's only needed when logged in
//            // Get the MqttHelper singleton
//            MqttHelper mqttHelper = MyApp.getInstance().getMqttHelper();
//            if (mqttHelper != null) {
//                // Option 1: Stop periodic connection attempts (connection might stay alive until broker disconnects)
//                mqttHelper.stopConnecting();
//                // Option 2: Explicitly disconnect the client (cleaner)
//                // mqttHelper.disconnect();
//                // Option 3: Destroy the client (if MQTT is truly not needed anymore in the app lifecycle)
//                // mqttHelper.destroy();
//            }
//            // Navigate to Login and clear the back stack
//            Intent intent = new Intent(this, LoginActivity.class);
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK); // Corrected flag name
//            startActivity(intent);
//            finish(); // Finish this activity
//        }
//        return true;
//    }
//
//    // Handle back button press (Keep if using DrawerLayout)
//    @Override
//    public void onBackPressed() {
//        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
//            drawer.closeDrawer(GravityCompat.START);
//        } else {
//            super.onBackPressed();
//        }
//    }
//}
//
//
//
