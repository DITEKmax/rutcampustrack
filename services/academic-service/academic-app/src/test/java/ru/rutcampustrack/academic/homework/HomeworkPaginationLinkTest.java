package ru.rutcampustrack.academic.homework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import ru.rutcampustrack.academic.contract.dto.homework.HomeworkResponse;
import ru.rutcampustrack.academic.entity.Homework;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real Spring Data link generation at the selected endpoint's public URI boundary. */
class HomeworkPaginationLinkTest {
    private final HomeworkService service = mock(HomeworkService.class);
    private final HomeworkAssembler itemAssembler = mock(HomeworkAssembler.class);
    private final HomeworkController controller = new HomeworkController(service, itemAssembler);
    private final PagedResourcesAssembler<HomeworkResponse> pages =
            new PagedResourcesAssembler<>(
                    new HateoasPageableHandlerMethodArgumentResolver(),
                    UriComponentsBuilder.fromUriString(
                                    "http://academic.internal:9091/academic/homeworks")
                            .build());

    @AfterEach
    void releaseRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void allPaginationRelationsUseThePublicPathAndRetainEncodedFiltersAndSort() {
        var request = new MockHttpServletRequest("GET", "/academic/homeworks");
        request.setServerName("academic.internal");
        request.setServerPort(9091);
        request.setQueryString(
                "groupId=11&groupId=99&semesterId=22&semesterId=99&archived=true&archived=false&sort=lessonDate,asc&sort=id,desc&opaque=a%2Bb%20c&page=17&size=999");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var pageable =
                PageRequest.of(1, 2, Sort.by(Sort.Order.asc("lessonDate"), Sort.Order.desc("id")));
        prepare(pageable, 6);

        var result = controller.listHomeworks(11L, 22L, true, pageable, pages).getBody();

        assertThat(result).isNotNull();
        assertThat(result.getMetadata().getTotalElements()).isEqualTo(6);
        assertThat(result.getContent()).hasSize(2);
        for (var relation :
                Map.of("self", 1, "first", 0, "prev", 0, "next", 2, "last", 2).entrySet()) {
            String href = result.getRequiredLink(relation.getKey()).getHref();
            var uri = URI.create(href);
            assertThat(uri.isAbsolute()).isFalse();
            assertThat(uri.getRawAuthority()).isNull();
            assertThat(uri.getPath()).isEqualTo("/api/academic/homeworks");
            var query = UriComponentsBuilder.fromUri(uri).build(true).getQueryParams();
            assertThat(query.get("groupId")).containsExactly("11");
            assertThat(query.get("semesterId")).containsExactly("22");
            assertThat(query.get("archived")).containsExactly("true");
            assertThat(query.get("sort")).containsExactly("lessonDate,asc", "id,desc");
            assertThat(query.get("opaque")).containsExactly("a%2Bb%20c");
            assertThat(query.get("page")).containsExactly(relation.getValue().toString());
            assertThat(query.get("size")).containsExactly("2");
        }
    }

    @Test
    void directReadsKeepPaginationWithoutAThreadBoundServletRequest() {
        var pageable = PageRequest.of(0, 2);
        prepare(pageable, 4);

        var result = controller.listHomeworks(11L, 22L, true, pageable, pages).getBody();

        assertThat(result).isNotNull();
        assertThat(result.getRequiredLink("self").getHref())
                .isEqualTo(
                        "/api/academic/homeworks?groupId=11&semesterId=22&archived=true&page=0&size=2");
        assertThat(result.getRequiredLink("next").getHref())
                .isEqualTo(
                        "/api/academic/homeworks?groupId=11&semesterId=22&archived=true&page=1&size=2");
    }

    private void prepare(PageRequest pageable, long total) {
        var first = mock(Homework.class);
        var second = mock(Homework.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(2L);
        when(service.listHomeworks(11L, 22L, pageable, true))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, total));
        when(service.completedHomeworkIds(List.of(1L, 2L))).thenReturn(Set.of(1L));
        when(itemAssembler.toModel(any(Homework.class), anyBoolean(), anyBoolean()))
                .thenAnswer(call -> EntityModel.of(new HomeworkResponse()));
    }
}
