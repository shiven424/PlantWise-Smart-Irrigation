package com.example.plant_smart.models;

// Model class for historical data entries
public class HistoryData {
    private String hourLabel; // Example: "10:00 AM"
    private int humidity;
    private int soilMoisture;
    private int brightness;

    public HistoryData(String hourLabel, int humidity, int soilMoisture, int brightness) {
        this.hourLabel = hourLabel;
        this.humidity = humidity;
        this.soilMoisture = soilMoisture;
        this.brightness = brightness;
    }

    public String getTimestamp() {
        return hourLabel;
    }

    public int getHumidity() {
        return humidity;
    }

    public int getSoilMoisture() {
        return soilMoisture;
    }

    public int getBrightness() {
        return brightness;
    }
}
