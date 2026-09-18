package com.valor.tracking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "valor.tracking")
class TrackingProperties {
    private int geofenceRadiusMeters = 150;
    private int backgroundIntervalSeconds = 300;

    int geofenceRadiusMeters() { return geofenceRadiusMeters; }
    public void setGeofenceRadiusMeters(int geofenceRadiusMeters) {
        this.geofenceRadiusMeters = geofenceRadiusMeters > 0 ? geofenceRadiusMeters : 150;
    }

    int backgroundIntervalSeconds() { return backgroundIntervalSeconds; }
    public void setBackgroundIntervalSeconds(int backgroundIntervalSeconds) {
        this.backgroundIntervalSeconds = Math.max(60, backgroundIntervalSeconds);
    }
}
