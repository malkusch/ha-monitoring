package de.malkusch.ha.shared.infrastructure.http;

import java.io.IOException;
import java.io.InputStream;

public final class HttpResponse implements AutoCloseable {

    public final int statusCode;
    public final InputStream body;
    public final String uri;
    public final boolean redirected;

    public HttpResponse(int statusCode, String uri, boolean redirected, InputStream body) {
        this.statusCode = statusCode;
        this.uri = uri;
        this.redirected = redirected;
        this.body = body;
    }

    @Override
    public void close() throws IOException {
        body.close();
    }

    public String bodyAsString() throws IOException {
        return new String(body.readAllBytes());
    }

    @Override
    public String toString() {
        return String.format("[%d] %s", statusCode, uri);
    }
}
