package ru.rutcampustrack.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.rutcampustrack.auth.security.InternalIssuerSecretFilter;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalIssuerSecretFilter internalIssuerSecretFilter;
    private final SessionStatePort sessionStatePort;
    private final CredentialSessionTransactionPort credentialSessionTransactionPort;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          InternalIssuerSecretFilter internalIssuerSecretFilter,
                          SessionStatePort sessionStatePort,
                          CredentialSessionTransactionPort credentialSessionTransactionPort) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.internalIssuerSecretFilter = internalIssuerSecretFilter;
        this.sessionStatePort = sessionStatePort;
        this.credentialSessionTransactionPort = credentialSessionTransactionPort;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/tma",
                                "/auth/public-key",
                                "/auth/otp/**",
                                "/internal/**",
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/actuator/prometheus"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalIssuerSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionLifecycleService sessionLifecycleService() {
        return new SessionLifecycleService(sessionStatePort, credentialSessionTransactionPort);
    }
}
