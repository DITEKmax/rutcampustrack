package ru.rutcampustrack.shared.observability.errors;

import io.sentry.SentryOptions;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "io.sentry.spring.boot.jakarta.SentryAutoConfiguration")
@ConditionalOnClass(SentryOptions.class)
public class ErrorPrivacyAutoConfiguration {
    @Bean
    public SentryOptions.BeforeSendCallback safeErrorExport() {
        return new SafeErrorEvent();
    }
}
