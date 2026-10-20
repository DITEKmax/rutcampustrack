package ru.rutcampustrack.academic.exportjobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

class ReportDownloadTicketBoundaryIT extends AbstractAcademicIntegrationTest {
    @Autowired TestRestTemplate http;

    @Test
    void rejectedOpaqueTicketIsNotEchoedByOwnerProblemResponse() {
        var marker = "SENTINEL" + "A".repeat(35);
        var response = http.getForEntity("/academic/report-download/" + marker, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        response.getHeaders()
                                .getContentType()
                                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(response.getBody())
                .doesNotContain(marker)
                .contains("/academic/report-download/{ticket}");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        response.getHeaders()
                .values()
                .forEach(
                        values ->
                                values.forEach(value -> assertThat(value).doesNotContain(marker)));
    }
}
