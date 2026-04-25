package ru.rutcampustrack.auth;

import java.security.Security;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.config.TmaProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, OtpProperties.class, TmaProperties.class, InternalIssuerProperties.class})
public class AuthApplication {
    public static void main(String[] args) {
        // M13 G25.16 — override SecureRandom.getInstanceStrong() default chain.
        // На alpine musl JDK 21 default `securerandom.strongAlgorithms` =
        // `NativePRNGBlocking:SUN`, который читает /dev/random — блокируется
        // на холодном GitHub Actions runner. Tomcat session ID generator,
        // CSRF token generator и другие beans могут lazy-вызывать
        // getInstanceStrong() после JwtService.init(), вызывая hang.
        // Перекрываем через Security.setProperty() ДО SpringApplication.run()
        // — applies global JVM-wide. urandom non-blocking, equivalent
        // криптостойкость для post-Java 8 JVMs.
        Security.setProperty("securerandom.strongAlgorithms", "NativePRNGNonBlocking:SUN,DRBG:SUN");
        SpringApplication.run(AuthApplication.class, args);
    }
}
