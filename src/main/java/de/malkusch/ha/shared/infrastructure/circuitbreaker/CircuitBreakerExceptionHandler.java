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

    public static class Builder<E extends Throwable> {

        private ExceptionHandler<CircuitBreakerOpenedException> onOpened = empty();
        private ExceptionHandler<CircuitBreakerOpenException> onOpen = empty();
        private ExceptionHandler<CircuitBreakerHalfOpenException> onHalfOpen = empty();
        private ExceptionHandler<? super Throwable> catchAll = empty();
        private Logger logger = log;

        public static <E extends Throwable> Builder<E> defaultLogging() {
            return new Builder<E>()
                    .onOpened((log, e) -> log.warn("Circuit Breaker {} opened after {} failures. It will be open for {}: {}", e.circuitBreaker(), e.failures(), formatDuration(e.remainingDelay()), causeMessage(e)))
                    .onOpen((log, e) -> log.debug("Circuit Breaker {} has {} failures and is open for another  {}: {}", e.circuitBreaker(), e.failures(), formatDuration(e.remainingDelay()), causeMessage(e)))
                    .onHalfOpen((log, e) -> log.warn("Circuit Breaker {} has {} failures was half open and is now closed for {}: {}", e.circuitBreaker(), e.failures(), formatDuration(e.remainingDelay()), causeMessage(e)))
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

        public Builder<E> logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder<E> onOpened(ExceptionHandler<CircuitBreakerOpenedException> onOpened) {
            this.onOpened = onOpened;
            return this;
        }

        public Builder<E> onOpened(LoggingHandler<CircuitBreakerOpenedException> logging) {
            return onOpened(logging(logging));
        }

        public Builder<E> onOpen(ExceptionHandler<CircuitBreakerOpenException> onOpen) {
            this.onOpen = onOpen;
            return this;
        }

        public Builder<E> onOpen(LoggingHandler<CircuitBreakerOpenException> logging) {
            return onOpen(logging(logging));
        }

        public Builder<E> onHalfOpen(ExceptionHandler<CircuitBreakerHalfOpenException> onHalfOpen) {
            this.onHalfOpen = onHalfOpen;
            return this;
        }

        public Builder<E> onHalfOpen(LoggingHandler<CircuitBreakerHalfOpenException> logging) {
            return onHalfOpen(logging(logging));
        }

        public Builder<E> catchAll(ExceptionHandler<? super Throwable> catchAll) {
            this.catchAll = catchAll;
            return this;
        }

        public Builder<E> catchAll(LoggingHandler<? super Throwable> catchAll) {
            return catchAll(logging(catchAll));
        }

        public CircuitBreakerLogging<E> buildLogging() {
            return new CircuitBreakerLogging<>(onOpened, onOpen, onHalfOpen);
        }

        public SilentHandler<E> logAllExceptions(LoggingHandler<? super Throwable> catchAll) {
            var logger = buildLogging();
            return new SilentHandler<>(logger, logging(catchAll));
        }
    }

    public static <E extends Throwable> void withCircuitBreakerLogging(Execution<E> execution) throws E {
        var builder = Builder.<E>defaultLogging();
        builder.buildLogging().handle(execution);
    }

    @FunctionalInterface
    public interface Execution<E extends Throwable> {
        void execute() throws E;
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
    public static class SilentHandler<E extends Throwable> {

        private final CircuitBreakerLogging<E> logger;
        private final ExceptionHandler<? super Throwable> catchAll;

        public void handle(Execution<E> execution) {
            try {
                logger.handle(execution);

            } catch (Throwable e) {
                catchAll.handle(e);
            }
        }
    }

    @RequiredArgsConstructor
    public static class CircuitBreakerLogging<E extends Throwable> {

        private final ExceptionHandler<CircuitBreakerOpenedException> onOpened;
        private final ExceptionHandler<CircuitBreakerOpenException> onOpen;
        private final ExceptionHandler<CircuitBreakerHalfOpenException> onHalfOpen;

        public void handle(Execution<E> execution) throws E {
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