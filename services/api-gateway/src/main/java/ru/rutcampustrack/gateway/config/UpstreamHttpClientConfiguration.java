package ru.rutcampustrack.gateway.config;

import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bounds positive DNS caching when an owned upstream keeps its name across a restart. */
@Configuration(proxyBeanMethods = false)
public class UpstreamHttpClientConfiguration {

    @Bean
    public HttpClientCustomizer upstreamDnsCachePolicy() {
        return client -> client.resolver(spec -> spec.cacheMaxTimeToLive(Duration.ofSeconds(5)));
    }
}
