package de.malkusch.ha.monitoring.infrastructure;

import de.malkusch.ha.shared.infrastructure.async.AsyncService;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreakerExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

@Slf4j
final class ScheduledPoller implements Poller {

    private final Poller poller;
    private final AsyncService async;

    public ScheduledPoller(Poller poller, AsyncService async) {
        log.info("Scheduling polling metric {}", poller);

        this.poller = poller;
        this.async = async;
    }

    @Override
    public void update() throws IOException, InterruptedException {
        try {
            CircuitBreakerExceptionHandler.<IOException, InterruptedException>withCircuitBreakerLogging(poller::update);

        } catch (IOException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                log.warn("Timed out polling {}", poller);
            } else {
                throw e;
            }
        }
    }

    @Scheduled(fixedRateString = "${monitoring.updateRate}")
    public void updateAsync() {
        async.executeAsync(this::update);
    }
}
