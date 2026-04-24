package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Ответ {@code POST /groups/promote[/preview]} (BUG-006-6 / план 58-06).
 *
 * <ul>
 *   <li>{@link #toPromote} — группы, которые будут/были переименованы на следующий курс.</li>
 *   <li>{@link #toArchive} — группы, которые будут/были архивированы (достигли maxCourse).</li>
 *   <li>{@link #conflicts} — префиксы, пропущенные целиком из-за конфликта
 *       ({@code name_conflict}, {@code unknown_type}, {@code parse_error}).</li>
 *   <li>{@link #dryRun} — {@code true} если это preview, {@code false} если execute.</li>
 *   <li>{@link #executed} — {@code true} если изменения применены в БД.</li>
 * </ul>
 */
@Schema(description = "Итог операции промоушена групп (preview или execute, HATEOAS Level 3 с _links)")
public class PromotionSummary extends RepresentationModel<PromotionSummary> {

    private List<PromotionPreviewItem> toPromote = new ArrayList<>();
    private List<PromotionPreviewItem> toArchive = new ArrayList<>();
    private List<PrefixConflict> conflicts = new ArrayList<>();
    private boolean dryRun;
    private boolean executed;

    public PromotionSummary() {}

    public PromotionSummary(List<PromotionPreviewItem> toPromote,
                             List<PromotionPreviewItem> toArchive,
                             List<PrefixConflict> conflicts,
                             boolean dryRun,
                             boolean executed) {
        this.toPromote = toPromote;
        this.toArchive = toArchive;
        this.conflicts = conflicts;
        this.dryRun = dryRun;
        this.executed = executed;
    }

    public List<PromotionPreviewItem> getToPromote() { return toPromote; }
    public void setToPromote(List<PromotionPreviewItem> toPromote) { this.toPromote = toPromote; }

    public List<PromotionPreviewItem> getToArchive() { return toArchive; }
    public void setToArchive(List<PromotionPreviewItem> toArchive) { this.toArchive = toArchive; }

    public List<PrefixConflict> getConflicts() { return conflicts; }
    public void setConflicts(List<PrefixConflict> conflicts) { this.conflicts = conflicts; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public boolean isExecuted() { return executed; }
    public void setExecuted(boolean executed) { this.executed = executed; }

    /**
     * Конфликт для отдельного префикса (весь префикс пропущен).
     * reason: {@code "name_conflict" | "unknown_type" | "parse_error"}.
     */
    @Schema(description = "Конфликт для отдельного префикса (весь префикс пропущен: name_conflict / unknown_type / parse_error)")
    public static class PrefixConflict {
        private String prefix;
        private String reason;
        private String message;
        private List<Long> groupIds;

        public PrefixConflict() {}

        public PrefixConflict(String prefix, String reason, String message, List<Long> groupIds) {
            this.prefix = prefix;
            this.reason = reason;
            this.message = message;
            this.groupIds = groupIds;
        }

        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public List<Long> getGroupIds() { return groupIds; }
        public void setGroupIds(List<Long> groupIds) { this.groupIds = groupIds; }
    }
}
