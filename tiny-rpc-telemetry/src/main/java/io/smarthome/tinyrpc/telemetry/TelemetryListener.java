package io.smarthome.tinyrpc.telemetry;

@FunctionalInterface
public interface TelemetryListener {
    TelemetryListener NOOP = observation -> { };
    void onObservation(TransportObservation observation);
}
