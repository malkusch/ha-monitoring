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
    private final int failureThreshold;
    private final int successThreshold;
    private final Duration delay;

    @SafeVarargs
    public CircuitBreaker(int failureThreshold, int successThreshold, Duration delay,
            Class<? extends Throwable>... exceptions) {

        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.delay = delay;

        breaker = Failsafe.with(dev.failsafe.CircuitBreaker.<R> builder() //
                .handle(exceptions) //
                .withFailureThreshold(failureThreshold) //
                .withDelay(delay) //
                .withSuccessThreshold(successThreshold) //
                .onOpen(it -> log.warn("Circuit breaker opened")) //
                .onClose(it -> log.info("Circuit breaker closed")) //
                .build());
    }

    @Data
    public static class Properties {
        private int failureThreshold;
        private int successThreshold;
        private Duration delay;
    }

    @SafeVarargs
    public CircuitBreaker(Properties properties, Class<? extends Throwable>... exceptions) {
        this(properties.failureThreshold, properties.successThreshold, properties.delay, exceptions);
    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        private static final long serialVersionUID = -6011504260051976020L;

        CircuitBreakerOpenException(Throwable cause) {
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
