package de.malkusch.ha.monitoring.infrastructure;

import java.io.IOException;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;

final class OfflinePoller implements Poller {

    private final Poller poller;
    private final CircuitBreaker<Void> circuitBreaker;

    public OfflinePoller(Poller poller, CircuitBreaker.Properties properties) {
        this.poller = poller;
        this.circuitBreaker = new CircuitBreaker<>(properties, IOException.class);
    }

    @Override
    public void update() throws IOException, InterruptedException {
        try {
            circuitBreaker.run(poller::update);

        } catch (CircuitBreakerOpenException e) {
        }
    }
}
