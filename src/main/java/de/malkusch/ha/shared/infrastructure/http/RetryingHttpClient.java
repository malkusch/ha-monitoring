package de.malkusch.ha.shared.infrastructure.http;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;

public final class RetryingHttpClient extends HttpClientProxy {

    private final FailsafeExecutor<HttpResponse> retry;
    private static final Logger LOGGER = getLogger(RetryingHttpClient.class);

    public RetryingHttpClient(HttpClient client, Duration delay, int retries) {
        super(client);

        var policy = RetryPolicy.<HttpResponse> builder();
        policy.handle(IOException.class);
        if (!delay.isZero()) {
            policy.withDelay(delay);
        }
        policy.withMaxRetries(retries);
        policy.onRetry(it -> LOGGER.warn("Retrying"));
        retry = Failsafe.with(policy.build());
    }

    @Override
    HttpResponse proxied(Operation op) throws IOException, InterruptedException {
        try {
            return retry.get(op::send);

        } catch (FailsafeException e) {
            var cause = e.getCause();

            if (cause instanceof IOException) {
                throw (IOException) cause;

            } else if (cause instanceof InterruptedException) {
                throw (InterruptedException) cause;

            } else {
                throw e;
            }
        }
    }
}