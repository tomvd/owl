package com.owl.adapter.blitzortung;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("owl.adapters.blitzortung")
public class BlitzortungConfiguration {

    private boolean enabled = false;
    private double latitude = 0;
    private double longitude = 0;
    private double radiusKm = 50;
    private int pollIntervalMinutes = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getRadiusKm() { return radiusKm; }
    public void setRadiusKm(double radiusKm) { this.radiusKm = radiusKm; }

    public int getPollIntervalMinutes() { return pollIntervalMinutes; }
    public void setPollIntervalMinutes(int pollIntervalMinutes) { this.pollIntervalMinutes = pollIntervalMinutes; }
}
