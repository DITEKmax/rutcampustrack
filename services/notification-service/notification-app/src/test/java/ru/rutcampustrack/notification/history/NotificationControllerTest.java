package ru.rutcampustrack.notification.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.notification.contract.dto.history.NotificationHistoryDto;
import ru.rutcampustrack.notification.contract.dto.history.UnreadCountDto;
import ru.rutcampustrack.notification.exception.AccessDeniedException;
import ru.rutcampustrack.notification.security.RequestContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationHistoryService service;

    @Mock
    private RequestContext requestContext;

    @Mock
    @SuppressWarnings("rawtypes")
    private PagedResourcesAssembler assembler;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(service, requestContext);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listDelegatesToServiceWithUserFromContext() {
        when(requestContext.getUserId()).thenReturn(42L);
        Page<NotificationHistoryDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(service.list(eq(42L), anyBoolean(), any(Pageable.class))).thenReturn(page);
        PagedModel<EntityModel<NotificationHistoryDto>> stubModel = PagedModel.empty();
        when(assembler.toModel(any(Page.class))).thenReturn((PagedModel) stubModel);

        ResponseEntity<PagedModel<EntityModel<NotificationHistoryDto>>> response =
                controller.list(false, PageRequest.of(0, 20), assembler);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(stubModel);
        verify(service).list(42L, false, PageRequest.of(0, 20));
    }

    @Test
    void unreadCountReturnsServiceValue() {
        when(requestContext.getUserId()).thenReturn(1L);
        when(service.getUnreadCount(1L)).thenReturn(new UnreadCountDto(5));

        ResponseEntity<UnreadCountDto> response = controller.unreadCount();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().count()).isEqualTo(5L);
    }

    @Test
    void markAsReadReturns204OnSuccess() {
        when(requestContext.getUserId()).thenReturn(1L);
        when(service.markAsRead("doc-id", 1L)).thenReturn(true);

        ResponseEntity<Void> response = controller.markAsRead("doc-id");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void markAsReadThrowsAccessDeniedWhenForeign() {
        when(requestContext.getUserId()).thenReturn(1L);
        when(service.markAsRead("doc-id", 1L)).thenReturn(false);

        assertThatThrownBy(() -> controller.markAsRead("doc-id"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void markAllReadReturns204() {
        when(requestContext.getUserId()).thenReturn(1L);
        when(service.markAllRead(1L)).thenReturn(3L);

        ResponseEntity<Void> response = controller.markAllRead();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
