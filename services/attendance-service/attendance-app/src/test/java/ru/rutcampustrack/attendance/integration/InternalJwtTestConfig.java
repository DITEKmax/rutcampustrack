package ru.rutcampustrack.attendance.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

/**
 * M03a: test PublicKeyProvider with in-memory keypair.
 */
@TestConfiguration
public class InternalJwtTestConfig {

    private final InternalJwtTestFactory factory = new InternalJwtTestFactory();

    @Bean
    public InternalJwtTestFactory internalJwtTestFactory() {
        return factory;
    }

    @Bean
    @Primary
    public PublicKeyProvider testPublicKeyProvider(InternalJwtProperties properties) {
        return new PublicKeyProvider(properties) {
            @Override
            public void init() {
            }

            @Override
            public void refresh() {
            }

            @Override
            public java.security.PublicKey getPublicKey() {
                return factory.publicKey();
            }
        };
    }
}
