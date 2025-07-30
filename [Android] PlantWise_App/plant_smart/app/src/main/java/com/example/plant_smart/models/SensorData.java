package com.example.plant_smart.models;

// Model class for current sensor readings
public class SensorData {
    private int humidity;
    private int soilMoisture;
    private int brightness;

    public SensorData(int humidity, int soilMoisture, int brightness) {
        this.humidity = humidity;
        this.soilMoisture = soilMoisture;
        this.brightness = brightness;
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
