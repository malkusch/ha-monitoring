package de.malkusch.ha.monitoring.infrastructure;

import static de.malkusch.ha.monitoring.infrastructure.PrometheusProxyPoller.mapping;
import static java.time.Duration.ZERO;
import static java.util.Arrays.asList;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.malkusch.ha.monitoring.infrastructure.PrometheusProxyPoller.Mapping;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.ha.shared.infrastructure.http.HttpClient;
import de.malkusch.ha.shared.infrastructure.http.JdkHttpClient;
import de.malkusch.ha.shared.infrastructure.http.RetryingHttpClient;
import io.prometheus.client.servlet.jakarta.exporter.MetricsServlet;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty("monitoring.enabled")
class PrometheusMonitoringConfiguration {

    @Component
    @ConfigurationProperties("monitoring")
    @Data
    static class MonitoringProperties {
        private Duration timeout;
        private int retries;
        private String inverter;
        private CircuitBreaker.Properties circuitBreaker;
        private List<Sensor> sensors;
        private List<MqttSensor> mqttSensors;

        @Data
        static class MqttSensor {
            private String name;
            private String topic;
            private List<String> metrics;

            public String topic() {
                return topic == null ? name : topic;
            }

        }

        @Data
        static class Sensor {
            private String name;
            private String url;
        }
    }

    private final MonitoringProperties properties;
    private final ObjectMapper mapper;

    @Bean
    public ServletRegistrationBean<MetricsServlet> prometheusServlet() {
        return new ServletRegistrationBean<>(new MetricsServlet(), "/prometheus/*");
    }

    @Bean
    public ScheduledPoller sonnenPrometheusProxy(@Value("${sonnen.url}") String url) {
        var mappings = asList( //
                mapping("/Consumption_W", "batterie_consumption"), //
                mapping("/Production_W", "batterie_production"), //
                mapping("/USOC", "batterie_charge"), //
                mapping("/GridFeedIn_W", "batterie_feed_in"), //
                mapping("/Pac_total_W", "batterie_battery_consumption"), //
                mapping("/Ubat", "batterie_battery_voltage"), //
                mapping("/Uac", "batterie_ac_voltage"), //
                mapping("/RemainingCapacity_W", "batterie_capacity"), //
                mapping("/RSOC", "batterie_realCharge"), //
                mapping("/Sac1", "batterie_Sac1"), //
                mapping("/Sac2", "batterie_Sac2"), //
                mapping("/Sac3", "batterie_Sac3") //
        );
        return new ScheduledPoller(new CircuitBreakerPoller(properties.circuitBreaker,
                new PrometheusProxyPoller(url, monitoringHttp(), mapper, mappings)));
    }

    @Bean
    public List<ScheduledPoller> climatePrometheusProxies() {
        return properties.sensors.stream().map(it -> {
            var mappings = asList( //
                    mapping("/temperature", it.name + "_temperature"), //
                    mapping("/humidity", it.name + "_humidity"), //
                    mapping("/co2", it.name + "_co2") //
            );

            return proxy(it.url, mappings);
        }).collect(Collectors.toList());
    }

    @Bean
    @Scope(value = SCOPE_PROTOTYPE)
    ScheduledPoller proxy(String url, Collection<Mapping> mappings) {
        return new ScheduledPoller(new CircuitBreakerPoller(properties.circuitBreaker,
                new PrometheusProxyPoller(url, monitoringHttp(), mapper, mappings)));
    }

    @Bean
    ScheduledPoller inverter() {
        var mappings = asList( //
                mapping("/Body/Data/Site/P_PV", "inverter_production") //
        );
        return new ScheduledPoller(
                new OfflinePoller(new PrometheusProxyPoller(properties.inverter, offlineHttp(), mapper, mappings)));
    }

    @Bean
    List<MqttMonitoring<JsonNode>> mqttMonitoring(MqttMonitoring.Factory factory) {
        return properties.mqttSensors.stream().map(it -> {
            try {
                return factory.build(it.name, it.topic(), it.metrics);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).toList();
    }

    @Bean
    HttpClient offlineHttp() {
        return new JdkHttpClient(properties.timeout, "");
    }

    @Bean
    HttpClient monitoringHttp() {
        var http = new JdkHttpClient(properties.timeout, "");
        return new RetryingHttpClient(http, ZERO, properties.retries);
    }
}
