package ru.rutcampustrack.academic.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

/**
 * M03a: supplies a test {@link PublicKeyProvider} bound to
 * {@link InternalJwtTestFactory}'s keypair so IT can issue valid Internal JWT
 * tokens without reaching an actual auth-service.
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
                // no-op — we don't call auth-service in tests
            }

            @Override
            public void refresh() {
                // no-op
            }

            @Override
            public java.security.PublicKey getPublicKey() {
                return factory.publicKey();
            }
        };
    }
}
