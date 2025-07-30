package com.example.plant_smart.helpers;

import com.example.plant_smart.models.SensorData;
import com.example.plant_smart.models.HistoryData;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Helper class for generating dummy data
public class MockDataGenerator {

    private static final Random random = new Random();

    // Generate one random sensor reading
    public static SensorData generateSensorData() {
        int humidity = random.nextInt(101); // 0 - 100%
        int soilMoisture = random.nextInt(101);
        int brightness = 100 + random.nextInt(900); // 100 to 1000 lux
        return new SensorData(humidity, soilMoisture, brightness);
    }

    // Generate a list of history entries for last 24 hours
    public static List<HistoryData> generateHistoryData(int hours) {
        List<HistoryData> historyList = new ArrayList<>();
        for (int i = 0; i < hours; i++) {
            String hourLabel = (i + 1) + ":00";
            int humidity = 20 + random.nextInt(61); // 20% - 80%
            int soilMoisture = 20 + random.nextInt(61);
            int brightness = 100 + random.nextInt(900); // 100 to 1000 lux
            historyList.add(new HistoryData(hourLabel, humidity, soilMoisture, brightness));
        }
        return historyList;
    }
}
