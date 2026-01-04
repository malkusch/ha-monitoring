package de.malkusch.ha.shared.infrastructure.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadFactory;

import static java.lang.Thread.currentThread;

@Service
@Slf4j
public final class AsyncService {

    private final ThreadFactory factory = Thread //
            .ofVirtual() //
            .uncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in thread {}", t, e)) //
            .factory();

    @FunctionalInterface
    public interface Task {
        void run() throws Exception;
    }

    public Runnable async(Runnable runnable) {
        return () -> executeAsync(runnable::run);
    }

    public void executeAsync(Task task) {
        factory.newThread((() -> {
            try {
                task.run();

            } catch (InterruptedException e) {
                currentThread().interrupt();

            } catch (Throwable e) {
                log.error("Error in async task", e);
            }
        })).start();
    }
}
