package de.malkusch.ha.monitoring.infrastructure.niu;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@ConditionalOnProperty("scooter.enabled")
@Retention(RetentionPolicy.RUNTIME)
public @interface ScooterEnabled {
}
