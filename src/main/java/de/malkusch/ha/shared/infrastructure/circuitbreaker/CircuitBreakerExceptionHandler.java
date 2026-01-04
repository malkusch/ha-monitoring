package de.malkusch.ha.shared.infrastructure.circuitbreaker;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerHalfOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import static de.malkusch.ha.shared.infrastructure.DateUtil.formatDuration;
import static de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreakerExceptionHandler.ExceptionHandler.empty;

@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerExceptionHandler {

    public static class Builder {

        private ExceptionHandler<CircuitBreakerOpenedException> onOpened = empty();
        private ExceptionHandler<CircuitBreakerOpenException> onOpen = empty();
        private ExceptionHandler<CircuitBreakerHalfOpenException> onHalfOpen = empty();
        private ExceptionHandler<? super Throwable> catchAll = empty();
        private Logger logger = log;

        public static Builder defaultLogging() {
            return new Builder()
                    .onOpened((log, e) -> log.warn("Circuit Breaker opened. Half open in {} - {} - {}", formatDuration(e.remainingDelay()), e.circuitBreaker(), causeMessage(e)))
                    .onOpen((log, e) -> log.debug("Circuit Breaker is open. Half open in {} - {} ", formatDuration(e.remainingDelay()), e.circuitBreaker()))
                    .onHalfOpen((log, e) -> log.warn("Circuit Breaker was half open. Half open again in {} -  {} - {}", formatDuration(e.remainingDelay()), e.circuitBreaker(), causeMessage(e)))
                    .catchAll((log, e) -> log.error("Error while circuit breaker is closed", e));
        }

        private static String causeMessage(Throwable e) {
            return e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        }

        @FunctionalInterface
        public interface LoggingHandler<E extends Throwable> {
            void log(Logger logger, E exception);
        }

        public <L extends Throwable> ExceptionHandler<L> logging(LoggingHandler<L> logging) {
            return e -> logging.log(logger(), e);
        }

        public Logger logger() {
            return logger;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder onOpened(ExceptionHandler<CircuitBreakerOpenedException> onOpened) {
            this.onOpened = onOpened;
            return this;
        }

        public Builder onOpened(LoggingHandler<CircuitBreakerOpenedException> logging) {
            return onOpened(logging(logging));
        }

        public Builder onOpen(ExceptionHandler<CircuitBreakerOpenException> onOpen) {
            this.onOpen = onOpen;
            return this;
        }

        public Builder onOpen(LoggingHandler<CircuitBreakerOpenException> logging) {
            return onOpen(logging(logging));
        }

        public Builder onHalfOpen(ExceptionHandler<CircuitBreakerHalfOpenException> onHalfOpen) {
            this.onHalfOpen = onHalfOpen;
            return this;
        }

        public Builder onHalfOpen(LoggingHandler<CircuitBreakerHalfOpenException> logging) {
            return onHalfOpen(logging(logging));
        }

        public Builder catchAll(ExceptionHandler<? super Throwable> catchAll) {
            this.catchAll = catchAll;
            return this;
        }

        public Builder catchAll(LoggingHandler<? super Throwable> catchAll) {
            return catchAll(logging(catchAll));
        }

        public <E1 extends Throwable, E2 extends Throwable> CircuitBreakerLogging<E1, E2> buildLogging() {
            return new CircuitBreakerLogging<>(onOpened, onOpen, onHalfOpen);
        }
    }

    public static <E1 extends Throwable, E2 extends Throwable> void withCircuitBreakerLogging(Execution<E1, E2> execution) throws E1, E2 {
        var builder = Builder.defaultLogging();
        builder.<E1, E2>buildLogging().handle(execution);
    }

    @FunctionalInterface
    public interface Execution<E1 extends Throwable, E2 extends Throwable> {
        void execute() throws E1, E2;
    }

    @FunctionalInterface
    public interface ExceptionHandler<E extends Throwable> {

        void handle(E e);

        static <E extends Throwable> ExceptionHandler<E> empty() {
            return e -> {
            };
        }
    }

    @RequiredArgsConstructor
    public static class CircuitBreakerLogging<E1 extends Throwable, E2 extends Throwable> {

        private final ExceptionHandler<CircuitBreakerOpenedException> onOpened;
        private final ExceptionHandler<CircuitBreakerOpenException> onOpen;
        private final ExceptionHandler<CircuitBreakerHalfOpenException> onHalfOpen;

        public void handle(Execution<E1, E2> execution) throws E1, E2 {
            try {
                execution.execute();

            } catch (CircuitBreakerOpenException e) {
                switch (e) {
                    case CircuitBreakerOpenedException opened -> onOpened.handle(opened);
                    case CircuitBreakerHalfOpenException halfOpen -> onHalfOpen.handle(halfOpen);
                    case CircuitBreakerOpenException open -> onOpen.handle(open);
                }
            }
        }
    }
}