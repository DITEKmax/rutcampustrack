package ru.rutcampustrack.mobilebff.teacher;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.mobilebff.contract.api.TeacherApi;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.Assignment;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.DayResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.ExcuseResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.ExportFormatsResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.JournalResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.LessonResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.SemesterResponse;
import ru.rutcampustrack.mobilebff.contract.model.TeacherApiModels.StatsResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
public final class TeacherApiController implements TeacherApi {
    private final TeacherReadFacade facade;

    public TeacherApiController(TeacherReadFacade facade) {
        this.facade = facade;
    }

    @Override
    public ResponseEntity<SemesterResponse> semester() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(facade.semester());
    }

    @Override
    public ResponseEntity<List<Assignment>> assignments(String semesterId, String dateFrom, String dateTo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(facade.assignments(parseId(semesterId), parseDate(dateFrom), parseDate(dateTo)));
    }

    @Override
    public ResponseEntity<DayResponse> day(String semesterId, String date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(facade.day(parseId(semesterId), parseDate(date)));
    }

    @Override
    public ResponseEntity<LessonResponse> lesson(String lessonId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(facade.lesson(parseId(lessonId)));
    }

    @Override
    public ResponseEntity<JournalResponse> journal(String semesterId,
                                                   String groupId,
                                                   String subjectId,
                                                   String lessonType,
                                                   String page,
                                                   String pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(facade.journal(
                parseId(semesterId),
                parseId(groupId),
                parseId(subjectId),
                parseText(lessonType, "lessonType"),
                parsePage(page, "page", 0),
                parsePage(pageSize, "pageSize", 100)));
    }

    @Override
    public ResponseEntity<ExportFormatsResponse> journalExportFormats() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(facade.journalExportFormats());
    }

    @Override
    public ResponseEntity<byte[]> exportJournal(String semesterId,
                                                String groupId,
                                                String subjectId,
                                                List<String> lessonTypes,
                                                String format) {
        TeacherReadFacade.Download download = facade.exportJournal(
                parseId(semesterId), parseId(groupId), parseId(subjectId), lessonTypes,
                parseText(format, "format"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.bytes().length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(download.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    @Override
    public ResponseEntity<StatsResponse> stats(String semesterId, String scope, String groupId,
                                               String subjectId, List<String> lessonTypes,
                                               List<String> sorts, List<String> filters) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(facade.stats(
                parseId(semesterId), parseText(scope, "scope"), parseOptionalId(groupId, "groupId"),
                parseOptionalId(subjectId, "subjectId"), lessonTypes, sorts, filters));
    }

    @Override
    public ResponseEntity<ExportFormatsResponse> statsExportFormats() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(facade.statsExportFormats());
    }

    @Override
    public ResponseEntity<byte[]> exportStats(String semesterId,
                                              String scope,
                                              String groupId,
                                              String subjectId,
                                              List<String> lessonTypes,
                                              List<String> sorts,
                                              List<String> filters,
                                              String format) {
        TeacherReadFacade.Download download = facade.exportStats(
                parseId(semesterId), parseText(scope, "scope"), parseOptionalId(groupId, "groupId"),
                parseOptionalId(subjectId, "subjectId"), lessonTypes, sorts, filters,
                parseText(format, "format"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.bytes().length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(download.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    @Override
    public ResponseEntity<ExcuseResponse> excuse(String requestId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(facade.excuse(requestId));
    }

    @Override
    public ResponseEntity<byte[]> excuseAttachment(String requestId, String attachmentId) {
        TeacherReadFacade.Download download = facade.attachment(requestId, attachmentId);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(download.contentType());
        } catch (RuntimeException invalidType) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentLength(download.bytes().length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(download.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    private static long parseId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException error) {
            throw new ru.rutcampustrack.mobilebff.error.MobileBffException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode.INVALID_REQUEST,
                    "ID должен быть положительным числом");
        }
    }

    private static Long parseOptionalId(String value, String field) {
        if (value == null || value.isBlank()) return null;
        return parseId(value);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw new ru.rutcampustrack.mobilebff.error.MobileBffException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode.INVALID_REQUEST,
                    "Дата должна быть в формате ISO-8601");
        }
    }

    private static String parseText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ru.rutcampustrack.mobilebff.error.MobileBffException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode.INVALID_REQUEST,
                    field + " не должен быть пустым");
        }
        return value.trim();
    }

    private static int parsePage(String value, String field, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || ("pageSize".equals(field) && parsed > 100)
                    || ("pageSize".equals(field) && parsed == 0)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException error) {
            throw new ru.rutcampustrack.mobilebff.error.MobileBffException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode.INVALID_REQUEST,
                    field + " имеет недопустимое значение");
        }
    }
}
