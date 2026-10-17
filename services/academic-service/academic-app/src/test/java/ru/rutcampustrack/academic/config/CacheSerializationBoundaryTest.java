package ru.rutcampustrack.academic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.SerializationException;
import ru.rutcampustrack.academic.contract.dto.subject.AssignmentSummaryResponse;
import ru.rutcampustrack.academic.contract.enums.SemesterType;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.shared.web.config.JacksonConfig;

/** Exercises the production cache serializer without starting Redis, a database or the app. */
class CacheSerializationBoundaryTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-10-14T09:30:15+03:00");

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(
                            context ->
                                    context.getBeanFactory()
                                            .setConversionService(
                                                    ApplicationConversionService
                                                            .getSharedInstance()))
                    .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                    .withUserConfiguration(CacheConfig.class, WebConfig.class, JacksonConfig.class)
                    // Only the factory is a fixture: serialization uses the actual cache config.
                    // RedisCacheManager initialization and these serialization pairs do no I/O.
                    .withBean(
                            RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    @Test
    void cachedDomainValuesAndCollectionsRetainTheirTypesAndDates() {
        contextRunner.run(
                context -> {
                    var groups = serialization(context, "groups");
                    Group sourceGroup = group();
                    assertGroup(roundtrip(groups, sourceGroup));

                    var semesters = serialization(context, "active_semester");
                    Semester sourceSemester = semester();
                    Semester restored = (Semester) roundtrip(semesters, sourceSemester);
                    assertThat(restored.getName()).isEqualTo("Autumn 2026");
                    assertThat(restored.getDateFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
                    assertThat(restored.getDateTo()).isEqualTo(LocalDate.of(2026, 12, 31));
                    assertThat(restored.getSemesterType()).isEqualTo(SemesterType.AUTUMN);
                    assertThat(restored.isActive()).isTrue();
                    assertThat(restored.getCreatedAt().toInstant())
                            .isEqualTo(CREATED_AT.toInstant());

                    // Collection support belongs to the serializer contract; the live roster is
                    // deliberately uncached. No DTO/roster caching behavior is introduced here.
                    var values = new ArrayList<Group>();
                    values.add(sourceGroup);
                    Object restoredCollection = roundtrip(groups, values);
                    assertThat(restoredCollection).isInstanceOf(List.class);
                    List<?> restoredGroups = (List<?>) restoredCollection;
                    assertThat(restoredGroups).hasSize(1);
                    assertGroup(restoredGroups.getFirst());
                });
    }

    @Test
    void foreignTypeMetadataIsRejectedWithoutBreakingAllowedValues() {
        contextRunner.run(
                context -> {
                    var values = serialization(context, "groups");
                    assertGroup(roundtrip(values, group()));

                    // A harmless URI is serialized as a value, never opened or resolved. Building
                    // the payload with the real writer preserves its actual type-id encoding.
                    var foreign = new LinkedHashMap<String, Object>();
                    foreign.put("target", URI.create("https://example.invalid/cache-fixture"));
                    ByteBuffer encoded = values.write(foreign);

                    assertThatThrownBy(() -> values.read(encoded))
                            .isInstanceOf(SerializationException.class)
                            .hasRootCauseInstanceOf(InvalidTypeIdException.class);

                    assertGroup(roundtrip(values, group()));
                });
    }

    @Test
    void cacheTypeMetadataDoesNotChangeOrdinaryRestDtoMapping() {
        contextRunner.run(
                context -> {
                    ObjectMapper rest = context.getBean(ObjectMapper.class);
                    var assignment =
                            new AssignmentSummaryResponse(
                                    11L,
                                    21L,
                                    31L,
                                    SubjectType.LECTURE,
                                    LocalDate.of(2026, 9, 1),
                                    null);
                    List<AssignmentSummaryResponse> response = List.of(assignment);
                    byte[] before = rest.writeValueAsBytes(response);

                    assertGroup(roundtrip(serialization(context, "groups"), group()));
                    byte[] after = rest.writeValueAsBytes(response);

                    assertThat(after).isEqualTo(before);
                    JsonNode json = rest.readTree(after);
                    assertThat(json.isArray()).isTrue();
                    assertThat(json.get(0).path("validFrom").asText()).isEqualTo("2026-09-01");
                    assertThat(json.get(0).path("lessonType").asText()).isEqualTo("LECTURE");
                    assertThat(json.findValues("@class")).isEmpty();
                    assertThat(
                                    rest.readValue(
                                            after,
                                            new TypeReference<
                                                    List<AssignmentSummaryResponse>>() {}))
                            .containsExactly(assignment);
                    // The Academic REST customizer remains independent of the cache mapper.
                    assertThat(rest.readValue("\"lecture\"", SubjectType.class))
                            .isEqualTo(SubjectType.LECTURE);
                });
    }

    private static SerializationPair<Object> serialization(
            AssertableApplicationContext context, String cacheName) {
        RedisCacheManager manager = (RedisCacheManager) context.getBean(CacheManager.class);
        return manager.getCacheConfigurations().get(cacheName).getValueSerializationPair();
    }

    private static Object roundtrip(SerializationPair<Object> values, Object source) {
        return values.read(values.write(source));
    }

    private static Group group() {
        Group group = new Group();
        group.setName("УИТ-311");
        group.setAlphabeticCode("УИТ");
        group.setNumericCode("311");
        group.setCurrentCourse(3);
        group.setTrainingDurationYears(4);
        group.setDurationStatus("KNOWN");
        group.setActive(true);
        group.setCreatedAt(CREATED_AT);
        return group;
    }

    private static Semester semester() {
        Semester semester = new Semester();
        semester.setName("Autumn 2026");
        semester.setDateFrom(LocalDate.of(2026, 9, 1));
        semester.setDateTo(LocalDate.of(2026, 12, 31));
        semester.setSemesterType(SemesterType.AUTUMN);
        semester.setActive(true);
        semester.setCreatedAt(CREATED_AT);
        return semester;
    }

    private static void assertGroup(Object restored) {
        assertThat(restored).isInstanceOf(Group.class);
        Group group = (Group) restored;
        assertThat(group.getName()).isEqualTo("УИТ-311");
        assertThat(group.getAlphabeticCode()).isEqualTo("УИТ");
        assertThat(group.getNumericCode()).isEqualTo("311");
        assertThat(group.getCurrentCourse()).isEqualTo(3);
        assertThat(group.getTrainingDurationYears()).isEqualTo(4);
        assertThat(group.getDurationStatus()).isEqualTo("KNOWN");
        assertThat(group.isActive()).isTrue();
        assertThat(group.getCreatedAt().toInstant()).isEqualTo(CREATED_AT.toInstant());
    }
}
