package de.malkusch.ha.monitoring.infrastructure;

import static de.malkusch.ha.monitoring.infrastructure.PrometheusProxyPoller.mapping;
import static java.time.Duration.ZERO;
import static java.util.Arrays.asList;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


import de.malkusch.ha.monitoring.infrastructure.PrometheusProxyPoller.Mapping;
import de.malkusch.ha.monitoring.infrastructure.SonnenPoller.DownTime;
import de.malkusch.ha.monitoring.infrastructure.mqtt.MqttMonitoring;
import de.malkusch.ha.shared.infrastructure.async.AsyncService;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.ha.shared.infrastructure.http.HttpClient;
import de.malkusch.ha.shared.infrastructure.http.JdkHttpClient;
import de.malkusch.ha.shared.infrastructure.http.RetryingHttpClient;
import io.prometheus.client.servlet.jakarta.exporter.MetricsServlet;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    private final AsyncService async;

    @Bean
    public ServletRegistrationBean<MetricsServlet> prometheusServlet() {
        return new ServletRegistrationBean<>(new MetricsServlet(), "/prometheus/*");
    }

    @Component
    @ConfigurationProperties("sonnen")
    @Data
    static class SonnenProperties {
        private String url;
        private DownTime downTime;

        @Data
        static class DownTime {
            private LocalTime start;
            private LocalTime end;
        }
    }

    private final SonnenProperties sonnenProperties;

    @Bean
    public ScheduledPoller sonnenPrometheusProxy() {
        var url = sonnenProperties.url;
        var downTime = new DownTime(sonnenProperties.downTime.start, sonnenProperties.downTime.end);

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
        var poller = proxyPoller(url, monitoringHttp(), mappings);
        poller = new SonnenPoller(poller, downTime);
        return new ScheduledPoller(poller, async);
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
        return new ScheduledPoller(proxyPoller(url, monitoringHttp(), mappings), async);
    }

    @Bean
    ScheduledPoller inverter() {
        var mappings = asList( //
                mapping("/Body/Data/Site/P_PV", "inverter_production") //
        );
        return new ScheduledPoller(new OfflinePoller(proxyPoller(properties.inverter, offlineHttp(), mappings)), async);
    }

    private Poller proxyPoller(String url, HttpClient http, Collection<Mapping> mappings) {
        Poller poller = new PrometheusProxyPoller(url, http, mapper, mappings);
        poller = new CircuitBreakerPoller(properties.circuitBreaker, poller);
        return poller;
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
