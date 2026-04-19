package ru.rutcampustrack.academic.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

/**
 * M03a: wires shared-security beans into academic-service context.
 *
 * Shared-security is a plain java-library (no autoconfig) — each consumer
 * registers its own {@link PublicKeyProvider} and {@link InternalJwtValidator}
 * instances to avoid cross-service coupling via component scan.
 */
@Configuration
@EnableConfigurationProperties(InternalJwtProperties.class)
public class InternalJwtConfig {

    @Bean
    public PublicKeyProvider publicKeyProvider(InternalJwtProperties properties) {
        return new PublicKeyProvider(properties);
    }

    @Bean
    public InternalJwtValidator internalJwtValidator(PublicKeyProvider publicKeyProvider,
                                                     InternalJwtProperties properties) {
        return new InternalJwtValidator(publicKeyProvider, properties);
    }
}
