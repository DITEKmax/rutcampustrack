package ru.rutcampustrack.academic.admin;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;
@Configuration
public class AdminAccessConfiguration {
    @Bean public AdminAccessJdbcAuthority adminAccessJdbcAuthority(JdbcTemplate jdbc) { return new AdminAccessJdbcAuthority(jdbc); }
}
