package de.malkusch.ha.shared.infrastructure.http;

import static java.net.http.HttpClient.newBuilder;
import static java.net.http.HttpClient.Redirect.ALWAYS;
import static java.util.Arrays.stream;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import org.slf4j.Logger;

public final class JdkHttpClient implements HttpClient {

	private static final Logger LOGGER = getLogger(JdkHttpClient.class);

	public JdkHttpClient(Duration timeout, String userAgent) {
		this.timeout = timeout;
		this.userAgent = userAgent;
		client = newBuilder().connectTimeout(timeout).cookieHandler(new CookieManager()).followRedirects(ALWAYS)
				.build();
	}

	@Override
	public HttpResponse get(String url) throws IOException, InterruptedException {
		var request = request(url).GET().build();
		return send(request);
	}

	@Override
	public HttpResponse post(String url, Field... fields) throws IOException, InterruptedException {
		var body = stream(fields).map(Field::urlencoded).reduce("", (f1, f2) -> f1 + "&" + f2);
		var request = request(url).POST(BodyPublishers.ofString(body))
				.setHeader("Content-Type", "application/x-www-form-urlencoded").build();
		return send(request);
	}

	private final java.net.http.HttpClient client;

	private HttpResponse send(HttpRequest request) throws IOException, InterruptedException {
		LOGGER.debug("{} {}", request.method(), request.uri());
		var response = client.send(request, BodyHandlers.ofInputStream());

		var previousBody = response.previousResponse().map(it -> it.body());
		if (previousBody.isPresent()) {
			previousBody.get().close();
		}

		if (response.statusCode() >= 500) {
			try (var body = response.body()) {
				throw new IOException(response.uri() + " failed with status code " + response.statusCode());
			}
		}
		if (response.statusCode() == 429) {
			try (var body = response.body()) {
				throw new IOException(response.uri() + " was requested too many times");
			}
		}

		var redirected = response.previousResponse().isPresent();
		return new HttpResponse(response.statusCode(), response.uri().toString(), redirected, response.body());
	}

	private final Duration timeout;
	private final String userAgent;

	private HttpRequest.Builder request(String url) {
		return HttpRequest.newBuilder(URI.create(url)).setHeader("User-Agent", userAgent).timeout(timeout);
	}
}
