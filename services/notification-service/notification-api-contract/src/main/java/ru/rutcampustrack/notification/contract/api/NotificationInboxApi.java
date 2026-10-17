package ru.rutcampustrack.notification.contract.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.notification.contract.dto.history.NotificationCatalogDto;
import ru.rutcampustrack.notification.contract.dto.history.NotificationInboxDto;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesV2Dto;

@RequestMapping("/notifications")
public interface NotificationInboxApi {
    @GetMapping("/catalog")
    ResponseEntity<NotificationCatalogDto> catalog();

    @GetMapping("/inbox")
    ResponseEntity<NotificationInboxDto.Page> inbox(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean unreadOnly);

    @GetMapping("/inbox/unread-count")
    ResponseEntity<NotificationInboxDto.Count> inboxUnreadCount();

    @PutMapping("/inbox/{id}/read-state")
    ResponseEntity<NotificationInboxDto.ReadResult> readState(
            @PathVariable String id, @RequestBody NotificationInboxDto.ReadState request);

    @PostMapping("/inbox/mark-all-read")
    ResponseEntity<NotificationInboxDto.AllReadResult> inboxMarkAllRead();

    @GetMapping("/preferences/v2")
    ResponseEntity<NotificationPreferencesV2Dto> preferencesV2();

    @PutMapping("/preferences/v2")
    ResponseEntity<NotificationPreferencesV2Dto> updatePreferencesV2(
            @RequestBody NotificationPreferencesV2Dto.Update request);
}
