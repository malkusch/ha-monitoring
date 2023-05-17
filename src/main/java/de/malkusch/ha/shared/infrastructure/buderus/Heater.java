package de.malkusch.ha.shared.infrastructure.buderus;

import java.io.IOException;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.km200.KM200;
import de.malkusch.km200.KM200Exception;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class Heater {

    private final KM200 km200;
    private final CircuitBreaker<Double> breaker;

    public double query(String path) throws KM200Exception, IOException, InterruptedException {
        return breaker.get(() -> km200.queryDouble(path));
    }
}
