package ru.rutcampustrack.attendance.report;

import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse;
import ru.rutcampustrack.attendance.exception.BadRequestException;

import java.util.List;
import java.util.Locale;
import java.util.Arrays;

enum HeadmanStatsTrendFormat {
    PNG("png", "Изображение PNG", "image/png", "png"),
    HTML("html", "HTML с таблицей данных", "text/html; charset=UTF-8", "html");

    private final String code;
    private final String label;
    private final String contentType;
    private final String extension;

    HeadmanStatsTrendFormat(String code, String label, String contentType, String extension) {
        this.code = code;
        this.label = label;
        this.contentType = contentType;
        this.extension = extension;
    }

    String code() { return code; }
    String label() { return label; }
    String contentType() { return contentType; }
    String extension() { return extension; }

    HeadmanStatsResponse.FormatOption toOption() {
        return new HeadmanStatsResponse.FormatOption(code, label, contentType, extension);
    }

    static List<HeadmanStatsResponse.FormatOption> catalogue() {
        return Arrays.stream(values()).map(HeadmanStatsTrendFormat::toOption).toList();
    }

    static HeadmanStatsTrendFormat from(String raw) {
        if (raw == null || raw.isBlank()) throw new BadRequestException("Не выбран формат графика");
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (HeadmanStatsTrendFormat value : values()) {
            if (value.code.equals(normalized)) return value;
        }
        throw new BadRequestException("Формат графика должен быть PNG или HTML");
    }
}
