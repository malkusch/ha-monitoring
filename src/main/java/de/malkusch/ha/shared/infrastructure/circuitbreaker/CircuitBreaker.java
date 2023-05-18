package de.malkusch.ha.shared.infrastructure.circuitbreaker;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.failsafe.CircuitBreaker.State;
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
    private final String name;

    @SafeVarargs
    public CircuitBreaker(String name, int failureThreshold, int successThreshold, Duration delay,
            Class<? extends Throwable>... exceptions) {

        this.name = name;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.delay = delay;

        breaker = Failsafe.with(dev.failsafe.CircuitBreaker.<R> builder() //
                .handle(exceptions) //
                .withFailureThreshold(failureThreshold) //
                .withDelay(delay) //
                .withSuccessThreshold(successThreshold) //
                .onClose(it -> onClose()) //
                .onOpen(it -> onOpen(it.getPreviousState())) //
                .build());
    }

    @Data
    public static class Properties {
        private int failureThreshold;
        private int successThreshold;
        private Duration delay;
    }

    @SafeVarargs
    public CircuitBreaker(String name, Properties properties, Class<? extends Throwable>... exceptions) {
        this(name, properties.failureThreshold, properties.successThreshold, properties.delay, exceptions);
    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        private static final long serialVersionUID = -6011504260051976020L;

        CircuitBreakerOpenException(Throwable cause) {
            super(cause);
        }
    }

    public static class CircuitBreakerOpenedException extends CircuitBreakerOpenException {
        private static final long serialVersionUID = -6011504260051976020L;

        CircuitBreakerOpenedException(Throwable cause) {
            super(cause);
        }
    }

    private final AtomicBoolean throwOpened = new AtomicBoolean(true);

    private void onClose() {
        throwOpened.set(true);
        log.info("Closed circuit breaker {}", name);
    }

    private void onOpen(State previousState) {
        if (previousState == State.CLOSED) {
            log.warn("Opened circuit breaker {}", name);
        }
    }

    public <T extends R> T get(CheckedSupplier<T> supplier) throws CircuitBreakerOpenException {
        try {
            return breaker.get(supplier);

        } catch (FailsafeException e) {
            if (throwOpened.compareAndExchange(true, false)) {
                throw new CircuitBreakerOpenedException(e.getCause());
            }
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
        return String.format("%s(failureThreshold=%d, successThreshold=%d, delay=%s)", name, failureThreshold,
                successThreshold, delay);
    }
}
