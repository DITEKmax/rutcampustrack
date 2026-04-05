package ru.rutcampustrack.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async configuration for Web Push delivery thread pool.
 *
 * Per T-27-08: ThreadPoolTaskExecutor with bounded pool and queue size prevents
 * RabbitMQ consumer thread starvation if push service is slow.
 * Per D-07: Push delivery is async and must not block the RabbitMQ listener thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "pushTaskExecutor")
    public TaskExecutor pushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("push-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
