package de.malkusch.ha.monitoring.infrastructure;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

import javax.annotation.PostConstruct;

import org.springframework.scheduling.annotation.Scheduled;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
final class ScheduledPoller implements Poller {

    private final Poller poller;

    @Override
    @Scheduled(fixedRateString = "${monitoring.updateRate}")
    @PostConstruct
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
