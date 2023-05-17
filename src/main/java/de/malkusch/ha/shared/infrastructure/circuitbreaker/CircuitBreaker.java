package de.malkusch.ha.shared.infrastructure.circuitbreaker;

import java.time.Duration;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.function.CheckedRunnable;
import dev.failsafe.function.CheckedSupplier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CircuitBreaker<R> {

    private final FailsafeExecutor<R> breaker;

    @Data
    public static class Properties {
        private int failureThreshold;
        private int successThreshold;
        private Duration delay;
    }

    private final int failureThreshold;
    private final int successThreshold;
    private final Duration delay;

    public CircuitBreaker(Properties properties, Class<? extends Throwable>... exceptions) {
        failureThreshold = properties.failureThreshold;
        successThreshold = properties.successThreshold;
        delay = properties.delay;

        breaker = Failsafe.with(dev.failsafe.CircuitBreaker.<R> builder() //
                .handle(exceptions) //
                .withFailureThreshold(properties.failureThreshold) //
                .withDelay(properties.delay) //
                .withSuccessThreshold(properties.successThreshold) //
                .onOpen(it -> log.warn("Circuit breaker opened")) //
                .onClose(it -> log.info("Circuit breaker closed")) //
                .build());

    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        private static final long serialVersionUID = -6011504260051976020L;

        public CircuitBreakerOpenException(Throwable cause) {
            super(cause);
        }

    }

    public <T extends R> T get(CheckedSupplier<T> supplier) throws CircuitBreakerOpenException {
        try {
            return breaker.get(supplier);
        } catch (FailsafeException e) {
            throw new CircuitBreakerOpenException(e.getCause());
        }
    }
    
    public void run(CheckedRunnable operation) throws CircuitBreakerOpenException {
        get(() -> {
            operation.run();
            return null;
        });
    }

    @Override
    public String toString() {
        return String.format("failureThreshold=%d, successThreshold=%d, delay=%s", failureThreshold, successThreshold,
                delay);
    }
}
