package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Одна запись в плане промоушена групп (BUG-006-6 / план 58-06).
 *
 * <p>В preview возвращается перед записью в БД; в execute — подтверждение выполнения.
 * {@code to} равен {@code null} для архивации.
 */
@Schema(description = "Одна запись в плане промоушена групп (переименование или архивация)")
public class PromotionPreviewItem {

    public enum Action { PROMOTE, ARCHIVE }

    private Long id;
    private String from;
    private String to;
    private Action action;

    public PromotionPreviewItem() {}

    public PromotionPreviewItem(Long id, String from, String to, Action action) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.action = action;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }
}
