package de.malkusch.ha.shared.infrastructure.circuitbreaker;

import dev.failsafe.CircuitBreaker.State;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.function.CheckedRunnable;
import dev.failsafe.function.CheckedSupplier;
import dev.failsafe.function.ContextualSupplier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public final class CircuitBreaker<R> {

    private final FailsafeExecutor<R> failsafe;
    private final dev.failsafe.CircuitBreaker<R> circuitBreaker;
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

        circuitBreaker = dev.failsafe.CircuitBreaker.<R>builder() //
                .handle(RegisterErrorException.class)
                .handle(exceptions) //
                .withFailureThreshold(failureThreshold) //
                .withDelay(delay) //
                .withSuccessThreshold(successThreshold) //
                .onClose(it -> onClose()) //
                .onOpen(it -> onOpen()) //
                .onHalfOpen(it -> onHalfOpen()) //
                .build();
        failsafe = Failsafe.with(circuitBreaker);
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

    @SafeVarargs
    public CircuitBreaker(String name, CircuitBreaker<?> prototype, Class<? extends Throwable>... exceptions) {
        this(name, prototype.failureThreshold, prototype.successThreshold, prototype.delay, exceptions);
    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        private static final long serialVersionUID = -6011504260051976020L;

        private final CircuitBreaker<?> circuitBreaker;

        CircuitBreakerOpenException(String message, Throwable cause, CircuitBreaker<?> circuitBreaker) {
            super(message, cause);
            this.circuitBreaker = circuitBreaker;
        }

        public CircuitBreaker<?> circuitBreaker() {
            return circuitBreaker;
        }
    }

    public static class CircuitBreakerOpenedException extends CircuitBreakerOpenException {
        private static final long serialVersionUID = -6011504260051976020L;

        CircuitBreakerOpenedException(String message, Throwable cause, CircuitBreaker<?> circuitBreaker) {
            super(message, cause, circuitBreaker);
        }
    }

    public static class CircuitBreakerHalfOpenException extends CircuitBreakerOpenException {
        private static final long serialVersionUID = -6011504260051976020L;

        CircuitBreakerHalfOpenException(String message, Throwable cause, CircuitBreaker<?> circuitBreaker) {
            super(message, cause, circuitBreaker);
        }
    }

    private void onClose() {
        log.debug("Closed circuit breaker {}", name);
    }

    private void onHalfOpen() {
        log.debug("Half opened circuit breaker {}", name);
    }

    private void onOpen() {
        log.debug("Opened circuit breaker {}", name);
    }

    public void close() {
        circuitBreaker.close();
    }

    public boolean isClosed() {
        return circuitBreaker.isClosed();
    }

    public boolean isOpen() {
        return circuitBreaker.isOpen();
    }

    public boolean isHalfOpen() {
        return circuitBreaker.isHalfOpen();
    }

    private static final class RegisterErrorException extends RuntimeException {
    }

    private static final RegisterErrorException REGISTER_ERROR = new RegisterErrorException();

    public <E1 extends Throwable, E2 extends Throwable> void error(CheckedRunnable runnable)
            throws E1, E2, CircuitBreakerOpenException {

        try {
            get(it -> {
                runnable.run();
                throw REGISTER_ERROR;
            });
        } catch (RegisterErrorException ignored) {

        }
    }

    public <E1 extends Throwable, E2 extends Throwable, T extends R> T get(CheckedSupplier<T> supplier)
            throws E1, E2, CircuitBreakerOpenException {

        return get(it -> supplier.get());
    }

    private <E1 extends Throwable, E2 extends Throwable, T extends R> T get(ContextualSupplier<T, T> supplier)
            throws E1, E2, CircuitBreakerOpenException {

        AtomicReference<State> previousState = new AtomicReference<>(circuitBreaker.getState());
        try {
            return failsafe.get(it -> {
                previousState.set(circuitBreaker.getState());
                return supplier.get(it);
            });

        } catch (Throwable e) {
            this.<E1>throwCircuitBreakerException(previousState.get(), e);
            throw new IllegalStateException(e);
        }
    }

    public <E1 extends Throwable, E2 extends Throwable> void run(CheckedRunnable operation)
            throws E1, E2, CircuitBreakerOpenException {

        this.<E1, E2, R>get(it -> {
            operation.run();
            return null;
        });
    }

    private <E1 extends Throwable> void throwCircuitBreakerException(State previousState, Throwable cause) throws E1, CircuitBreakerOpenException {
        var state = circuitBreaker.getState();
        if (state == State.OPEN) {
            switch (previousState) {
                case HALF_OPEN -> throw new CircuitBreakerHalfOpenException("Circuit breaker half open", cause, this);
                case CLOSED -> throw new CircuitBreakerOpenedException("Circuit breaker opened", cause, this);
                case OPEN -> throw new CircuitBreakerOpenException("Circuit breaker open", cause, this);
            }
        }
        switch (cause) {
            case dev.failsafe.CircuitBreakerOpenException f ->
                    throw new CircuitBreakerOpenException("Circuit breaker open", f, this);
            case FailsafeException f -> throw (E1) f.getCause();
            case RuntimeException r -> throw r;
            default -> throw (E1) cause;
        }
    }

    @Override
    public String toString() {
        return String.format("%s(failureThreshold=%d, successThreshold=%d, delay=%s)", name, failureThreshold,
                successThreshold, delay);
    }
}
