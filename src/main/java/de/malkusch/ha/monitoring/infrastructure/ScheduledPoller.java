package de.malkusch.ha.monitoring.infrastructure;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

import org.springframework.scheduling.annotation.Scheduled;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ScheduledPoller implements Poller {

    private final Poller poller;

    public ScheduledPoller(Poller poller) {
        this.poller = poller;
        log.info("Scheduling polling metric {}", poller);
    }

    @Override
    @Scheduled(fixedRateString = "${monitoring.updateRate}")
    public void update() throws IOException, InterruptedException {
        try {
            poller.update();

        } catch (CircuitBreakerOpenedException e) {
            log.warn("Stop polling metric {}: Open circuit breaker", poller);

        } catch (CircuitBreakerOpenException e) {

        } catch (IOException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                log.warn("Timed out polling {}", poller);
            } else {
                throw e;
            }
        }
    }
}
