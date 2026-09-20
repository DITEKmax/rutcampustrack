package ru.rutcampustrack.mobilebff.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

@Configuration
@EnableConfigurationProperties(InternalJwtProperties.class)
public class MobileInternalJwtConfig {
    @Bean
    PublicKeyProvider publicKeyProvider(InternalJwtProperties properties) {
        return new PublicKeyProvider(properties);
    }

    @Bean
    InternalJwtValidator internalJwtValidator(PublicKeyProvider provider, InternalJwtProperties properties) {
        return new InternalJwtValidator(provider, properties);
    }
}
