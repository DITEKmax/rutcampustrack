package ru.rutcampustrack.academic.grpc;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * M16 G7 — registers {@link HeadmanRateLimitProperties} bean.
 */
@Configuration
@EnableConfigurationProperties(HeadmanRateLimitProperties.class)
public class HeadmanRateLimitConfig {
}
