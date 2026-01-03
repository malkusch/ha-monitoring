package de.malkusch.ha.shared.infrastructure.circuitbreaker;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerHalfOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import dev.failsafe.function.CheckedRunnable;
import dev.failsafe.function.CheckedSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
public class CircuitBreakerTest {

    private record TestScenario(CircuitBreaker<Integer> breaker, Call... calls) {
    }

    private record Call(CheckedSupplier<Integer> call, CheckedRunnable error, int expectation, Duration delay) {
        Call(CheckedSupplier<Integer> call, int expectation) {
            this(call, null, expectation, Duration.ZERO);
        }

        Call(CheckedRunnable error) {
            this(null, error, 0, Duration.ZERO);
        }
    }

    private static final Duration ANY_DELAY = Duration.ofSeconds(1);
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final Duration TWO_SECONDS = Duration.ofSeconds(2);

    private static class TestException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final Call SUCCESS = success(1);
    private static final Call FAIL = new Call(() -> {
        throw new TestException();
    }, 0);
    private static final Call CHECKED_FAIL = new Call(() -> {
        throw new AnyCheckedException();
    }, 0);
    private static final Call ERROR_SUCCESS = new Call(() -> {
    });
    private static final Call ERROR_FAIL = new Call(() -> {
        throw new TestException();
    });
    private static final Call CHECKED_ERROR_FAIL = new Call(() -> {
        throw new AnyCheckedException();
    });


    public static Throwable[] DELEGATE_ERROR_EXCEPTION_TEST_CASES() {
        return new Throwable[]{new TestException(), new RuntimeException(), new IOException(), new AnyCheckedException()};
    }

    @ParameterizedTest
    @MethodSource("DELEGATE_ERROR_EXCEPTION_TEST_CASES")
    void errorShouldDelegateException(Throwable exception) throws Exception {
        var breaker = withFailureThreshold(3);

        assertThrows(exception.getClass(), () -> breaker.error(() -> {
            throw exception;
        }));
    }

    public static TestScenario[] TEST_CASES_SHOULD_NOT_OPEN() {
        return new TestScenario[]{ //
                new TestScenario(withFailureThreshold(1), SUCCESS), //
                new TestScenario(withFailureThreshold(1), SUCCESS, SUCCESS), //

                new TestScenario(withFailureThreshold(2), SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL), //
                new TestScenario(withFailureThreshold(2), ERROR_SUCCESS), //
                new TestScenario(withFailureThreshold(2), ERROR_FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, ERROR_SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(2), ERROR_SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), ERROR_FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS, ERROR_SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), ERROR_SUCCESS, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), ERROR_SUCCESS, SUCCESS, ERROR_SUCCESS), //

                new TestScenario(withFailureThreshold(3), SUCCESS, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(3), SUCCESS, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(3), SUCCESS, SUCCESS, ERROR_SUCCESS), //
                new TestScenario(withFailureThreshold(3), SUCCESS, SUCCESS, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(3), SUCCESS, FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(3), SUCCESS, ERROR_SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(3), SUCCESS, FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3), SUCCESS, ERROR_SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(3), FAIL, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(3), FAIL, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(3), FAIL, FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(3), ERROR_SUCCESS, ERROR_SUCCESS, SUCCESS), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_SHOULD_NOT_OPEN")
    void shouldNotOpen(TestScenario scenario) throws Exception {
        callAll(scenario);

        assertEquals(1, call(scenario.breaker, SUCCESS));
        assertTrue(scenario.breaker.isClosed());
    }

    public static TestScenario[] TEST_CASES_SHOULD_CALL_UPSTREAM() {
        return new TestScenario[]{ //
                new TestScenario(withFailureThreshold(2), success(1)), //
                new TestScenario(withFailureThreshold(2), success(1), success(2)), //
                new TestScenario(withFailureThreshold(2), success(1), FAIL), //
                new TestScenario(withFailureThreshold(2), FAIL, success(1)), //
                new TestScenario(withFailureThreshold(2), success(1), success(2), success(3)), //
                new TestScenario(withFailureThreshold(2), success(1), FAIL, success(3)), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_SHOULD_CALL_UPSTREAM")
    void shouldCallUpstreamWhenClosed(TestScenario scenario) {
        var breaker = scenario.breaker;
        for (var call : scenario.calls) {
            var result = 0;

            try {
                result = breaker.get(call.call);

            } catch (TestException optional) {
            }

            assertEquals(call.expectation, result);
        }
    }

    public static TestScenario[] TEST_CASES_THROW_UPSTREAM() {
        return new TestScenario[]{ //
                new TestScenario(withFailureThreshold(2), FAIL), //
                new TestScenario(withFailureThreshold(2), ERROR_FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS, FAIL), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_THROW_UPSTREAM")
    void shouldThrowUpstreamExceptionWhenClosed(TestScenario scenario) {
        var breaker = scenario.breaker;

        for (var call : scenario.calls) {
            assertTrue(breaker.isClosed());
            if (call == FAIL || call == ERROR_FAIL) {
                assertThrows(TestException.class, () -> call(breaker, call));

            } else {
                call(breaker, call);
            }
        }
    }

    private static class AnyCheckedException extends Exception {
    }

    private static void anyMethod() throws AnyCheckedException {
        throw new AnyCheckedException();
    }

    public static TestScenario[] TEST_CASES_THROW_CHECKED_UPSTREAM() {
        return new TestScenario[]{ //
                new TestScenario(withFailureThreshold(2, AnyCheckedException.class)), //
                new TestScenario(withFailureThreshold(2, AnyCheckedException.class), SUCCESS), //
                new TestScenario(withFailureThreshold(3, AnyCheckedException.class), SUCCESS, CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(3, AnyCheckedException.class), SUCCESS, CHECKED_ERROR_FAIL), //
                new TestScenario(withFailureThreshold(3, AnyCheckedException.class), SUCCESS, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(4, AnyCheckedException.class), CHECKED_FAIL, CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(4, AnyCheckedException.class), CHECKED_ERROR_FAIL, CHECKED_ERROR_FAIL), //
                new TestScenario(withFailureThreshold(4, AnyCheckedException.class), CHECKED_ERROR_FAIL, CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(4, AnyCheckedException.class), CHECKED_FAIL, CHECKED_ERROR_FAIL), //
                new TestScenario(withFailureThreshold(4, AnyCheckedException.class), SUCCESS, CHECKED_FAIL, CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(4, AnyCheckedException.class), SUCCESS, CHECKED_ERROR_FAIL, CHECKED_ERROR_FAIL), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_THROW_CHECKED_UPSTREAM")
    void shouldThrowCheckedUpstreamException(TestScenario scenario) {
        callAllIgnoreAnyException(scenario);

        assertThrows(AnyCheckedException.class, () -> scenario.breaker.run(() -> anyMethod()));
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_THROW_CHECKED_UPSTREAM")
    void errorShouldThrowCheckedUpstreamException(TestScenario scenario) {
        callAllIgnoreAnyException(scenario);

        assertThrows(AnyCheckedException.class, () -> scenario.breaker.error(() -> anyMethod()));
    }

    public static TestScenario[] TEST_CASES_OPEN_ON_CHECKED() {
        return new TestScenario[]{ //
                new TestScenario(withFailureThreshold(1, AnyCheckedException.class), SUCCESS), //
                new TestScenario(withFailureThreshold(1, AnyCheckedException.class), SUCCESS, SUCCESS), //

                new TestScenario(withFailureThreshold(2, AnyCheckedException.class), CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(2, AnyCheckedException.class), CHECKED_ERROR_FAIL), //
                new TestScenario(withFailureThreshold(2, AnyCheckedException.class), SUCCESS, CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(2, AnyCheckedException.class), SUCCESS, CHECKED_ERROR_FAIL), //
                new TestScenario(withFailureThreshold(2, AnyCheckedException.class), SUCCESS, SUCCESS, CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(2, AnyCheckedException.class), SUCCESS, SUCCESS, CHECKED_ERROR_FAIL), //

                new TestScenario(withFailureThreshold(3, AnyCheckedException.class), CHECKED_FAIL, CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(3, AnyCheckedException.class), CHECKED_ERROR_FAIL, CHECKED_ERROR_FAIL), //
                new TestScenario(withFailureThreshold(3, AnyCheckedException.class), SUCCESS, CHECKED_FAIL, CHECKED_FAIL), //
                new TestScenario(withFailureThreshold(3, AnyCheckedException.class), SUCCESS, CHECKED_ERROR_FAIL, CHECKED_ERROR_FAIL), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_OPEN_ON_CHECKED")
    void shouldOpenOnCheckedExceptions(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertTrue(breaker.isClosed());
        assertThrows(CircuitBreakerOpenedException.class, () -> breaker.get(CHECKED_FAIL.call));
        assertTrue(breaker.isOpen());
        assertThrowsExactly(CircuitBreakerOpenException.class, () -> breaker.get(CHECKED_FAIL.call));
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_OPEN_ON_CHECKED")
    void errorShouldOpenOnCheckedExceptions(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertTrue(breaker.isClosed());
        assertThrows(CircuitBreakerOpenedException.class, () -> call(breaker, CHECKED_ERROR_FAIL));
        assertTrue(breaker.isOpen());
        assertThrowsExactly(CircuitBreakerOpenException.class, () -> call(breaker, CHECKED_ERROR_FAIL));
    }

    public static TestScenario[] TEST_CASES_OPEN_AFTER_FAILURES() {
        return new TestScenario[]{ //
                new TestScenario(withFailureThreshold(1), SUCCESS), //
                new TestScenario(withFailureThreshold(1), SUCCESS, SUCCESS), //

                new TestScenario(withFailureThreshold(2), FAIL), //
                new TestScenario(withFailureThreshold(2), ERROR_FAIL), //
                new TestScenario(withFailureThreshold(2), ERROR_SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, ERROR_SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS, FAIL), //

                new TestScenario(withFailureThreshold(3), FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3), ERROR_FAIL, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(3), ERROR_FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3), FAIL, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(3), SUCCESS, FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3), SUCCESS, ERROR_FAIL, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(3), SUCCESS, ERROR_FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3), SUCCESS, FAIL, ERROR_FAIL), //

                new TestScenario(withFailureThreshold(3, ONE_SECOND), FAIL, FAIL, FAIL, waitSeconds(2), SUCCESS, SUCCESS, SUCCESS, FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3, ONE_SECOND), ERROR_FAIL, ERROR_FAIL, ERROR_FAIL, waitSeconds(2), SUCCESS, SUCCESS, SUCCESS, ERROR_FAIL, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(3, ONE_SECOND), ERROR_FAIL, ERROR_FAIL, FAIL, waitSeconds(2), SUCCESS, SUCCESS, SUCCESS, ERROR_FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3, ONE_SECOND), FAIL, FAIL, FAIL, FAIL, waitSeconds(2), SUCCESS, SUCCESS, SUCCESS, FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3, ONE_SECOND), ERROR_FAIL, ERROR_FAIL, ERROR_FAIL, ERROR_FAIL, waitSeconds(2), SUCCESS, SUCCESS, SUCCESS, ERROR_FAIL, ERROR_FAIL), //
                new TestScenario(withFailureThreshold(3, ONE_SECOND), ERROR_FAIL, ERROR_FAIL, ERROR_FAIL, FAIL, waitSeconds(2), SUCCESS, SUCCESS, SUCCESS, ERROR_FAIL, FAIL), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_OPEN_AFTER_FAILURES")
    void shouldOpenAfterFailures(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertThrows(CircuitBreakerOpenedException.class, () -> breaker.get(FAIL.call));
        assertTrue(breaker.isOpen());
        assertThrowsExactly(CircuitBreakerOpenException.class, () -> breaker.get(FAIL.call));
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_OPEN_AFTER_FAILURES")
    void errorShouldOpenAfterFailures(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertThrows(CircuitBreakerOpenedException.class, () -> call(breaker, ERROR_SUCCESS));
        assertTrue(breaker.isOpen());
        assertThrowsExactly(CircuitBreakerOpenException.class, () -> call(breaker, ERROR_SUCCESS));
    }

    public static TestScenario[] TEST_CASES_CLOSE_AFTER_SUCCESS() {
        return new TestScenario[]{ //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS, SUCCESS, FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2), SUCCESS, SUCCESS, FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2), SUCCESS, SUCCESS, FAIL, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS, SUCCESS, ERROR_SUCCESS, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS, SUCCESS, ERROR_SUCCESS, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2), SUCCESS, SUCCESS, ERROR_SUCCESS, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 3, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS, SUCCESS), //
                new TestScenario(withThreshold(1, 3, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2), SUCCESS, SUCCESS), //
                new TestScenario(withThreshold(1, 3, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS, SUCCESS), //
                new TestScenario(withThreshold(1, 3, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2), SUCCESS, SUCCESS), //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), FAIL, FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 1, ONE_SECOND), ERROR_SUCCESS, ERROR_SUCCESS, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), FAIL, FAIL, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), ERROR_SUCCESS, ERROR_SUCCESS, waitSeconds(2), SUCCESS), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_CLOSE_AFTER_SUCCESS")
    void shouldCloseAfterSuccess(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        var result = breaker.get(success(123).call);

        assertTrue(breaker.isClosed());
        assertEquals(123, result);
        assertEquals(1, call(breaker, SUCCESS));
    }

    public static TestScenario[] TEST_CASES_DONT_CLOSE_AFTER_DELAY() {
        return new TestScenario[]{ //
                new TestScenario(withFailureThreshold(1, TWO_SECONDS), FAIL, waitSeconds(1), SUCCESS, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(1, TWO_SECONDS), ERROR_SUCCESS, waitSeconds(1), SUCCESS, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(1, TWO_SECONDS), ERROR_FAIL, waitSeconds(1), SUCCESS, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(1, TWO_SECONDS), FAIL, SUCCESS, SUCCESS, SUCCESS, waitSeconds(1)), //
                new TestScenario(withFailureThreshold(1, TWO_SECONDS), ERROR_SUCCESS, SUCCESS, SUCCESS, SUCCESS, waitSeconds(1)), //
                new TestScenario(withFailureThreshold(1, TWO_SECONDS), ERROR_FAIL, SUCCESS, SUCCESS, SUCCESS, waitSeconds(1)), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_DONT_CLOSE_AFTER_DELAY")
    void shouldNotCloseAfterDelay(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertThrows(CircuitBreakerOpenException.class, () -> breaker.get(SUCCESS.call));
        assertTrue(breaker.isOpen());
    }

    @Test
    void shouldNotCallUpstreamWhenOpen() {
        var scenario = new TestScenario(withFailureThreshold(1), FAIL);
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);
        var calls = new AtomicInteger();

        assertThrows(CircuitBreakerOpenException.class, () -> breaker.get(calls::incrementAndGet));
        assertThrows(CircuitBreakerOpenException.class, () -> breaker.error(calls::incrementAndGet));
        assertEquals(0, calls.get());
    }

    public static TestScenario[] TEST_CASES_FAIL_HALF_OPEN() {
        return new TestScenario[]{ //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), ERROR_SUCCESS, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), ERROR_FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), FAIL, FAIL, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), FAIL, ERROR_SUCCESS, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), ERROR_SUCCESS, FAIL, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(1, 2, ONE_SECOND), ERROR_SUCCESS, ERROR_SUCCESS, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), FAIL, FAIL, FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), ERROR_SUCCESS, FAIL, ERROR_SUCCESS, waitSeconds(2)), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), ERROR_SUCCESS, ERROR_SUCCESS, ERROR_SUCCESS, waitSeconds(2)), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), FAIL, FAIL, FAIL, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), ERROR_SUCCESS, FAIL, ERROR_SUCCESS, waitSeconds(2), SUCCESS), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), ERROR_SUCCESS, ERROR_SUCCESS, ERROR_SUCCESS, waitSeconds(2), SUCCESS), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_FAIL_HALF_OPEN")
    void shouldOpenOnFailureWhenHalfOpen(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertThrows(CircuitBreakerHalfOpenException.class, () -> call(breaker, FAIL));
        assertTrue(breaker.isOpen());
        assertThrowsExactly(CircuitBreakerOpenException.class, () -> call(breaker, FAIL));
        assertThrowsExactly(CircuitBreakerOpenException.class, () -> call(breaker, FAIL));
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_FAIL_HALF_OPEN")
    void errorShouldOpenOnFailureWhenHalfOpen(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertThrows(CircuitBreakerHalfOpenException.class, () -> call(breaker, ERROR_SUCCESS));
        assertTrue(breaker.isOpen());
        assertThrowsExactly(CircuitBreakerOpenException.class, () -> call(breaker, ERROR_SUCCESS));
        assertThrowsExactly(CircuitBreakerOpenException.class, () -> call(breaker, ERROR_SUCCESS));
    }


    public static TestScenario[] TEST_CASES_SUCCESS_HALF_OPEN() {
        return new TestScenario[]{ //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), FAIL, FAIL, FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), ERROR_FAIL, ERROR_FAIL, FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), ERROR_FAIL, ERROR_FAIL, ERROR_FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), FAIL, FAIL, ERROR_FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), ERROR_FAIL, ERROR_FAIL, ERROR_FAIL, waitSeconds(2)), //
                new TestScenario(withThreshold(2, 2, ONE_SECOND), ERROR_FAIL, ERROR_FAIL, FAIL, waitSeconds(2)), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_SUCCESS_HALF_OPEN")
    void shouldCloseOnSuccessWhenHalfOpen(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertEquals(SUCCESS.expectation, call(breaker, SUCCESS));
        assertTrue(breaker.isHalfOpen());
        assertEquals(SUCCESS.expectation, call(breaker, SUCCESS));
        assertTrue(breaker.isClosed());
        assertThrows(TestException.class, () -> call(breaker, FAIL));
        assertEquals(SUCCESS.expectation, call(breaker, SUCCESS));
        assertTrue(breaker.isClosed());
    }

    private static void callAll(TestScenario scenario) {
        var breaker = scenario.breaker;
        for (var call : scenario.calls) {
            try {
                call(breaker, call);

            } catch (TestException optional) {
            }
        }
    }

    private static void callAllIgnoreAnyException(TestScenario scenario) {
        var breaker = scenario.breaker;
        for (var call : scenario.calls) {
            try {
                call(breaker, call);

            } catch (Exception optional) {
            }
        }
    }

    private static int call(CircuitBreaker<Integer> breaker, Call call) {
        try {
            if (call.call != null) {
                return breaker.get(call.call);
            }
            if (call.error != null) {
                breaker.error(call.error);
            }
            return 0;

        } finally {
            try {
                Thread.sleep(call.delay.toMillis());
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static Call success(int i) {
        return new Call(() -> i, i);
    }

    private static final CheckedSupplier<Integer> WAITING = () -> 0;

    private static Call waitSeconds(int seconds) {
        return new Call(null, null, 0, Duration.ofSeconds(seconds));
    }

    private static CircuitBreaker<Integer> withFailureThreshold(int failureThreshold, Class<? extends Throwable> exception) {
        return new CircuitBreaker<>(ANY_NAME, withFailureThreshold(failureThreshold, ANY_DELAY), exception);
    }

    private static CircuitBreaker<Integer> withFailureThreshold(int failureThreshold) {
        return withFailureThreshold(failureThreshold, ANY_DELAY);
    }

    private static final String ANY_NAME = "Any";

    private static CircuitBreaker<Integer> withFailureThreshold(int failureThreshold, Duration delay) {
        return new CircuitBreaker<>(ANY_NAME, failureThreshold, 2, delay, TestException.class);
    }

    private static CircuitBreaker<Integer> withThreshold(int failureThreshold, int successThreshold, Duration delay) {
        return new CircuitBreaker<>(ANY_NAME, failureThreshold, successThreshold, delay, TestException.class);
    }
}
