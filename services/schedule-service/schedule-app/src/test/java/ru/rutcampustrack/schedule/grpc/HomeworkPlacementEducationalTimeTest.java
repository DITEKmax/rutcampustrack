package ru.rutcampustrack.schedule.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.grpc.Context;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.schedule.config.ClockConfig;
import ru.rutcampustrack.schedule.homework.HomeworkPlacementService;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.web.time.EducationalTime;

class HomeworkPlacementEducationalTimeTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final Clock REAL = Clock.fixed(Instant.parse("2026-10-22T21:05:00Z"), MOSCOW);
    private static final Clock TEACHING = Clock.fixed(Instant.parse("2026-10-22T06:00:00Z"), MOSCOW);
    private static final UUID KEY = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void dateAdmissionUsesEducationalDayAcrossRealMidnightAndStillRejectsPastDay() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenAnswer(call -> call.<String>getArgument(0).contains("WHERE binding_id = ?")
                        ? List.of(Map.<String, Object>of("binding_id", 7L, "group_id", 10L,
                                "subject_id", 20L, "semester_id", 30L, "binding_mode", "DATE",
                                "placement_date", LocalDate.of(2026, 10, 22), "revision", 1L,
                                "state", "PENDING")) : List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Clock.class, () -> REAL);
            context.registerBean(EducationalTime.class, () -> new EducationalTime(TEACHING));
            context.registerBean(JdbcTemplate.class, () -> jdbc);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(ScheduleSemesterArchiveWriteFence.class,
                    () -> mock(ScheduleSemesterArchiveWriteFence.class));
            context.register(HomeworkPlacementService.class);
            context.refresh();
            var claims = new InternalJwtClaims(1L, UUID.randomUUID(), 1L, 1L,
                    "HEADMAN", "ACTIVE", 10L, true, false);
            Context actor = Context.current().withValue(HomeworkBindingActorContext.CLAIMS, claims);
            var service = context.getBean(HomeworkPlacementService.class);
            var accepted = actor.call(() -> service.reserveDate(request("2026-10-22")));
            assertThat(accepted.getBindingId()).isEqualTo(7L);
            assertThat(accepted.getDate()).isEqualTo("2026-10-22");
            assertThatThrownBy(() -> actor.call(() -> service.reserveDate(request("2026-10-21"))))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(context.getBean(Clock.class)).isSameAs(REAL);
            assertThat(LocalDate.now(context.getBean(Clock.class))).isEqualTo(LocalDate.of(2026, 10, 23));
        }
    }

    @Test
    void productionEducationalTimeKeepsTheRealClock() {
        Clock real = new ClockConfig().clock();
        assertThat(new ClockConfig().educationalTime(real).clock()).isSameAs(real);
    }

    private static ReserveHomeworkBindingRequest request(String date) {
        return ReserveHomeworkBindingRequest.newBuilder().setBindingMode("DATE")
                .setGroupId(10L).setSubjectId(20L).setSemesterId(30L).setDate(date)
                .setRequestKey(KEY.toString()).setPayloadHash(ByteString.copyFrom(new byte[32])).build();
    }
}
