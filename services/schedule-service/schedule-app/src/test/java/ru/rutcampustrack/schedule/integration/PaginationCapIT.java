package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Actual HTTP pagination against more matching rows than the configured service cap. */
class PaginationCapIT extends AbstractScheduleIntegrationTest {
    @Autowired TestRestTemplate http;
    @MockitoBean AcademicGrpcClient academic;

    @Test
    void getLessons_sizeExceedsMax_truncatedToServiceCap() {
        resetScheduleData();
        try {
            var item = new ScheduleItem();
            item.setGroupId(1L);
            item.setSubjectId(100L);
            item.setSemesterId(10L);
            item.setDayOfWeek((short) 1);
            item.setLessonNumber((short) 1);
            item.setWeekType(WeekType.ALL);
            item.setActive(true);
            item = saveFixtureScheduleItem(item);
            List<Long> expected = new ArrayList<>();
            for (int i = 0; i < 250; i++) {
                var lesson = new Lesson();
                lesson.setScheduleItemId(item.getId());
                lesson.setDate(LocalDate.of(2026, 1, 1).plusDays(i));
                lesson.setStatus(LessonStatus.PLANNED);
                expected.add(saveFixtureLesson(lesson).getId());
            }
            var first = request(1000000, 0);
            var second = request(1000000, 1);
            assertThat(first.path("page").path("size").asInt()).isEqualTo(200);
            assertThat(first.path("page").path("totalElements").asInt()).isEqualTo(250);
            assertThat(second.path("page").path("totalElements").asInt()).isEqualTo(250);
            var firstIds = ids(first);
            var secondIds = ids(second);
            assertThat(firstIds).hasSize(200).doesNotHaveDuplicates();
            assertThat(secondIds).hasSize(50).doesNotHaveDuplicates();
            assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
            firstIds.addAll(secondIds);
            assertThat(firstIds).containsExactlyInAnyOrderElementsOf(expected);
            var small = request(7, 0);
            assertThat(small.path("page").path("size").asInt()).isEqualTo(7);
            assertThat(ids(small)).hasSize(7).allMatch(expected::contains);
        } finally {
            resetScheduleData();
        }
    }

    private JsonNode request(int size, int page) {
        var headers = new HttpHeaders();
        headers.set("X-User-Id", "42");
        headers.set("X-User-Role", "STUDENT");
        headers.set("X-Group-Id", "1");
        var response =
                http.exchange(
                        "/schedule/groups/1/lessons?dateFrom=2026-01-01&dateTo=2026-12-31"
                                + "&sort=date,asc&size="
                                + size
                                + "&page="
                                + page,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Long> ids(JsonNode body) {
        List<Long> ids = new ArrayList<>();
        for (var row : body.path("_embedded").path("lessonResponseList")) {
            assertThat(row.path("groupId").asLong()).isEqualTo(1L);
            assertThat(row.path("status").asText()).isEqualTo("PLANNED");
            ids.add(row.path("id").asLong());
        }
        return ids;
    }
}
