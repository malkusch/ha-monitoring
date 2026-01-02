package de.malkusch.ha.monitoring.infrastructure;

import de.malkusch.ha.shared.infrastructure.http.HttpClient;
import io.prometheus.client.Gauge;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collection;

@RequiredArgsConstructor
final class PrometheusProxyPoller implements Poller {

    private final String url;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Collection<Mapping> mappings;

    @RequiredArgsConstructor
    public static final class Mapping {
        private final String jsonPath;
        private final Gauge gauge;
    }

    public static Mapping mapping(String jsonPath, String prometheusName) {
        var gauge = Gauge.build().name(prometheusName).help(prometheusName).create();
        gauge.register();
        return new Mapping(jsonPath, gauge);
    }

    @Override
    public void update() throws IOException, InterruptedException {
        try (var response = http.get(url)) {
            var json = mapper.readTree(response.body);
            for (var mapping : mappings) {
                var value = json.at(mapping.jsonPath).asDouble(0);
                mapping.gauge.set(value);
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Failed polling " + url, e);
        }
    }

    @Override
    public String toString() {
        return url;
    }
}
