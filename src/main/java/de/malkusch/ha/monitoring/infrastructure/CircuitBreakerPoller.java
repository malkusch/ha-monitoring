package de.malkusch.ha.monitoring.infrastructure;

import java.io.IOException;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class CircuitBreakerPoller implements Poller {

    private final Poller poller;
    private final CircuitBreaker<Void> circuitBreaker;

    public CircuitBreakerPoller(CircuitBreaker.Properties properties, Poller poller) {
        this.poller = poller;
        this.circuitBreaker = new CircuitBreaker<>(poller.toString(), properties, IOException.class);
        log.info("Configured {} with circuit-breaker: {}", poller, circuitBreaker);
    }

    @Override
    public void update() throws IOException, InterruptedException, CircuitBreakerOpenException {
        circuitBreaker.run(poller::update);
    }

    @Override
    public String toString() {
        return poller.toString();
    }
}
