import ru.rutcampustrack.shared.events.IdempotencyStore;
import ru.rutcampustrack.shared.observability.MetricNames;
class CheckProjectOutput { IdempotencyStore store; String metric = MetricNames.OUTBOX_LAG_SECONDS; }
