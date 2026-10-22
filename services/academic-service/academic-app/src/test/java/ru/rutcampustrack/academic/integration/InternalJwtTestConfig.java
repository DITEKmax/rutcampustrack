package ru.rutcampustrack.academic.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

/** Fixture key material is also loaded by the actual Auth JwtService. Admission stays live. */
@TestConfiguration
public class InternalJwtTestConfig {
    @Bean(destroyMethod = "")
    public AcademicIdentityIntegrationFixture identityIntegrationFixture(
            @Value("${rct.fixture.kind}") String kind) {
        return switch (kind) {
            case "ordinary" -> AbstractAcademicIntegrationTest.IDENTITY_FIXTURE;
            case "cache" -> AbstractAcademicCacheIntegrationTest.IDENTITY_FIXTURE;
            case "event" -> AbstractAcademicEventIntegrationTest.IDENTITY_FIXTURE;
            case "health-degradation" -> HealthDegradationIT.IDENTITY_FIXTURE;
            default ->
                    throw new IllegalArgumentException("Known owned integration fixture required");
        };
    }

    @Bean
    public InternalJwtTestFactory internalJwtTestFactory(
            AcademicIdentityIntegrationFixture fixture) {
        return fixture.signer();
    }

    @Bean
    @Primary
    public PublicKeyProvider testPublicKeyProvider(
            InternalJwtProperties properties, AcademicIdentityIntegrationFixture fixture) {
        return new PublicKeyProvider(properties) {
            @Override
            public void init() {}

            @Override
            public void refresh() {}

            @Override
            public java.security.PublicKey getPublicKey() {
                return fixture.signer().publicKey();
            }
        };
    }
}
