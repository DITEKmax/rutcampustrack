package ru.rutcampustrack.attendance.report.planned;

import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.util.List;

/** Exact typed schedule reads; an immutable plan implementation has no outbound transport. */
public interface ReportScheduleReadPort {
    LessonResponse getLessonById(Long lessonId);

    LessonsResponse getLessonsByGroup(Long groupId, Long semesterId, String from, String to);

    List<LessonInfo> getLessonsByIds(List<Long> lessonIds);

    static ReportScheduleReadPort live(ScheduleGrpcClient client) {
        if (client == null) return null;
        return new ReportScheduleReadPort() {
            @Override
            public LessonResponse getLessonById(Long id) {
                return client.getLessonById(id);
            }

            @Override
            public LessonsResponse getLessonsByGroup(
                    Long group, Long semester, String from, String to) {
                return client.getLessonsByGroup(group, semester, from, to);
            }

            @Override
            public List<LessonInfo> getLessonsByIds(List<Long> ids) {
                return client.getLessonsByIds(ids);
            }
        };
    }
}
