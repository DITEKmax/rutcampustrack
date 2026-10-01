package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.grpc.GroupCompositionMember;
import ru.rutcampustrack.academic.grpc.HeadmanGroupCompositionResponse;
import ru.rutcampustrack.attendance.contract.api.ReportApi;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeadmanGroupCompositionServiceTest {
    private final RequestContext context = new RequestContext();
    private final AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
    private final DocumentRendererGrpcClient converter = mock(DocumentRendererGrpcClient.class);
    private final HeadmanGroupCompositionService service = new HeadmanGroupCompositionService(
            context, academic, converter, new HeadmanGroupCompositionRenderer(),
            Clock.fixed(Instant.parse("2026-09-30T22:00:00Z"), ZoneId.of("Europe/Moscow")));

    @Test
    void snapshotBecomesSameMinimalEscapedOrderedRosterInHtmlDocxAndXlsx() throws Exception {
        headman();
        when(academic.getHeadmanGroupComposition(10)).thenReturn(snapshot(10));
        var html = service.export("html");
        assertThat(html.fileName()).isEqualTo("состав_уит-311_2026-10-01.html");
        assertThat(html.contentType()).isEqualTo(ReportApi.HTML_MEDIA_TYPE);
        String text = new String(html.content(), StandardCharsets.UTF_8);
        assertThat(text).contains("УИТ-311", "01.10.2026", "ФИО", "Логин", "Роль в группе",
                "<td>1</td><td>А &lt;Б&gt;</td><td>=login</td><td>Помощник</td>",
                "<td>2</td><td>Я Староста</td><td>headman</td><td>Староста</td>");
        assertThat(text).doesNotContain("telegram", "password", "recovery");

        var docx = xmlEntries(service.export("docx").content());
        assertThat(docx.get("word/document.xml")).contains("А &lt;Б&gt;", "=login", "01.10.2026", "<w:tblHeader/>");
        var xlsx = xmlEntries(service.export("xlsx").content());
        assertThat(xlsx.get("xl/worksheets/sheet1.xml")).contains("А &lt;Б&gt;", "=login", "t=\"inlineStr\"")
                .doesNotContain("<f>");
        assertThat(service.formats().formats()).extracting(option -> option.code())
                .containsExactly("docx", "pdf", "png", "html", "xlsx");
        assertThat(service.formats().formats().get(2).extension()).isEqualTo("zip");
    }

    @Test
    void helpersAreDeniedBeforePersonalReadAndFreshAuthorityFailureStaysForbidden() {
        headman();
        context.setHeadman(false);
        assertThatThrownBy(() -> service.export("html")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(service::formats).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(academic, converter);
        context.setHeadman(true);
        when(academic.getHeadmanGroupComposition(10)).thenThrow(new AccessDeniedException("revoked"));
        assertThatThrownBy(() -> service.export("html")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(converter);
    }

    @Test
    void mismatchedGroupOrMissingActorSnapshotNeverProducesPersonalFile() {
        headman();
        when(academic.getHeadmanGroupComposition(10)).thenReturn(snapshot(20));
        assertThatThrownBy(() -> service.export("html")).isInstanceOf(AcademicServiceUnavailableException.class);
        when(academic.getHeadmanGroupComposition(10)).thenReturn(
                HeadmanGroupCompositionResponse.newBuilder().setGroupId(10).setGroupName("УИТ-311").build());
        assertThatThrownBy(() -> service.export("html")).isInstanceOf(AcademicServiceUnavailableException.class);
        verifyNoInteractions(converter);
    }

    private void headman() {
        context.setUserId(2L);
        context.setRole(UserRole.STUDENT);
        context.setGroupId(10L);
        context.setHeadman(true);
    }

    private static HeadmanGroupCompositionResponse snapshot(long groupId) {
        return HeadmanGroupCompositionResponse.newBuilder().setGroupId(groupId).setGroupName("УИТ-311")
                .addMembers(GroupCompositionMember.newBuilder().setUserId(1).setDisplayName("А <Б>")
                        .setLogin("=login").setGroupRole("ASSISTANT"))
                .addMembers(GroupCompositionMember.newBuilder().setUserId(2).setDisplayName("Я Староста")
                        .setLogin("headman").setGroupRole("HEADMAN")).build();
    }

    private static Map<String, String> xmlEntries(byte[] bytes) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                byte[] xml = zip.readAllBytes();
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
                entries.put(entry.getName(), new String(xml, StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
