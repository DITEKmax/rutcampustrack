package ru.rutcampustrack.attendance.contract.dto.marking;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.util.List;

/**
 * Response for batch mark (M05 D7 / P2-10/4).
 *
 * <p>{@code items} — persisted marks в том же порядке что и request.
 * {@code processed} — успешно записано (при pseudo-atomic semantics
 * всегда равно items.size()).
 *
 * <p>Не содержит HATEOAS _links per-item (noise на 30+ items);
 * self-link добавляется на уровне MarkBatchResponse.
 */
@Schema(description = "Результат пакетной отметки: список персистированных записей и их количество")
public class MarkBatchResponse extends RepresentationModel<MarkBatchResponse> {

    private List<MarkResponse> items;
    private int processed;

    public MarkBatchResponse() {}

    public MarkBatchResponse(List<MarkResponse> items, int processed) {
        this.items = items;
        this.processed = processed;
    }

    public List<MarkResponse> getItems() {
        return items;
    }

    public void setItems(List<MarkResponse> items) {
        this.items = items;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }
}
