package ru.rutcampustrack.attendance.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendExportRequest;

/** Requeries through the secured trend path on each export, including ticket redemption. */
@Service
@RequiredArgsConstructor
public class HeadmanStatsTrendExportService {
    private final HeadmanStatsService headmanStatsService;
    private final HeadmanStatsTrendRenderer renderer;

    public HeadmanStatsExportResult export(HeadmanStatsTrendExportRequest request) {
        if (request == null || request.query() == null) {
            throw new IllegalArgumentException("Trend export query is required");
        }
        var trend = headmanStatsService.trend(request.query());
        return renderer.render(trend, HeadmanStatsTrendFormat.from(request.format()));
    }
}
