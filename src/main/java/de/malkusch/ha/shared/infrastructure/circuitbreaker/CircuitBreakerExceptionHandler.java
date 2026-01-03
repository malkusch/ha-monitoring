package de.malkusch.ha.shared.infrastructure.circuitbreaker;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerHalfOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import static de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreakerExceptionHandler.ExceptionHandler.empty;

@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerExceptionHandler {

    public static class Builder<E extends Throwable, T extends Throwable> {

        private ExceptionHandler<CircuitBreakerOpenedException> onOpened = empty();
        private ExceptionHandler<CircuitBreakerOpenException> onOpen = empty();
        private ExceptionHandler<CircuitBreakerHalfOpenException> onHalfOpen = empty();
        private CheckedExceptionHandler<? super Throwable, T> catchAll = CheckedExceptionHandler.empty();
        private Logger logger = log;

        public static <E extends Throwable> Builder<E, ? extends RuntimeException> defaultLogging() {
            return new Builder<E, RuntimeException>()
                    .onOpened((log, e) -> log.warn("Circuit Breaker {} opened: {}", e.circuitBreaker(), causeMessage(e)))
                    .onOpen((log, e) -> log.debug("Circuit Breaker {} open: {}", e.circuitBreaker(), causeMessage(e)))
                    .onHalfOpen((log, e) -> log.warn("Circuit Breaker {} half open: {}", e.circuitBreaker(), causeMessage(e)))
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

        public Builder<E, T> logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder<E, T> onOpened(ExceptionHandler<CircuitBreakerOpenedException> onOpened) {
            this.onOpened = onOpened;
            return this;
        }

        public Builder<E, T> onOpened(LoggingHandler<CircuitBreakerOpenedException> logging) {
            return onOpened(logging(logging));
        }

        public Builder<E, T> onOpen(ExceptionHandler<CircuitBreakerOpenException> onOpen) {
            this.onOpen = onOpen;
            return this;
        }

        public Builder<E, T> onOpen(LoggingHandler<CircuitBreakerOpenException> logging) {
            return onOpen(logging(logging));
        }

        public Builder<E, T> onHalfOpen(ExceptionHandler<CircuitBreakerHalfOpenException> onHalfOpen) {
            this.onHalfOpen = onHalfOpen;
            return this;
        }

        public Builder<E, T> onHalfOpen(LoggingHandler<CircuitBreakerHalfOpenException> logging) {
            return onHalfOpen(logging(logging));
        }

        public Builder<E, T> catchAll(CheckedExceptionHandler<? super Throwable, T> catchAll) {
            this.catchAll = catchAll;
            return this;
        }

        public Builder<E, T> catchAll(LoggingHandler<? super Throwable> catchAll) {
            return catchAll(logging(catchAll).checkedExceptionHandler());
        }

        public CircuitBreakerLogging<E> buildLogging() {
            return new CircuitBreakerLogging<>(onOpened, onOpen, onHalfOpen);
        }

        public Handler<E, ? extends RuntimeException> logAllExceptions(LoggingHandler<? super Throwable> catchAll) {
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
    public interface ExceptionHandler<E extends Throwable> extends CheckedExceptionHandler<E, RuntimeException> {

        default <T extends Throwable> CheckedExceptionHandler<E, T> checkedExceptionHandler() {
            return this::handle;
        }

        static <E extends Throwable> ExceptionHandler<E> empty() {
            return e -> {
            };
        }
    }

    @FunctionalInterface
    public interface CheckedExceptionHandler<E extends Throwable, T extends Throwable> {
        void handle(E e) throws T;

        static <E extends Throwable, T extends Throwable> CheckedExceptionHandler<E, T> empty() {
            return e -> {
            };
        }
    }

    @FunctionalInterface
    public interface Handler<T extends Throwable, T2 extends Throwable> {

        void handle(Execution<T> execution) throws T2;

    }

    @RequiredArgsConstructor
    static class SilentHandler<E extends Throwable> implements Handler<E, RuntimeException> {

        private final CircuitBreakerLogging<E> logger;
        private final CheckedExceptionHandler<? super Throwable, ? extends RuntimeException> catchAll;

        public void handle(Execution<E> execution) {
            try {
                logger.handle(execution);

            } catch (Throwable e) {
                catchAll.handle(e);
            }
        }
    }

    @RequiredArgsConstructor
    public static class CircuitBreakerLogging<E extends Throwable> implements Handler<E, E> {

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