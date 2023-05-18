package de.malkusch.ha.monitoring.infrastructure.niu;

import java.io.IOException;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.niu.Niu.BatteryInfo;
import de.malkusch.niu.Niu.Odometer;
import de.malkusch.niu.Niu.Vehicle;
import de.malkusch.niu.Niu.VehicleInfo;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class Niu {

    private final de.malkusch.niu.Niu api;
    private final CircuitBreaker<Object> breaker;

    public Odometer odometer(String serialNumber) throws IOException {
        return breaker.get(() -> api.odometer(serialNumber));
    }

    public Vehicle[] vehicles() throws IOException {
        return breaker.get(() -> api.vehicles());
    }

    public VehicleInfo vehicle(String serialNumber) throws IOException {
        return breaker.get(() -> api.vehicle(serialNumber));
    }

    public BatteryInfo batteryInfo(String serialNumber) throws IOException {
        return breaker.get(() -> api.batteryInfo(serialNumber));
    }
}
