package ru.rutcampustrack.attendance.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

/**
 * M03a: wires shared-security beans into attendance-service context.
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
