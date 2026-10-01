package ru.rutcampustrack.attendance.report;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanGroupCompositionFormatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanGroupCompositionFormatsResponse.FormatOption;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportExportTooLargeException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class HeadmanGroupCompositionService {
    private static final int MAX_MEMBERS = 5000;
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final RequestContext context;
    private final AcademicGrpcClient academic;
    private final DocumentRendererGrpcClient converter;
    private final HeadmanGroupCompositionRenderer renderer;
    private final Clock clock;

    public HeadmanGroupCompositionService(RequestContext context, AcademicGrpcClient academic,
            DocumentRendererGrpcClient converter, HeadmanGroupCompositionRenderer renderer, Clock clock) {
        this.context = context;
        this.academic = academic;
        this.converter = converter;
        this.renderer = renderer;
        this.clock = clock;
    }

    public HeadmanGroupCompositionFormatsResponse formats() {
        currentModel(); // The catalogue does not admit stale headman sessions or VIEW_STATS helpers.
        return new HeadmanGroupCompositionFormatsResponse(Arrays.stream(HeadmanStatsFormat.values())
                .map(format -> new FormatOption(format.code(), format.label(), format.contentType(), format.extension()))
                .toList());
    }

    public HeadmanGroupCompositionExportResult export(String rawFormat) {
        var model = currentModel();
        var format = HeadmanStatsFormat.from(rawFormat);
        byte[] content;
        switch (format) {
            case DOCX -> content = renderer.renderDocx(model);
            case HTML -> content = renderer.renderHtml(model);
            case XLSX -> content = renderer.renderXlsx(model);
            case PDF, PNG -> {
                try {
                    content = converter.convertDocxForHeadmanWeeklyExport(renderer.renderDocx(model),
                            format == HeadmanStatsFormat.PDF ? TargetFormat.PDF : TargetFormat.PNG_PAGES_ZIP);
                } catch (StatusRuntimeException error) {
                    if (error.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                        throw new ReportExportTooLargeException("Состав группы превышает лимит преобразования DOCX");
                    }
                    throw error;
                }
            }
            default -> throw new IllegalStateException("Unsupported group composition format");
        }
        if (content == null || content.length == 0) {
            throw new ReportExportUnavailableException("Сервис формирования не вернул файл состава группы");
        }
        if (content.length > MAX_BYTES) throw new ReportExportTooLargeException("Файл состава превышает 20 МиБ");
        String group = Normalizer.normalize(model.groupName(), Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_")
                .replaceAll("_+", "_").replaceAll("^[._]+|[._]+$", "").toLowerCase(Locale.ROOT);
        if (group.isBlank()) group = "группа";
        return new HeadmanGroupCompositionExportResult(
                "состав_" + group + "_" + model.generatedOn() + "." + format.extension(),
                format.contentType(), content);
    }

    private HeadmanGroupCompositionModel currentModel() {
        Long groupId = context.getGroupId();
        Long actorId = context.getUserId();
        if (context.getRole() != UserRole.STUDENT || !context.isHeadman()
                || actorId == null || actorId <= 0 || groupId == null || groupId <= 0) {
            throw new AccessDeniedException("Выгрузка состава доступна только старосте своей группы");
        }
        var snapshot = academic.getHeadmanGroupComposition(groupId);
        if (snapshot == null || snapshot.getGroupId() != groupId || snapshot.getGroupName().isBlank()) {
            throw inconsistent();
        }
        if (snapshot.getMembersCount() > MAX_MEMBERS) {
            throw new ReportExportTooLargeException("Состав группы превышает 5000 студентов");
        }
        var rows = new ArrayList<HeadmanGroupCompositionModel.Row>();
        Set<Long> ids = new HashSet<>();
        boolean actorPresent = false;
        int headmen = 0;
        for (var member : snapshot.getMembersList()) {
            String role = switch (member.getGroupRole()) {
                case "HEADMAN" -> "Староста";
                case "ASSISTANT" -> "Помощник";
                case "STUDENT" -> "Студент";
                default -> throw inconsistent();
            };
            if (member.getUserId() <= 0 || !ids.add(member.getUserId())
                    || member.getDisplayName().isBlank() || member.getLogin().isBlank()) throw inconsistent();
            if ("HEADMAN".equals(member.getGroupRole())) headmen++;
            if (member.getUserId() == actorId && "HEADMAN".equals(member.getGroupRole())) actorPresent = true;
            rows.add(new HeadmanGroupCompositionModel.Row(rows.size() + 1,
                    member.getDisplayName(), member.getLogin(), role));
        }
        if (!actorPresent || headmen != 1) throw inconsistent();
        // Preserve the authoritative ru_icu surname/name/patronymic order from Academic.
        return new HeadmanGroupCompositionModel(snapshot.getGroupName(), LocalDate.now(clock), rows);
    }

    private static AcademicServiceUnavailableException inconsistent() {
        return new AcademicServiceUnavailableException("Academic вернул некорректный снимок состава группы");
    }
}
