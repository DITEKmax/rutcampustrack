package ru.rutcampustrack.academic.identityoperation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;

@Configuration
public class AcademicIdentityOperationConfiguration {
    @Bean
    AcademicIdentityCoordinator academicIdentityCoordinator(
            JdbcAcademicIdentityOperationStore store,
            AuthIdentityOperationClient auth,
            IdentityCatalogSynchronizer catalog) {
        return new AcademicIdentityCoordinator(store, auth, catalog);
    }
}
