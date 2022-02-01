package de.malkusch.ha.shared.infrastructure.scheduler;

import static java.util.concurrent.Executors.newScheduledThreadPool;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
class SchedulingConfiguration implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        var counter = new AtomicInteger();
        taskRegistrar.setScheduler(
                newScheduledThreadPool(10, it -> new Thread(it, "@Scheduled-" + counter.incrementAndGet())));
    }
}
