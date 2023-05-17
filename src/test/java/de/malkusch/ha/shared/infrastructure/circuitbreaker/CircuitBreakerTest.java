package de.malkusch.ha.shared.infrastructure.circuitbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import dev.failsafe.function.CheckedSupplier;

public class CircuitBreakerTest {

    private static record TestScenario(CircuitBreaker<Integer> breaker, Call... calls) {
    }

    private static record Call(CheckedSupplier<Integer> call, int expectation, Duration delay) {
        Call(CheckedSupplier<Integer> call, int expectation) {
            this(call, expectation, Duration.ZERO);
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

    public static TestScenario[] TEST_CASES_SHOULD_NOT_OPEN() {
        return new TestScenario[] { //
                new TestScenario(withFailureThreshold(1), SUCCESS), //
                new TestScenario(withFailureThreshold(1), SUCCESS, SUCCESS), //

                new TestScenario(withFailureThreshold(2), SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), SUCCESS, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL, SUCCESS, FAIL), //

                new TestScenario(withFailureThreshold(3), SUCCESS, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(3), SUCCESS, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(3), SUCCESS, FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(3), SUCCESS, FAIL, FAIL), //
                new TestScenario(withFailureThreshold(3), FAIL, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(3), FAIL, SUCCESS, FAIL), //
                new TestScenario(withFailureThreshold(3), FAIL, FAIL, SUCCESS), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_SHOULD_NOT_OPEN")
    void shouldNotOpen(TestScenario scenario) {
        callAll(scenario);
    }

    public static TestScenario[] TEST_CASES_SHOULD_CALL_UPSTREAM() {
        return new TestScenario[] { //
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
        return new TestScenario[] { //
                new TestScenario(withFailureThreshold(2), FAIL), //
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

            if (call == FAIL) {
                assertThrows(TestException.class, () -> breaker.get(call.call));

            } else {
                breaker.get(call.call);
            }
        }
    }

    public static TestScenario[] TEST_CASES_OPEN_AFTER_FAILURES() {
        return new TestScenario[] { //
                new TestScenario(withFailureThreshold(1), FAIL), //
                new TestScenario(withFailureThreshold(1), FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(1), SUCCESS, FAIL), //

                new TestScenario(withFailureThreshold(2), FAIL, FAIL), //
                new TestScenario(withFailureThreshold(2), FAIL, FAIL, FAIL), //
                new TestScenario(withFailureThreshold(2), SUCCESS, FAIL, FAIL), //
                new TestScenario(withFailureThreshold(2), FAIL, FAIL, SUCCESS), //
                new TestScenario(withFailureThreshold(2), FAIL, FAIL, FAIL), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_OPEN_AFTER_FAILURES")
    void shouldOpenAfterFailures(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertThrows(CircuitBreakerOpenException.class, () -> breaker.get(SUCCESS.call));
    }

    public static TestScenario[] TEST_CASES_CLOSE_AFTER_SUCCESS() {
        return new TestScenario[] { //
                new TestScenario(withFailureThreshold(1, ONE_SECOND), FAIL, waitSeconds(2)), //
                new TestScenario(withFailureThreshold(1, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS), //
                new TestScenario(withFailureThreshold(1, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(1, ONE_SECOND), FAIL, waitSeconds(2), SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(2, ONE_SECOND), FAIL, FAIL, waitSeconds(2), SUCCESS, SUCCESS,
                        SUCCESS, FAIL), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_CLOSE_AFTER_SUCCESS")
    void shouldCloseAfterSuccess(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        var result = breaker.get(success(123).call);

        assertEquals(123, result);
    }

    public static TestScenario[] TEST_CASES_DONT_CLOSE_AFTER_DELAY() {
        return new TestScenario[] { //
                new TestScenario(withFailureThreshold(1, TWO_SECONDS), FAIL, waitSeconds(1), SUCCESS, SUCCESS, SUCCESS), //
                new TestScenario(withFailureThreshold(1, TWO_SECONDS), FAIL, SUCCESS, SUCCESS, SUCCESS, waitSeconds(1)), //
        };
    }

    @ParameterizedTest
    @MethodSource("TEST_CASES_DONT_CLOSE_AFTER_DELAY")
    void shouldNotCloseAfterDelay(TestScenario scenario) {
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);

        assertThrows(CircuitBreakerOpenException.class, () -> breaker.get(SUCCESS.call));
    }

    @Test
    void shouldNotCallUpstreamWhenOpen() {
        var scenario = new TestScenario(withFailureThreshold(1), FAIL);
        var breaker = scenario.breaker;
        callAllIgnoreAnyException(scenario);
        var calls = new AtomicInteger();

        assertThrows(CircuitBreakerOpenException.class, () -> breaker.get(calls::incrementAndGet));
        assertEquals(0, calls.get());
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
            return breaker.get(call.call);

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

    private static Call waitSeconds(int seconds) {
        return new Call(() -> 1, 1, Duration.ofSeconds(seconds));
    }

    private static CircuitBreaker<Integer> withFailureThreshold(int failureThreshold) {
        return withFailureThreshold(failureThreshold, ANY_DELAY);
    }

    private static CircuitBreaker<Integer> withFailureThreshold(int failureThreshold, Duration delay) {
        return new CircuitBreaker<>(failureThreshold, 2, delay, TestException.class);
    }

    private static CircuitBreaker<Integer> withThreshold(int failureThreshold, int successThreshold) {
        return new CircuitBreaker<>(failureThreshold, 2, ANY_DELAY, TestException.class);
    }
}
