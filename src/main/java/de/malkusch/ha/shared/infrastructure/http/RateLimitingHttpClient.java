package de.malkusch.ha.shared.infrastructure.http;

import java.io.IOException;

import com.google.common.util.concurrent.RateLimiter;

public final class RateLimitingHttpClient extends HttpClientProxy {

    private final RateLimiter limiter;

    public RateLimitingHttpClient(HttpClient client, RateLimiter limiter) {
        super(client);

        this.limiter = limiter;
    }

    @Override
    HttpResponse proxied(Operation op) throws IOException, InterruptedException {
        limiter.acquire();
        return op.send();
    }
}
