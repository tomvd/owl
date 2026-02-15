package com.owl.adapter.pmsensor;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("owl.adapters.pmsensor")
public class PmSensorConfiguration {

    private boolean enabled = false;
    private String sensorId;
    private int pollIntervalMinutes = 5;
    private String apiUrl = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }

    public int getPollIntervalMinutes() { return pollIntervalMinutes; }
    public void setPollIntervalMinutes(int pollIntervalMinutes) { this.pollIntervalMinutes = pollIntervalMinutes; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
}
