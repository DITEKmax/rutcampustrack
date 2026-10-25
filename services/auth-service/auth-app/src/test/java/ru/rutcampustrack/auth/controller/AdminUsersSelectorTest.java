package ru.rutcampustrack.auth.controller;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.dto.*;

class AdminUsersSelectorTest {
    @Test
    void groupIsBoundWithoutChangingAbsentSelectorsAndRejectsNonPositiveIds() throws Exception {
        var mapper = new ObjectMapper();
        var absent = request(null);
        var group = request(11L);
        // Pre-groupId canonical encoding, from the accepted six-field selector.
        assertThat(absent.bindingHash())
                .isEqualTo("dcbc2f44e3e8e3bced1db982c1a085e3e32132a6d2ad775d3b9038c1530b0cdb");
        assertThat(group.bindingHash())
                .isNotEqualTo(absent.bindingHash())
                .isNotEqualTo(request(12L).bindingHash());
        assertThat(mapper.valueToTree(absent.adminUsers()).has("groupId")).isFalse();
        assertThat(
                        mapper.readValue(
                                mapper.writeValueAsBytes(group),
                                IssueReportDownloadTicketRequest.class))
                .isEqualTo(group);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(absent)).isEmpty();
            assertThat(validator.validate(group)).isEmpty();
            for (long id : new long[] {0, -1})
                assertThat(validator.validate(request(id)))
                        .anyMatch(v -> v.getPropertyPath().toString().equals("adminUsers.groupId"));
        }
    }

    private IssueReportDownloadTicketRequest request(Long groupId) {
        return new IssueReportDownloadTicketRequest(
                ReportDownloadKind.ADMIN_USERS,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new IssueReportDownloadTicketRequest.AdminUsersParameters(
                        null, "STUDENT", null, "ACTIVE", groupId, null, ReportDownloadFormat.HTML),
                null);
    }
}
