package ru.rutcampustrack.auth.controller;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.auth.dto.*;

import java.util.List;

class AdminAttendanceSelectorTest {
    private final ObjectMapper mapper =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void selectorBindsEveryExportDimensionAndRejectsUnknownOrMalformedStoredState()
            throws Exception {
        for (ReportDownloadFormat format : ReportDownloadFormat.values()) {
            var original = request("7", "ИВТ", "PRESENT_PERCENT", "DESC", format);
            assertThat(original.isParametersConsistent()).isTrue();
            assertThat(original.expectedMediaType()).isEqualTo(format.mediaType());
            assertThat(
                            mapper.readValue(
                                    mapper.writeValueAsBytes(original),
                                    IssueReportDownloadTicketRequest.class))
                    .isEqualTo(original);
            for (var changed :
                    List.of(
                            request("8", "ИВТ", "PRESENT_PERCENT", "DESC", format),
                            request("7", "другая", "PRESENT_PERCENT", "DESC", format),
                            request("7", "ИВТ", "GROUP_NAME", "DESC", format),
                            request("7", "ИВТ", "PRESENT_PERCENT", "ASC", format)))
                assertThat(changed.bindingHash()).isNotEqualTo(original.bindingHash());
        }
        for (var invalid :
                List.of(
                        request("0", "", "GROUP_NAME", "ASC", ReportDownloadFormat.HTML),
                        request(
                                "9223372036854775808",
                                "",
                                "GROUP_NAME",
                                "ASC",
                                ReportDownloadFormat.HTML),
                        request("7", "", "FUTURE", "ASC", ReportDownloadFormat.HTML),
                        request("7", "", "GROUP_NAME", "SIDEWAYS", ReportDownloadFormat.HTML),
                        request("7", "", "GROUP_NAME", "ASC", null)))
            assertThat(invalid.isParametersConsistent()).isFalse();
        assertThatThrownBy(
                        () ->
                                mapper.readValue(
                                        "{\"semesterId\":\"7\",\"search\":\"\",\"sort\":\"GROUP_NAME\",\"direction\":\"ASC\",\"format\":\"html\",\"page\":1}",
                                        IssueReportDownloadTicketRequest
                                                .AdminGroupAttendanceParameters.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    private static IssueReportDownloadTicketRequest request(
            String semester,
            String search,
            String sort,
            String direction,
            ReportDownloadFormat format) {
        return new IssueReportDownloadTicketRequest(
                ReportDownloadKind.ADMIN_GROUP_ATTENDANCE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new IssueReportDownloadTicketRequest.AdminGroupAttendanceParameters(
                        semester, search, sort, direction, format));
    }
}
