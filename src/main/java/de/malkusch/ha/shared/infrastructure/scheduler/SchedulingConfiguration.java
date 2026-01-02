package de.malkusch.ha.shared.infrastructure.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newScheduledThreadPool;

@Configuration
@EnableScheduling
class SchedulingConfiguration implements SchedulingConfigurer {

    @Value("${scheduler.pool-size}")
    private int poolSize;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        var counter = new AtomicInteger();
        taskRegistrar.setScheduler(
                newScheduledThreadPool(poolSize, it -> new Thread(it, "@Scheduled-" + counter.incrementAndGet())));
    }
}
