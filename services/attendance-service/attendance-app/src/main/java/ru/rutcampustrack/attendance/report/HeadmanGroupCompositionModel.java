package ru.rutcampustrack.attendance.report;

import java.time.LocalDate;
import java.util.List;

/** One minimal roster snapshot shared by all five export formats. */
record HeadmanGroupCompositionModel(String groupName, LocalDate generatedOn, List<Row> rows) {
    record Row(int number, String displayName, String login, String groupRole) {}
}
