package ru.rutcampustrack.mobilebff;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(
                title = "RutCampusTrack Student Mobile BFF",
                version = "JS-STUDENT-01-r1",
                description = "Canonical PWA/TMA student contract. Domain handlers land after contract freeze."
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Validated access JWT; client X-User-* headers are never identity"
)
public class MobileBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(MobileBffApplication.class, args);
    }
}
